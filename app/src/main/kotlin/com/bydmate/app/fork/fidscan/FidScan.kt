@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth", "ThrowsCount", "CyclomaticComplexMethod") // why: a diagnostic recorder must never crash the app; every step catches Throwable and rethrows CancellationException
package com.bydmate.app.fork.fidscan

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.SystemClock
import android.util.Log
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

    fun flush() {
        try { out.flush() } catch (e: IOException) { fail(e) }
    }

    private fun fail(e: IOException) {
        if (ioError == null) ioError = e
        Log.w(TAG, "write failed", e)
    }

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

object FidScan {
    private val _status = MutableStateFlow(FidScanStatus())
    val status: StateFlow<FidScanStatus> = _status

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.w(TAG, "uncaught", e) },
    )
    private val mutex = Mutex()
    private var job: Job? = null
    private var resumeJob: Job? = null
    private var app: Application? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FID_SCAN_PREFS, Context.MODE_PRIVATE)

    fun init(app: Application) {
        try {
            this.app = app
            val saved = persistedState(prefs(app))
            if (!saved.active) return
            _status.value = FidScanStatus(active = true, usedBytes = saved.usedBytes)
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
                    _status.value = FidScanStatus(error = _status.value.error.takeIf { running == null })
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
                _status.value = FidScanStatus(error = lc.getString(R.string.fork_fidscan_error_no_access))
                return
            }
            val (file, sink) = opened
            savePersisted(prefs(ctx), active = true, usedBytes = usedBefore)
            val session = FidScanSession(HelperScanIo(entry.helperClient()), sink, usedBefore)
            _status.value = FidScanStatus(active = true, fileName = file.name, usedBytes = usedBefore)
            job = scope.launch { runLoop(ctx, session, sink, file.name) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "launchSession", e)
            try { savePersisted(prefs(ctx), active = false, usedBytes = 0L) } catch (_: Throwable) { }
            _status.value = FidScanStatus(error = e.message ?: e::class.java.simpleName)
        }
    }

    private fun openSink(ctx: Context): Pair<File, FileScanSink>? {
        val dir = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/storage/emulated/0/Download"),
            ctx.getExternalFilesDir(null),
        ).firstOrNull { (it.isDirectory || it.mkdirs()) && it.canWrite() } ?: return null
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

    private suspend fun runLoop(ctx: Context, session: FidScanSession, sink: FileScanSink, fileName: String) {
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
            withContext(NonCancellable) {
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
                    _status.value = FidScanStatus(fileName = fileName, usedBytes = session.usedBytes, sweeps = session.sweeps)
                } else if (reason == "write error") {
                    val why = sink.ioError?.let { it.message ?: it::class.java.simpleName } ?: "?"
                    val lc = try { ctx.appLocalizedContext() } catch (e: Throwable) { ctx }
                    _status.value = FidScanStatus(
                        fileName = fileName, usedBytes = session.usedBytes, sweeps = session.sweeps,
                        error = lc.getString(R.string.fork_fidscan_error_write, why),
                    )
                }
            }
        }
    }

    private fun publish(session: FidScanSession, fileName: String, error: String?) {
        _status.value = FidScanStatus(
            active = true,
            fileName = fileName,
            usedBytes = session.usedBytes,
            sweeps = session.sweeps,
            pushRunning = session.pushRunning,
            error = error,
        )
    }
}
