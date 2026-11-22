@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions") // why: every failure ends as an E line and the session carries on; the schedule steps are flat chains of guards
package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.helper.push.FID_REC_NO_ERROR
import com.bydmate.app.helper.push.FidRecStart
import com.bydmate.app.helper.push.FidRecStatus
import com.bydmate.app.helper.push.MAX_REC_DEVICES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// fork: session loop of the fid scan. Polls the parameters of the chosen list file on a schedule,
// keeps the author's push recorder running next to it and stops when the byte budget is spent.
// Nothing here may throw out of tick()/finish() except CancellationException: every failure is
// written to the log as an E line and the session carries on.

/** Everything the session needs from the outside world; Task 3 backs it with the helper and the clock. */
internal interface ScanIo {
    suspend fun catalog(): String?
    suspend fun readBatch(items: List<BatchReadItem>): List<Pair<Int, Int>>?
    /** The chosen list file; null when none is chosen. Throws [java.io.IOException] with a short reason when it cannot be read. */
    suspend fun listFile(): ScanListFile?
    suspend fun recStart(devices: IntArray): FidRecStart?
    suspend fun recStatus(): FidRecStatus?
    suspend fun recStop()
    /** The first bytes (at most 4096, UTF-8) of the recorder file; null when it cannot be read. */
    suspend fun recHead(path: String): String?
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

/** The text of the chosen list file and its path. */
internal data class ScanListFile(val path: String, val text: String)

internal enum class TickResult { RUNNING, BUDGET_REACHED }

/** [usedBefore] is the size of the earlier files of this recording. */
internal class FidScanSession(
    private val io: ScanIo,
    private val sink: ScanSink,
    private val usedBefore: Long,
) {
    private var items: List<ScanItem>? = null
    private var emptyPlanLogged = false
    private var tooManyLogged = false
    private var nextCatalogAt = 0L
    private var lastMode: ScanMode? = null
    private var lastEngine: Boolean? = null
    private var modeChangePending = false
    private var lastSweepMs: Long? = null
    private var baselineDone = false
    private val previous = HashMap<Int, Reading>()

    // Per-batch guard: the batch size of this session (a slow batch halves it), and in how many
    // sweeps the batch of each item (key = index in [items]) failed since its last good read.
    private var batchSymbols = BATCH_SYMBOLS
    private val failures = HashMap<Int, Int>()

    private var pushStarted = false
    private var pushAttempted = false
    private var nextPushAt = 0L
    private var pushBackoffMs = RETRY_MS
    private var finishedPushBytes = 0L
    private var currentPushPath = ""
    private var currentPushBytes = 0L
    private val headChecked = HashSet<String>()
    private var headCandidate: String? = null

    /** The plan of the chosen list, set once it is loaded (null until then). */
    var listPlan: ListPlan? = null
        private set

    /** Path of the list file the plan came from. */
    var listPath: String? = null
        private set

    /** Completed sweeps only: a sweep ended by a failed batch or by the budget is not counted. */
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
            safeWrite("B ${timeOf(now)} ${oneLine(reason)}")
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
        if (listPlan != null || now < nextCatalogAt) return
        // Also after a throw or an empty plan: a failed list or dump is retried only after RETRY_MS.
        nextCatalogAt = now + RETRY_MS
        val file = readList(now) ?: return
        val dump = io.catalog()
        if (dump == null) {
            safeWrite("E ${timeOf(now)} catalog: no answer")
            return
        }
        if (dump.isBlank()) {
            safeWrite("E ${timeOf(now)} catalog: empty")
            return
        }
        val plan = planFromList(file.text, dump)
        if (plan.items.isEmpty()) {
            safeWrite("E ${timeOf(now)} list: nothing to read")
            if (!emptyPlanLogged) listSkipLines(plan).forEach { safeWrite(it) }
            emptyPlanLogged = true
            return
        }
        items = plan.items
        listPlan = plan
        listPath = file.path
        safeWrite(
            "# list: ${file.path} lines=${plan.lines} read=${plan.items.size} devices=${plan.devices.joinToString(",")} sha=${sha8(file.text)}",
        )
        listSkipLines(plan).forEach { safeWrite(it) }
    }

    /** The list file, or null after an E line (not chosen, or unreadable). */
    private suspend fun readList(now: Long): ScanListFile? {
        val file = try {
            io.listFile()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logError("list", e, now)
            return null
        }
        if (file == null) safeWrite("E ${timeOf(now)} list: not chosen")
        return file
    }

