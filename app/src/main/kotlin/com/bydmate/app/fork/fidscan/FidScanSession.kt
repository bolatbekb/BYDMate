@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions") // why: every failure ends as an E line and the session carries on; the schedule steps are flat chains of guards
package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.helper.push.FID_REC_NO_ERROR
import com.bydmate.app.helper.push.FidRecStart
import com.bydmate.app.helper.push.FidRecStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// fork: session loop of the fid scan. Polls every readable autoservice parameter on a schedule,
// keeps the author's push recorder running next to it and stops when the byte budget is spent.
// Nothing here may throw out of tick()/finish() except CancellationException: every failure is
// written to the log as an E line and the session carries on.

/** Everything the session needs from the outside world; Task 3 backs it with the helper and the clock. */
internal interface ScanIo {
    suspend fun catalog(): String?
    suspend fun readBatch(items: List<BatchReadItem>): List<Pair<Int, Int>>?
    suspend fun recStart(): FidRecStart?
    suspend fun recStatus(): FidRecStatus?
    suspend fun recStop()
    fun live(): DiParsData?
    fun nowMs(): Long
    fun timeText(ms: Long): String
}

/** The poll log file: [write] appends `line + "\n"`. */
internal interface ScanSink {
    fun write(line: String)
    val bytes: Long
    fun close()
}

internal enum class TickResult { RUNNING, BUDGET_REACHED }

/** [usedBefore] is the size of the earlier files of this recording. */
internal class FidScanSession(
    private val io: ScanIo,
    private val sink: ScanSink,
    private val usedBefore: Long,
) {
    private var items: List<ScanItem>? = null
    private var nextCatalogAt = 0L
    private var lastMode: ScanMode? = null
    private var modeChangePending = false
    private var lastSweepMs: Long? = null
    private var baselineDone = false
    private val previous = HashMap<Int, Reading>()

    private var pushStarted = false
    private var nextPushAt = 0L
    private var finishedPushBytes = 0L
    private var currentPushPath = ""
    private var currentPushBytes = 0L

    var sweeps = 0
        private set
    var pushRunning = false
        private set

    val usedBytes: Long
        get() = usedBefore + (try { sink.bytes } catch (e: Exception) { 0L }) + finishedPushBytes + currentPushBytes

    suspend fun tick(): TickResult {
        var now = 0L
        try {
            now = io.nowMs()
            step("catalog", now) { loadCatalog(now) }
            step("mode", now) { checkMode(now) }
            step("push", now) { checkPush(now) }
            step("sweep", now) { if (usedBytes < BUDGET_BYTES) sweepIfDue(now) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logError("tick", e, now)
        }
        return if (usedBytes >= BUDGET_BYTES) TickResult.BUDGET_REACHED else TickResult.RUNNING
    }

    suspend fun finish(reason: String) {
        try {
            val now = try { io.nowMs() } catch (e: Exception) { 0L }
            safeWrite("B ${timeOf(now)} $reason")
            try {
                io.recStop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logError("recStop", e, now)
            }
        } finally {
            try { sink.close() } catch (e: Exception) { /* nothing left to log into */ }
        }
    }

    private suspend fun step(name: String, now: Long, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logError(name, e, now)
        }
    }

    private suspend fun loadCatalog(now: Long) {
        if (items != null || now < nextCatalogAt) return
        nextCatalogAt = now + RETRY_MS // also after a throw: a failed dump is retried only after RETRY_MS
        val dump = io.catalog()
        if (dump == null) {
            safeWrite("E ${timeOf(now)} catalog: no answer")
            return
        }
        items = scanList(dump)
    }

    private fun checkMode(now: Long) {
        val d = io.live()
        val mode = scanMode(d)
        if (mode == lastMode) return
        if (lastMode != null) modeChangePending = true
        lastMode = mode
        sink.write(stateLine(timeOf(now), mode, d))
    }

    private suspend fun checkPush(now: Long) {
        var restart = !pushStarted
        if (pushStarted) {
            val st = io.recStatus()
            if (st != null) {
                pushRunning = st.running
                if (st.running) {
                    if (currentPushPath.isNotEmpty() && st.filePath != currentPushPath) moveCurrentPush()
                    currentPushPath = st.filePath
                    currentPushBytes = st.fileBytes
                } else {
                    if (st.filePath == currentPushPath && st.fileBytes > currentPushBytes) currentPushBytes = st.fileBytes
                    restart = true
                }
            }
        }
        if (!restart || now < nextPushAt || usedBytes >= BUDGET_BYTES) return
        moveCurrentPush()
        nextPushAt = now + RETRY_MS // also after a throw: a failed start is retried only after RETRY_MS
        val r = io.recStart()
        when {
            r == null -> failPushStart(now, "no answer")
            r.error != FID_REC_NO_ERROR -> failPushStart(now, r.error)
            r.registered == 0 -> failPushStart(now, "no fid registered")
            else -> {
                nextPushAt = 0L
                pushStarted = true
                pushRunning = true
            }
        }
    }

    private fun failPushStart(now: Long, why: String) {
        pushRunning = false
        safeWrite("E ${timeOf(now)} recStart: $why")
    }

    private fun moveCurrentPush() {
        finishedPushBytes += currentPushBytes
        currentPushBytes = 0L
        currentPushPath = ""
    }

    private suspend fun sweepIfDue(now: Long) {
        val list = items ?: return
        if (list.isEmpty() || !sweepDue(now, lastSweepMs, modeChangePending)) return
        lastSweepMs = now
        modeChangePending = false
        sweeps++
        val firstPass = !baselineDone
        var changed = 0
        if (firstPass) sink.write("F ${timeOf(now)} sweep=$sweeps items=${list.size}")
        var from = 0
        while (from < list.size) {
            if (from > 0) delay(BATCH_PAUSE_MS)
            val chunk = list.subList(from, minOf(from + BATCH_SYMBOLS, list.size))
            val triples = chunk.flatMap { listOf(BatchReadItem(TX_INT, it.dev, it.fid), BatchReadItem(TX_DOUBLE, it.dev, it.fid)) }
            val res = io.readBatch(triples)
            if (res == null || res.size != triples.size) {
                safeWrite("E ${timeOf(now)} readBatch: ${if (res == null) "no answer" else "short answer ${res.size}/${triples.size}"}")
                return
            }
            for ((j, item) in chunk.withIndex()) {
                if (usedBytes >= BUDGET_BYTES) return
                val (iSt, iv) = res[2 * j]
                val (ds, dv) = res[2 * j + 1]
                val r = Reading(iSt, iv, ds, dv)
                val key = from + j
                if (firstPass || previous[key] != r) {
                    sink.write(valueLine(item, r))
                    changed++
                }
                previous[key] = r
            }
            if (usedBytes >= BUDGET_BYTES) return
            from += chunk.size
        }
        if (firstPass) baselineDone = true
        else sink.write("F ${timeOf(now)} sweep=$sweeps ms=${io.nowMs() - now} changed=$changed")
    }

    private fun timeOf(ms: Long): String = try { io.timeText(ms) } catch (e: Exception) { "??:??:??" }

    private fun safeWrite(line: String) {
        try { sink.write(line) } catch (e: Exception) { /* the log itself is broken: nothing to do */ }
    }

    private fun logError(step: String, e: Throwable, now: Long) {
        safeWrite("E ${timeOf(now)} $step: ${e.message ?: e::class.java.simpleName}")
    }
}
