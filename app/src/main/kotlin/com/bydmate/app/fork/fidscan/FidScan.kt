@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth", "ThrowsCount", "CyclomaticComplexMethod") // why: a diagnostic recorder must never crash the app; every step catches Throwable and rethrows CancellationException
package com.bydmate.app.fork.fidscan

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import com.bydmate.app.R
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.DumpFidsResult
import com.bydmate.app.data.vehicle.HelperBootstrap
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.helper.push.FidRecStart
import com.bydmate.app.helper.push.FidRecStatus
import com.bydmate.app.service.TrackingService
import com.bydmate.app.util.appLocalizedContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// fork: Android side of the fid scan (test-build feature, branch feat/fid-scan): the switch in
// «Сервис и данные» starts a FidScanSession on a background scope; the saved state lets a session
// killed by a quickboot resume in a new file after the app starts again. Nothing here may throw
// out of the scope: every step is guarded, CancellationException is always rethrown.

internal const val FID_SCAN_PREFS = "bydmate_fidscan"
private const val KEY_ACTIVE = "active"
private const val KEY_USED = "used_bytes"
private const val KEY_LIST = "list_path"
private const val REC_HEAD_BYTES = 4096
private const val CATALOG_TIMEOUT_MS = 10_000L
private const val TAG = "FidScan"
private const val RESUME_DELAY_MS = 30_000L

internal data class PersistedScan(val active: Boolean, val usedBytes: Long)

internal fun persistedState(prefs: SharedPreferences): PersistedScan =
    PersistedScan(prefs.getBoolean(KEY_ACTIVE, false), prefs.getLong(KEY_USED, 0L))

internal fun savePersisted(prefs: SharedPreferences, active: Boolean, usedBytes: Long) {
    prefs.edit().putBoolean(KEY_ACTIVE, active).putLong(KEY_USED, usedBytes).apply()
}

data class FidScanStatus(
    val active: Boolean = false,
    val fileName: String? = null,
    val usedBytes: Long = 0L,
    val sweeps: Int = 0,
    val pushRunning: Boolean = false,
    val error: String? = null,
    val packing: Boolean = false,
    val lastZip: String? = null,
    /** The zip button's own error: the session never clears it, and [FidScan.pack] clears only this. */
    val packError: String? = null,
)

/** What the list picker shows about the chosen list: [read] items of [lines] lines are polled. */
data class FidScanListStatus(
    val name: String,
    val lines: Int,
    val read: Int,
    val skipped: List<ListSkipped>,
    val withCatalog: Boolean,
    val error: String? = null,
)

internal fun listStatus(name: String, plan: ListPlan) =
    FidScanListStatus(name, plan.lines, plan.items.size, plan.skipped, plan.withCatalog)

/** A new session status [base] with the fields the zip button owns taken over from [old]. */
internal fun keepPackFields(old: FidScanStatus, base: FidScanStatus): FidScanStatus =
    base.copy(packing = old.packing, lastZip = old.lastZip, packError = old.packError)

/** `base (reason)` when there is a reason to show, else just [base]. */
internal fun withReason(base: String, reason: String?): String =
    if (reason.isNullOrBlank()) base else "$base (${oneLine(reason)})"

/** The catalog text; null when the helper is unreachable. A read error throws with its detail, so
 *  the session's `E … catalog:` line names it (and the dump is retried after RETRY_MS). */