    /** An S line when the car mode changes or the engine starts or stops, one per tick at most;
     *  only a mode change asks for an early sweep. */
    private fun checkMode(now: Long) {
        val d = io.live()
        val mode = scanMode(d)
        val engine = engineRunning(d)
        val engineFlipped = engine != null && lastEngine != null && engine != lastEngine
        if (engine != null) lastEngine = engine
        if (mode == lastMode && !engineFlipped) return
        if (mode != lastMode) {
            if (lastMode != null) modeChangePending = true
            lastMode = mode
        }
        sink.write(stateLine(timeOf(now), mode, d))
    }

    private suspend fun checkPush(now: Long) {
        val devices = listPlan?.devices ?: return
        // Above MAX_REC_DEVICES the wire reads "all devices", 1033 included: never ask for that.
        if (devices.isEmpty() || devices.size > MAX_REC_DEVICES) {
            if (!tooManyLogged && devices.isNotEmpty()) {
                safeWrite("E ${timeOf(now)} push: too many devices (${devices.size})")
            }
            tooManyLogged = true
            return
        }
        var restart = !pushStarted
        if (pushStarted) {
            val st = io.recStatus()
            if (st != null) {
                pushRunning = st.running
                if (st.running) {
                    if (currentPushPath.isNotEmpty() && st.filePath != currentPushPath) moveCurrentPush()
                    currentPushPath = st.filePath
                    currentPushBytes = st.fileBytes
                    checkRawTap(now, st.filePath, st.fileBytes)
                } else {
                    if (st.filePath == currentPushPath && st.fileBytes > currentPushBytes) currentPushBytes = st.fileBytes
                    restart = true
                }
            }
        }
        if (!restart || now < nextPushAt || usedBytes >= BUDGET_BYTES) return
        moveCurrentPush()
        if (!pushStarted && pushAttempted && adoptRunningPush(now)) return
        // Set before the call: whatever throws below, the next start waits at least the backoff.
        nextPushAt = now + pushBackoffMs
        startPush(now, devices)
    }

    /** Once per recorder file: does its head carry the tap's `fidscan raw:` header? An old helper
     *  daemon (same versionCode, still alive) has no tap. Done on the second tick that sees bytes in
     *  the file, so the author's 2 s flush has put the head on disk; an unreadable head says nothing. */
    private suspend fun checkRawTap(now: Long, path: String, bytes: Long) {
        if (path.isEmpty() || bytes <= 0L || path in headChecked) return
        if (headCandidate != path) {
            headCandidate = path
            return
        }
        headChecked += path
        val head = io.recHead(path) ?: return
        if (RAW_MARKER !in head) {
            safeWrite("E ${timeOf(now)} push: helper from an older build, no raw capture - reboot the head unit")
        }
    }

