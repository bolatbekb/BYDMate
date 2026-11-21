@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth", "ThrowsCount", "CyclomaticComplexMethod") // why: a diagnostic recorder must never crash the app; every step catches Throwable and rethrows CancellationException
package com.bydmate.app.fork.fidscan

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
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

// fork: Android side of the fid scan (test-build feature, branch feat/fid-scan): the switch in
// «Сервис и данные» starts a FidScanSession on a background scope; the saved state lets a session
// killed by a quickboot resume in a new file after the app starts again. Nothing here may throw
// out of the scope: every step is guarded, CancellationException is always rethrown.

internal const val FID_SCAN_PREFS = "bydmate_fidscan"
private const val KEY_ACTIVE = "active"
private const val KEY_USED = "used_bytes"
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
)

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

private class HelperScanIo(private val helper: HelperClient) : ScanIo {
    override suspend fun catalog(): String? = (helper.dumpFids() as? DumpFidsResult.Success)?.dump
    override suspend fun readBatch(items: List<BatchReadItem>): List<Pair<Int, Int>>? = helper.readBatch(items)
    override suspend fun recStart(): FidRecStart? = helper.recStart(IntArray(0))
    override suspend fun recStatus(): FidRecStatus? = helper.recStatus()
    override suspend fun recStop() { helper.recStop() }
    override fun live(): DiParsData? = TrackingService.lastData.value

    // Monotonic: the head unit's wall clock can jump after a quickboot.
    override fun nowMs(): Long = SystemClock.elapsedRealtime()

    override fun timeText(ms: Long): String {
        val wall = System.currentTimeMillis() - SystemClock.elapsedRealtime() + ms
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(wall))
    }
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
    private val mutex = Mutex()
    private val packBusy = AtomicBoolean(false)
    @Volatile private var currentSink: FileScanSink? = null

    /** Replaces the session part of the status and keeps what the zip button owns. */
    private fun setStatus(base: FidScanStatus) {
        _status.update { base.copy(packing = it.packing, lastZip = it.lastZip) }
    }
    private var job: Job? = null
    private var resumeJob: Job? = null
    private var app: Application? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FID_SCAN_PREFS, Context.MODE_PRIVATE)

    fun init(app: Application) {
        try {
            this.app = app
            val saved = persistedState(prefs(app))
            if (!saved.active) return
            setStatus(FidScanStatus(active = true, usedBytes = saved.usedBytes))
            // The service and the helper need a moment after a cold start.
            resumeJob = scope.launch {
                delay(RESUME_DELAY_MS)
                mutex.withLock { launchSession(app, saved.usedBytes) }
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
                    launchSession(ctx, 0L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "start", e)
            }
        }
    }

    fun stop() {
        scope.launch {
            try {
                mutex.withLock {
                    resumeJob?.cancelAndJoin()
                    resumeJob = null
                    val running = job
                    if (running != null) {
                        running.cancelAndJoin() // the loop's finally closes the session
                        job = null
                    }
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

    /** Called under [mutex]. Opens the file, saves the state and starts the loop. */
    private suspend fun launchSession(ctx: Context, usedBefore: Long) {
        try {
            if (job?.isActive == true) return
            val entry = EntryPointAccessors.fromApplication(ctx, FidScanEntryPoint::class.java)
            try {
                entry.helperBootstrap().ensureRunning()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "ensureRunning", e)
            }
            val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
            val opened = try { openSink(ctx) } catch (e: CancellationException) { throw e } catch (e: Throwable) { null }
            if (opened == null) {
                savePersisted(prefs(ctx), active = false, usedBytes = 0L)
                setStatus(FidScanStatus(error = lc.getString(R.string.fork_fidscan_error_no_access)))
                return
            }
            val (file, sink) = opened
            val free = freeBytes(file.parentFile)
            if (free != null && !hasRoom(free)) {
                // Nothing was recorded yet: no empty fidscan-*.txt may stay in Download.
                sink.close()
                file.delete()
                savePersisted(prefs(ctx), active = false, usedBytes = 0L)
                setStatus(FidScanStatus(error = lc.getString(R.string.fork_fidscan_error_space_start, gbText(free))))
                return
            }
            savePersisted(prefs(ctx), active = true, usedBytes = usedBefore)
            val session = FidScanSession(HelperScanIo(entry.helperClient()), sink, usedBefore)
            setStatus(FidScanStatus(active = true, fileName = file.name, usedBytes = usedBefore))
            currentSink = sink
            job = scope.launch { runLoop(ctx, session, sink, file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "launchSession", e)
            try { savePersisted(prefs(ctx), active = false, usedBytes = 0L) } catch (_: Throwable) { }
            setStatus(FidScanStatus(error = e.message ?: e::class.java.simpleName))
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

    private fun openSink(ctx: Context): Pair<File, FileScanSink>? {
        val dir = logDir(ctx) ?: return null
        val now = Date()
        val file = File(dir, "fidscan-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt")
        val sink = FileScanSink(file)
        try {
            val vName = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
            sink.write("# bydmate fid scan versionName=$vName started=" +
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now))
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
        var savedSweeps = 0
        try {
            while (currentCoroutineContext().isActive) {
                try {
                    val r = session.tick()
                    sink.flush()
                    if (sink.ioError != null) {
                        reason = "write error"
                        return
                    }
                    if (session.sweeps > savedSweeps) {
                        savedSweeps = session.sweeps
                        savePersisted(prefs(ctx), active = true, usedBytes = session.usedBytes)
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
        _status.update { it.copy(packing = true, error = null) }
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
        _status.update { it.copy(error = lc.getString(res, *args)) }
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