internal fun catalogDump(r: DumpFidsResult): String? = when (r) {
    is DumpFidsResult.Success -> r.dump
    is DumpFidsResult.ReadError -> throw IOException("read error: ${r.detail}")
    DumpFidsResult.BinderAbsent -> null
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FidScanEntryPoint {
    fun helperClient(): HelperClient
    fun helperBootstrap(): HelperBootstrap
}

/** The poll log: buffered, counts its bytes, remembers the last `E` line for the status. */
internal class FileScanSink(private val out: OutputStream) : ScanSink {
    constructor(file: File) : this(BufferedOutputStream(FileOutputStream(file), 64 * 1024))

    private var written = 0L
    @Volatile var lastError: String? = null
        private set

    /** The first write or flush failure (full disk, lost folder). Never cleared: the session ends on it. */
    @Volatile var ioError: IOException? = null
        private set

    override val bytes: Long get() = written

    @Synchronized
    override fun write(line: String) {
        val data = (line + "\n").toByteArray(Charsets.UTF_8)
        try {
            out.write(data)
        } catch (e: IOException) {
            fail(e)
            throw e
        }
        written += data.size
        if (line.startsWith("E ")) lastError = line.substring(2)
    }

    @Synchronized
    fun flush() {
        try { out.flush() } catch (e: IOException) { fail(e) }
    }

    private fun fail(e: IOException) {
        if (ioError == null) ioError = e
        Log.w(TAG, "write failed", e)
    }

    @Synchronized
    override fun close() {
        try { out.close() } catch (e: Exception) { /* nothing left to do */ }
    }
}

private class HelperScanIo(private val helper: HelperClient, private val listPath: () -> String?) : ScanIo {
    override suspend fun catalog(): String? = catalogDump(helper.dumpFids())
    override suspend fun readBatch(items: List<BatchReadItem>): List<Pair<Int, Int>>? = helper.readBatch(items)

    override suspend fun listFile(): ScanListFile? = listPath()?.let { readListFile(File(it)) }

    // The devices of the list only (never 1033): an empty array would mean all of them.
    override suspend fun recStart(devices: IntArray): FidRecStart? = helper.recStart(devices)
    override suspend fun recStatus(): FidRecStatus? = helper.recStatus()
    override suspend fun recStop() { helper.recStop() }

    override suspend fun recHead(path: String): String? = try {
        File(path).inputStream().use { input ->
            val buf = ByteArray(REC_HEAD_BYTES)
            var n = 0
            while (n < buf.size) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            String(buf, 0, n, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
    override fun live(): DiParsData? = TrackingService.lastData.value

    // Monotonic: the head unit's wall clock can jump after a quickboot.
    override fun nowMs(): Long = SystemClock.elapsedRealtime()

    override fun timeText(ms: Long): String =
        wallTimeText(System.currentTimeMillis() - SystemClock.elapsedRealtime() + ms)
}

// why: one object owns the session, the status and the zip button; they share the scope and the status flow
@Suppress("TooManyFunctions")
object FidScan {
    private val _status = MutableStateFlow(FidScanStatus())
    val status: StateFlow<FidScanStatus> = _status

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "uncaught", e) },
    )
    private val _list = MutableStateFlow<FidScanListStatus?>(null)

    /** The chosen list: a preview after choosing, the session's plan once it has loaded one. */
    val list: StateFlow<FidScanListStatus?> = _list
    @Volatile private var publishedPlan: ListPlan? = null
    private val mutex = Mutex()
    private val packBusy = AtomicBoolean(false)
    @Volatile private var currentSink: FileScanSink? = null

    /** Replaces the session part of the status and keeps what the zip button owns. */
    private fun setStatus(base: FidScanStatus) {
        _status.update { keepPackFields(it, base) }
    }
    @Volatile private var job: Job? = null
    @Volatile private var resumeJob: Job? = null
    private var app: Application? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FID_SCAN_PREFS, Context.MODE_PRIVATE)

    fun init(app: Application) {
        try {
            this.app = app
            prefs(app).getString(KEY_LIST, null)?.let { path -> scope.launch { publishPreview(app, path, withCatalog = false) } }
            val saved = persistedState(prefs(app))
            if (!saved.active) return
            setStatus(FidScanStatus(active = true, usedBytes = saved.usedBytes))
            // The service and the helper need a moment after a cold start.
            resumeJob = scope.launch {
                delay(RESUME_DELAY_MS)
                mutex.withLock { launchSession(app, saved.usedBytes, resumed = true) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "init", e)
        }
    }

    fun start(context: Context) {
        val ctx = try { context.applicationContext } catch (e: Throwable) { return }
        scope.launch {
            try {
                mutex.withLock {
                    if (job?.isActive == true || resumeJob?.isActive == true) return@withLock
                    val problem = listProblem(ctx)
                    if (problem != null) {
                        setStatus(FidScanStatus(error = problem))
                        return@withLock
                    }
                    launchSession(ctx, 0L, resumed = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "start", e)
            }
        }
    }

    /** Why a manual start cannot begin: no list chosen, or the chosen file is gone. Null when it can. */
    private fun listProblem(ctx: Context): String? {
        val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
        val path = prefs(ctx).getString(KEY_LIST, null) ?: return lc.getString(R.string.fork_fidscan_error_no_list)
        val file = File(path)
        return if (file.isFile) null else lc.getString(R.string.fork_fidscan_error_list_missing, file.name)
    }

    /** The files of the log folder the owner may pick as the list, newest first. */
    fun listCandidates(context: Context): List<File> = try {
        logDir(context.applicationContext)?.listFiles()
            ?.filter { it.isFile && isListCandidate(it.name) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    } catch (e: Throwable) {
        Log.w(TAG, "listCandidates", e)
        emptyList()
    }

    /** Saves [file] as the list and publishes its preview (checked against the car's catalog when
     *  the helper answers). Ignored while a recording runs or a resume is pending. */
    fun chooseList(context: Context, file: File) {
        try {
            if (job?.isActive == true || resumeJob?.isActive == true) return
            val ctx = context.applicationContext
            prefs(ctx).edit().putString(KEY_LIST, file.absolutePath).apply()
            scope.launch { publishPreview(ctx, file.absolutePath, withCatalog = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "chooseList", e)
        }
    }

    // No session mutex here: the helper call may take seconds.
    private suspend fun publishPreview(ctx: Context, path: String, withCatalog: Boolean) {
        try {
            val file = File(path)
            val text = try {
                readListFile(file).text
            } catch (e: IOException) {
                _list.value = FidScanListStatus(file.name, 0, 0, emptyList(), false, error = e.message ?: e::class.java.simpleName)
                return
            }
            val dump = if (withCatalog) previewCatalog(ctx) else null
            _list.value = listStatus(file.name, planFromList(text, dump))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "preview", e)
        }
    }

    /** The car's catalog for the preview; null on any failure or after 10 s. */
    private suspend fun previewCatalog(ctx: Context): String? = try {
        val helper = EntryPointAccessors.fromApplication(ctx, FidScanEntryPoint::class.java).helperClient()
        withTimeoutOrNull(CATALOG_TIMEOUT_MS) { catalogDump(helper.dumpFids()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "preview catalog", e)
        null
    }

    fun stop() {
        scope.launch {
            try {
                mutex.withLock {
                    val pendingResume = resumeJob?.isActive == true
                    resumeJob?.cancelAndJoin()
                    resumeJob = null
                    val running = job
                    if (running != null) {
                        running.cancelAndJoin() // the loop's finally closes the session
                        job = null
                    }
                    // A resume cancelled in its delay: the killed process's push recorder may still run.
                    if (pendingResume && running == null) app?.let { stopOrphanRecorder(it) }
                    app?.let { savePersisted(prefs(it), active = false, usedBytes = 0L) }
                    setStatus(FidScanStatus(error = _status.value.error.takeIf { running == null }))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "stop", e)
            }
        }
    }

    /** Called under [mutex]. Opens the file, saves the state and starts the loop. [resumed]: the
     *  automatic start after an app restart, with [usedBefore] bytes of the recording already used. */
    private suspend fun launchSession(ctx: Context, usedBefore: Long, resumed: Boolean) {
        try {
            if (job?.isActive == true) return
            if (resumed && prefs(ctx).getString(KEY_LIST, null) == null) {
                abandonResumeWithoutList(ctx)
                return
            }
            val entry = EntryPointAccessors.fromApplication(ctx, FidScanEntryPoint::class.java)
            // Only a start from the switch brings the helper up: after a cold start TrackingService's
            // watchdog owns the daemon, and a second bootstrap would hold the helper for seconds.
            if (!resumed) bringHelperUp(entry)
            val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
            var openFailure: Throwable? = null
            val opened = try {
                openSink(ctx, resumed, usedBefore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "openSink", e)
                openFailure = e
                null
            }
            if (opened == null) {
                if (resumed) stopOrphanRecorder(ctx)
                savePersisted(prefs(ctx), active = false, usedBytes = 0L)
                val noAccess = lc.getString(R.string.fork_fidscan_error_no_access)
                setStatus(FidScanStatus(error = withReason(noAccess, openFailure?.message)))
                return
            }
            val (file, sink) = opened
            val free = freeBytes(file.parentFile)
            if (free != null && !hasRoom(free)) {
                // Nothing was recorded yet: no empty fidscan-*.txt may stay in Download.
                sink.close()
                file.delete()
                if (resumed) stopOrphanRecorder(ctx)
                savePersisted(prefs(ctx), active = false, usedBytes = 0L)
                setStatus(FidScanStatus(error = lc.getString(R.string.fork_fidscan_error_space_start, gbText(free))))
                return
            }
            savePersisted(prefs(ctx), active = true, usedBytes = usedBefore)
            val session = FidScanSession(HelperScanIo(entry.helperClient()) { prefs(ctx).getString(KEY_LIST, null) }, sink, usedBefore)
            setStatus(FidScanStatus(active = true, fileName = file.name, usedBytes = usedBefore))
            currentSink = sink
            job = scope.launch { runLoop(ctx, session, sink, file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "launchSession", e)
            if (resumed) stopOrphanRecorder(ctx)
            try {
                savePersisted(prefs(ctx), active = false, usedBytes = 0L)
            } catch (s: CancellationException) {
                throw s
            } catch (s: Throwable) {
                Log.w(TAG, "save", s)
            }
            setStatus(FidScanStatus(error = e.message ?: e::class.java.simpleName))
        }
    }

    /** A resume with no list chosen: an endless resume would only log "not chosen" and block the picker. */
    private suspend fun abandonResumeWithoutList(ctx: Context) {
        stopOrphanRecorder(ctx)
        savePersisted(prefs(ctx), active = false, usedBytes = 0L)
        val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
        setStatus(FidScanStatus(error = lc.getString(R.string.fork_fidscan_error_no_list)))
    }

    private suspend fun bringHelperUp(entry: FidScanEntryPoint) {
        try {
            entry.helperBootstrap().ensureRunning()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "ensureRunning", e)
        }
    }

    /** Best effort: a resume that starts no session must not leave the killed process's push
     *  recorder writing. Only resumes call it: after a manual start failure the recorder is not ours. */
    private suspend fun stopOrphanRecorder(ctx: Context) {
        try {
            EntryPointAccessors.fromApplication(ctx, FidScanEntryPoint::class.java).helperClient().recStop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "recStop", e)
        }
    }

    /** The folder of the logs: Download, else the app's external files. Never creates a file. */
    private fun logDir(ctx: Context): File? = listOfNotNull(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        File("/storage/emulated/0/Download"),
        ctx.getExternalFilesDir(null),
    ).firstOrNull { (it.isDirectory || it.mkdirs()) && it.canWrite() }

    /** Free bytes of the volume of [dir]; null when unknown (a StatFs throw never stops a session). */
    private fun freeBytes(dir: File?): Long? {
        if (dir == null) return null
        return try {
            StatFs(dir.path).availableBytes
        } catch (e: Throwable) {
            Log.w(TAG, "StatFs", e)
            null
        }
    }

    private fun gbText(bytes: Long): String = String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024 * 1024))

    private fun openSink(ctx: Context, resumed: Boolean, usedBefore: Long): Pair<File, FileScanSink>? {
        val dir = logDir(ctx) ?: return null
        val now = Date()
        val file = File(dir, "fidscan-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt")
        val sink = FileScanSink(file)
        try {
            val vName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
            sink.write(headerLine(vName, wallTimeText(now.time), Build.FINGERPRINT ?: "?", resumed, usedBefore))
            sink.flush()
        } catch (e: Throwable) {
            sink.close()
            throw e
        }
        return file to sink
    }

    private suspend fun runLoop(ctx: Context, session: FidScanSession, sink: FileScanSink, file: File) {
        val fileName = file.name
        var reason = "stop"
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val r = session.tick()
                    sink.flush()
                    if (sink.ioError != null) {
                        reason = "write error"
                        return
                    }
                    // Every tick, not every sweep: a kill (each quickboot) loses at most one tick of
                    // push file growth from the 400 MB count.
                    try {
                        savePersisted(prefs(ctx), active = true, usedBytes = session.usedBytes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Log.w(TAG, "save", e)
                    }
                    val free = freeBytes(file.parentFile)
                    if (free != null && !hasRoom(free)) {
                        reason = "low disk"
                        return
                    }
                    publish(session, fileName, sink.ioError?.message ?: sink.lastError)
                    if (r == TickResult.BUDGET_REACHED) {
                        reason = "budget"
                        return
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "tick", e)
                }
                delay(TICK_MS)
            }
        } finally {
            withContext(NonCancellable) { endSession(ctx, session, sink, fileName, reason) }
        }
    }

    /** The one end path: budget, write error, low disk and a plain stop all pass here. */
    private suspend fun endSession(ctx: Context, session: FidScanSession, sink: FileScanSink, fileName: String, reason: String) {
        if (currentSink === sink) currentSink = null
        try {
            session.finish(reason)
        } catch (e: Throwable) {
            Log.w(TAG, "finish", e)
        }
        try {
            savePersisted(prefs(ctx), active = false, usedBytes = 0L)
        } catch (e: Throwable) {
            Log.w(TAG, "save", e)
        }
        if (reason == "budget") {
            setStatus(FidScanStatus(fileName = fileName, usedBytes = session.usedBytes, sweeps = session.sweeps))
        } else if (reason == "write error" || reason == "low disk") {
            val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
            val text = if (reason == "low disk") {
                lc.getString(R.string.fork_fidscan_error_space_run)
            } else {
                val why = sink.ioError?.let { it.message ?: it::class.java.simpleName } ?: "?"
                lc.getString(R.string.fork_fidscan_error_write, why)
            }
            setStatus(
                FidScanStatus(
                    fileName = fileName, usedBytes = session.usedBytes, sweeps = session.sweeps,
                    error = text,
                ),
            )
        }
    }

    private fun publish(session: FidScanSession, fileName: String, error: String?) {
        val plan = session.listPlan
        if (plan != null && plan !== publishedPlan) {
            publishedPlan = plan
            _list.value = listStatus(File(session.listPath ?: "").name, plan)
        }
        setStatus(
            FidScanStatus(
                active = true,
                fileName = fileName,
                usedBytes = session.usedBytes,
                sweeps = session.sweeps,
                pushRunning = session.pushRunning,
                error = error,
            ),
        )
    }

    /** «Упаковать и отправить»: zips every recording log in the folder and opens the share sheet.
     *  Own flag, not the session mutex: packing never blocks start/stop and the other way round. */
    fun pack(context: Context) {
        val ctx = try { context.applicationContext } catch (e: Throwable) { return }
        if (!packBusy.compareAndSet(false, true)) return
        _status.update { it.copy(packing = true, packError = null) }
        try {
            scope.launch {
                try {
                    doPack(ctx)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "pack", e)
                    packFailed(ctx, R.string.fork_fidscan_error_pack, e.message ?: e::class.java.simpleName)
                } finally {
                    packBusy.set(false)
                    _status.update { it.copy(packing = false) }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "pack launch", e)
            packBusy.set(false)
            _status.update { it.copy(packing = false) }
        }
    }

    private fun packFailed(ctx: Context, res: Int, vararg args: Any) {
        val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
        _status.update { it.copy(packError = lc.getString(res, *args)) }
    }

    private suspend fun doPack(ctx: Context) {
        val dir = logDir(ctx)
        if (dir == null) {
            packFailed(ctx, R.string.fork_fidscan_error_no_access)
            return
        }
        val files = dir.listFiles()?.filter { it.isFile && isScanLog(it.name) }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) {
            packFailed(ctx, R.string.fork_fidscan_error_no_files)
            return
        }
        val free = freeBytes(dir)
        if (free != null && !hasRoom(free)) {
            packFailed(ctx, R.string.fork_fidscan_error_space_pack, gbText(free))
            return
        }
        // A running recording: push the buffered tail to the file first (flush stores IO errors itself).
        try { currentSink?.flush() } catch (e: Throwable) { Log.w(TAG, "flush", e) }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val zip = File(dir, zipName(stamp))
        val size = zipLogs(files, zip)
        val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
        val mb = String.format(Locale.US, "%.1f", size / (1024.0 * 1024))
        _status.update { it.copy(lastZip = lc.getString(R.string.fork_fidscan_zip_done, zip.name, mb)) }
        withContext(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", zip)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "share", e)
                packFailed(ctx, R.string.fork_fidscan_error_share, e.message ?: e::class.java.simpleName)
            }
        }
    }
}