    /** After a start that got no answer the helper may still have started: a running recorder is
     *  taken over instead of asking for a second one. False when it is not running or cannot say. */
    private suspend fun adoptRunningPush(now: Long): Boolean {
        val st = try {
            io.recStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        if (st == null || !st.running) return false
        pushStarted = true
        pushRunning = true
        pushBackoffMs = RETRY_MS
        nextPushAt = now + RETRY_MS
        currentPushPath = st.filePath
        currentPushBytes = st.fileBytes
        safeWrite("P ${timeOf(now)} push adopted path=${oneLine(st.filePath)} bytes=${st.fileBytes}")
        return true
    }

    /** One recStart (it holds the helper's mutex for up to 15 s): a P line after every attempt, a
     *  growing backoff after a failure, a minute's rest after a good start. */
    private suspend fun startPush(now: Long, devices: List<Int>) {
        pushAttempted = true
        val t0 = io.nowMs()
        var r: FidRecStart? = null
        var thrown: String? = null
        try {
            r = io.recStart(devices.toIntArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            thrown = messageOf(e)
        }
        val ms = io.nowMs() - t0
        val why = when {
            thrown != null -> thrown
            r == null -> "no answer"
            r.error != FID_REC_NO_ERROR -> r.error
            r.registered == 0 -> "no fid registered"
            else -> null
        }
        val error = oneLine(r?.error ?: why ?: FID_REC_NO_ERROR)
        safeWrite(
            "P ${timeOf(now)} push start ms=$ms devices=${r?.devices ?: "-"} registered=${r?.registered ?: "-"} error=$error",
        )
        if (why == null) {
            pushBackoffMs = RETRY_MS
            nextPushAt = now + RETRY_MS // even a recorder seen stopped right away is not restarted sooner
            pushStarted = true
            pushRunning = true
        } else {
            pushBackoffMs = minOf(pushBackoffMs * 2, MAX_PUSH_BACKOFF_MS)
            failPushStart(now, why)
        }
    }

    private fun failPushStart(now: Long, why: String) {
        pushRunning = false
        safeWrite("E ${timeOf(now)} recStart: ${oneLine(why)}")
    }

    private fun moveCurrentPush() {
        finishedPushBytes += currentPushBytes
        currentPushBytes = 0L
        currentPushPath = ""
    }

    private suspend fun sweepIfDue(now: Long) {
        val list = items ?: return
        if (list.isEmpty() || inReverse(io.live()) || !sweepDue(now, lastSweepMs, modeChangePending)) return
        val priorSweepMs = lastSweepMs
        val priorModeChange = modeChangePending
        lastSweepMs = now
        modeChangePending = false
        val firstPass = !baselineDone
        // Items whose batch failed in two sweeps stay out for the rest of the session.
        val keys = list.indices.filter { (failures[it] ?: 0) < SKIP_AFTER_FAILURES }
        var changed = 0
        var pos = 0
        while (pos < keys.size) {
            if (pos > 0) {
                delay(BATCH_PAUSE_MS)
                if (inReverse(io.live())) {
                    // The rest waits: the sweep is due again on the first tick after reverse.
                    lastSweepMs = priorSweepMs
                    modeChangePending = priorModeChange
                    return
                }
            }
            val chunk = keys.subList(pos, minOf(pos + batchSymbols, keys.size))
            val res = readChunk(list, chunk) ?: return
            for ((j, key) in chunk.withIndex()) {
                if (usedBytes >= BUDGET_BYTES) return
                val (iSt, iv) = res[2 * j]
                val (ds, dv) = res[2 * j + 1]
                val r = Reading(iSt, iv, ds, dv)
                if (firstPass || previous[key] != r) {
                    sink.write(valueLine(list[key], r))
                    changed++
                }
                previous[key] = r
            }
            if (usedBytes >= BUDGET_BYTES) return
            pos += chunk.size
        }
        sweeps++
        val ms = io.nowMs() - now
        if (firstPass) {
            baselineDone = true
            sink.write("F ${timeOf(now)} sweep=$sweeps items=${list.size} ms=$ms")
        } else {
            sink.write("F ${timeOf(now)} sweep=$sweeps ms=$ms changed=$changed")
        }
    }

    /** One batch call for the items [keys] of [list], timed. Null when it failed: the E line is
     *  written and the failure counted for each of its items. A slow answer is still used, and
     *  halves the batch size for the rest of the session. */
    private suspend fun readChunk(list: List<ScanItem>, keys: List<Int>): List<Pair<Int, Int>>? {
        val triples = keys.flatMap { k ->
            val item = list[k]
            listOf(BatchReadItem(TX_INT, item.dev, item.fid), BatchReadItem(TX_DOUBLE, item.dev, item.fid))
        }
        val first = list[keys.first()]
        val where = "dev=${first.dev} from=${first.symbol} size=${keys.size}"
        val t0 = io.nowMs()
        var res: List<Pair<Int, Int>>? = null
        var thrown: String? = null
        try {
            res = io.readBatch(triples)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            thrown = messageOf(e)
        }
        val ms = io.nowMs() - t0
        val why = when {
            thrown != null -> thrown
            res == null -> "no answer"
            res.size != triples.size -> "wrong size ${res.size}/${triples.size}"
            else -> null
        }
        if (why != null) {
            safeWrite("E ${timeOf(t0)} batch failed $where ms=$ms: ${oneLine(why)}")
            countFailure(list, keys, t0)
            return null
        }
        keys.forEach { failures.remove(it) }
        if (ms > SLOW_BATCH_MS) {
            safeWrite("E ${timeOf(t0)} slow batch $ms ms $where")
            batchSymbols = maxOf(MIN_BATCH_SYMBOLS, batchSymbols / 2)
        }
        return res
    }

    private fun countFailure(list: List<ScanItem>, keys: List<Int>, t: Long) {
        val skipped = ArrayList<Int>()
        for (k in keys) {
            val n = (failures[k] ?: 0) + 1
            failures[k] = n
            if (n == SKIP_AFTER_FAILURES) skipped += k
        }
        if (skipped.isEmpty()) return
        val first = list[skipped.first()]
        safeWrite("E ${timeOf(t)} skipping ${skipped.size} symbols from dev=${first.dev} ${first.symbol} (failed twice)")
    }

    private fun messageOf(e: Throwable): String = e.message ?: e::class.java.simpleName

    private fun timeOf(ms: Long): String = try { io.timeText(ms) } catch (e: Exception) { "????-??-?? ??:??:??" }

    private fun safeWrite(line: String) {
        try { sink.write(line) } catch (e: Exception) { /* the log itself is broken: nothing to do */ }
    }

    private fun logError(step: String, e: Throwable, now: Long) {
        safeWrite("E ${timeOf(now)} $step: ${oneLine(messageOf(e))}")
    }
}
