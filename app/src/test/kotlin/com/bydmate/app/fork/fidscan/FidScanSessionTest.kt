@file:Suppress("UseCheckOrError") // why: the fakes throw on purpose
package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.helper.push.FidRecStart
import com.bydmate.app.helper.push.FidRecStatus
import com.bydmate.app.helper.push.MAX_REC_DEVICES
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private const val MIN = 60_000L

private class FakeSink : ScanSink {
    val lines = mutableListOf<String>()
    var failWrites = false
    var closed = false
    override var bytes = 0L
    override fun write(line: String) {
        if (failWrites) throw java.io.IOException("disk full")
        lines += line
        bytes += line.length + 1
    }
    override fun close() { closed = true }
}

private class FakeIo : ScanIo {
    var now = 1_000_000L
    var catalogText: String? = """
        BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012
        BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
        Engine.ENGINE_A=1
        Engine.ENGINE_B=2
        Statistic.STATISTIC_C=3
    """.trimIndent()
    var catalogThrows: Throwable? = null
    var listText: String? = DEFAULT_LIST
    var listThrows: Throwable? = null
    var listCalls = 0
    val recDevices = mutableListOf<List<Int>>()
    var readThrows: Throwable? = null
    var readNull = false
    /** Milliseconds one batch call takes on the fake clock. */
    var readCostMs: (List<BatchReadItem>) -> Long = { 0L }
    /** A batch for which this is true answers null. */
    var failBatch: (List<BatchReadItem>) -> Boolean = { false }
    val values = HashMap<Int, Int>()
    val batches = mutableListOf<List<BatchReadItem>>()
    var catalogCalls = 0
    var recStartCalls = 0
    var startResult: FidRecStart? = FidRecStart(2, 5)
    var status: FidRecStatus? = null
    var stopCalls = 0
    var liveData: DiParsData? = null

    override suspend fun catalog(): String? {
        catalogCalls++
        catalogThrows?.let { throw it }
        return catalogText
    }
    override suspend fun readBatch(items: List<BatchReadItem>): List<Pair<Int, Int>>? {
        readThrows?.let { throw it }
        batches += items
        now += readCostMs(items)
        if (readNull || failBatch(items)) return null
        return items.map { if (it.tx == TX_INT) 0 to (values[it.fid] ?: 10 * it.fid) else 0 to 0 }
    }
    override suspend fun listFile(): ScanListFile? {
        listCalls++
        listThrows?.let { throw it }
        return listText?.let { ScanListFile("/d/list.txt", it) }
    }
    override suspend fun recStart(devices: IntArray): FidRecStart? {
        recStartCalls++
        recDevices += devices.toList()
        return startResult
    }
    override suspend fun recStatus(): FidRecStatus? = status
    override suspend fun recStop() { stopCalls++ }
    override fun live(): DiParsData? = liveData
    override fun nowMs(): Long = now
    override fun timeText(ms: Long): String = "t$ms"
}

private fun recStatus(running: Boolean, path: String, bytes: Long) =
    FidRecStatus(running, emptyList(), 0, emptyList(), path, bytes)

private const val DEFAULT_LIST = "Engine.ENGINE_A=1\nEngine.ENGINE_B=2\nStatistic.STATISTIC_C=3"
private val BIG_LIST = (1..130).joinToString("\n") { "Engine.ENGINE_X$it=$it" }

private val BIG_CATALOG = "BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012\n" +
    (1..130).joinToString("\n") { "Engine.ENGINE_X$it=$it" }

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass") // why: one session, one fake clock; the cases share the private fakes
class FidScanSessionTest {
    private val io = FakeIo()
    private val sink = FakeSink()
    private fun session(usedBefore: Long = 0L) = FidScanSession(io, sink, usedBefore)
    private fun vLines() = sink.lines.filter { it.startsWith("V ") }
    private fun fLines() = sink.lines.filter { it.startsWith("F ") }
    private fun eLines() = sink.lines.filter { it.startsWith("E ") }
    private fun sLines() = sink.lines.filter { it.startsWith("S ") }
    private fun pLines() = sink.lines.filter { it.startsWith("P ") }

    @Test
    fun firstSweepWritesEveryItemTwiceRead() = runTest {
        val s = session()
        assertEquals(TickResult.RUNNING, s.tick())
        assertEquals(3, vLines().size)
        // The F line closes the pass, as for later sweeps, so it can carry the duration.
        assertEquals("F t${io.now} sweep=1 items=3 ms=0", fLines().single())
        assertEquals(sink.lines.last(), fLines().single())
        assertTrue(sink.lines.indexOfFirst { it.startsWith("V ") } < sink.lines.indexOf(fLines().single()))
        val triples = io.batches.flatten()
        assertEquals(6, triples.size)
        assertEquals(3, triples.count { it.tx == 5 })
        assertEquals(3, triples.count { it.tx == 7 })
        assertEquals(1, s.sweeps)
    }

    @Test
    fun largeScanListIsReadInBatchesOf128Triples() = runTest {
        io.catalogText = "BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012\n" +
            (1..130).joinToString("\n") { "Engine.ENGINE_X$it=$it" }
        io.listText = BIG_LIST
        session().tick()
        assertEquals(listOf(128, 128, 4), io.batches.map { it.size })
        assertEquals(130, vLines().size)
    }

    @Test
    fun secondSweepWritesOnlyChanges() = runTest {
        val s = session()
        s.tick()
        io.values[2] = 99
        io.now += 5 * MIN
        s.tick()
        assertEquals(4, vLines().size)
        assertTrue(vLines().last().contains("ENGINE_B") && vLines().last().contains("i=99"))
        assertTrue(fLines().last().startsWith("F t${io.now} sweep=2 ms="))
        assertTrue(fLines().last().endsWith("changed=1"))
    }

    @Test
    fun noSweepBeforeFiveMinutes() = runTest {
        val s = session()
        s.tick()
        io.values[2] = 99
        io.now += 4 * MIN
        s.tick()
        assertEquals(1, fLines().size)
        assertEquals(3, vLines().size)
    }

    @Test
    fun modeChangeSweepsAfterOneMinute() = runTest {
        val s = session()
        s.tick()
        io.liveData = di(chargeGunState = 2)
        io.now += 61_000L
        s.tick()
        assertTrue(sink.lines.any { it.startsWith("S ") && it.contains("mode=CHARGE_AC") })
        assertEquals(2, s.sweeps)
    }

    @Test
    fun modeChangeInsideFirstMinuteWaitsForTheGap() = runTest {
        val s = session()
        s.tick()
        io.liveData = di(chargeGunState = 2)
        io.now += 30_000L
        s.tick()
        assertEquals(1, s.sweeps)
        io.now += 31_000L
        s.tick()
        assertEquals(2, s.sweeps)
    }

    @Test
    fun recorderRestartedAndBytesKept() = runTest {
        val s = session()
        s.tick()
        assertEquals(1, io.recStartCalls)
        assertTrue(s.pushRunning)
        io.status = recStatus(true, "/a", 1000)
        io.now += 10_000L
        s.tick()
        assertEquals(1, io.recStartCalls)
        assertEquals(1000L, s.usedBytes - sink.bytes)
        io.status = recStatus(false, "/a", 1000)
        io.now += 50_000L // 60 s after the good start: the minimum restart interval is over
        s.tick()
        assertEquals(2, io.recStartCalls)
        assertEquals(1000L, s.usedBytes - sink.bytes)
    }

    @Test
    fun stoppedRecorderIsNotCountedTwiceWhileRestartKeepsFailing() = runTest {
        val s = session()
        s.tick()
        io.status = recStatus(true, "/a", 1000)
        io.now += 10_000L
        s.tick()
        io.startResult = null
        io.status = recStatus(false, "/a", 1000)
        repeat(3) { io.now += 70_000L; s.tick() }
        assertEquals(1000L, s.usedBytes - sink.bytes)
        assertTrue(eLines().any { it.contains("recStart") })
    }

    @Test
    fun newFilePathMovesOldBytesToFinished() = runTest {
        val s = session()
        s.tick()
        io.status = recStatus(true, "/a", 1000)
        io.now += 10_000L
        s.tick()
        io.status = recStatus(true, "/b", 200)
        io.now += 10_000L
        s.tick()
        assertEquals(1200L, s.usedBytes - sink.bytes)
        assertEquals(1, io.recStartCalls)
    }

    @Test
    fun budgetStopsMidSweep() = runTest {
        val s = session(usedBefore = BUDGET_BYTES - 10)
        assertEquals(TickResult.BUDGET_REACHED, s.tick())
        assertEquals(0, vLines().size)
        assertTrue(s.usedBytes >= BUDGET_BYTES)
    }

    @Test
    fun budgetCrossedInsideASweepStopsWritingAtOnce() = runTest {
        val probeSink = FakeSink()
        FidScanSession(FakeIo(), probeSink, 0L).tick()
        val firstV = probeSink.lines.indexOfFirst { it.startsWith("V ") }
        val upToFirstV = probeSink.lines.take(firstV + 1).sumOf { it.length + 1L }
        val s = FidScanSession(io, sink, BUDGET_BYTES - upToFirstV)
        assertEquals(TickResult.BUDGET_REACHED, s.tick())
        assertEquals(1, vLines().size)
        assertTrue(fLines().none { it.contains("changed=") })
    }

    @Test
    fun recorderNotRestartedOnceBudgetReached() = runTest {
        val s = session()
        s.tick()
        io.status = recStatus(true, "/a", BUDGET_BYTES)
        io.now += 10_000L
        assertEquals(TickResult.BUDGET_REACHED, s.tick())
        io.status = recStatus(false, "/a", BUDGET_BYTES)
        io.now += 10_000L
        s.tick()
        assertEquals(1, io.recStartCalls)
    }

    @Test
    fun catalogNullRetriesAfterAMinute() = runTest {
        io.catalogText = null
        val s = session()
        s.tick()
        assertEquals(1, io.catalogCalls)
        assertEquals(1, eLines().count { it.contains("catalog") })
        io.now += 30_000L
        s.tick()
        assertEquals(1, io.catalogCalls)
        io.now += 31_000L
        s.tick()
        assertEquals(2, io.catalogCalls)
    }

    @Test
    fun catalogThrowIsLoggedAndRetried() = runTest {
        io.catalogThrows = IllegalStateException("boom")
        val s = session()
        assertEquals(TickResult.RUNNING, s.tick())
        assertTrue(eLines().any { it.contains("catalog: boom") })
        io.catalogThrows = null
        io.now += 61_000L
        s.tick()
        assertEquals(3, vLines().size)
    }

    @Test
    fun readBatchThrowsKeepsRunning() = runTest {
        val s = session()
        io.readThrows = java.io.IOException("binder died")
        assertEquals(TickResult.RUNNING, s.tick())
        assertEquals("E t${io.now} batch failed dev=1012 from=Engine.ENGINE_A size=3 ms=0: binder died", eLines().single())
        assertEquals(0, s.sweeps)
        io.readThrows = null
        io.now += 5 * MIN
        s.tick()
        assertEquals(3, vLines().size)
        assertEquals(1, s.sweeps)
    }

    @Test
    fun readBatchNullStopsTheSweepWithAnELine() = runTest {
        io.readNull = true
        val s = session()
        assertEquals(TickResult.RUNNING, s.tick())
        assertEquals("E t${io.now} batch failed dev=1012 from=Engine.ENGINE_A size=3 ms=0: no answer", eLines().single())
        assertEquals(0, vLines().size)
    }

    @Test
    fun sinkThrowsDoesNotEscape() = runTest {
        sink.failWrites = true
        val s = session()
        assertEquals(TickResult.RUNNING, s.tick())
    }

    @Test
    fun cancellationIsRethrown() = runTest {
        io.readThrows = CancellationException("stop")
        val s = session()
        try {
            s.tick()
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("stop", e.message)
        }
    }

    @Test
    fun recStartThrowIsLogged() = runTest {
        val s = object : ScanIo by io {
            override suspend fun recStart(devices: IntArray): FidRecStart? = throw IllegalStateException("no daemon")
        }
        val session = FidScanSession(s, sink, 0L)
        assertEquals(TickResult.RUNNING, session.tick())
        assertEquals("E t${io.now} recStart: no daemon", eLines().single())
        assertEquals("P t${io.now} push start ms=0 devices=- registered=- error=no daemon", pLines().single())
        assertEquals(3, vLines().size)
    }

    @Test
    fun finishStopsRecorderAndCloses() = runTest {
        val s = session()
        s.tick()
        s.finish("user")
        assertTrue(sink.lines.last().startsWith("B t") && sink.lines.last().endsWith(" user"))
        assertEquals(1, io.stopCalls)
        assertTrue(sink.closed)
    }

    @Test
    fun catalogThrowBacksOffForAMinute() = runTest {
        io.catalogThrows = IllegalStateException("boom")
        val s = session()
        s.tick()
        io.now += 30_000L
        s.tick()
        assertEquals(1, io.catalogCalls)
        assertEquals(1, eLines().count { it.contains("catalog") })
        io.now += 31_000L
        s.tick()
        assertEquals(2, io.catalogCalls)
    }

    @Test
    fun recStartThrowBacksOffForAMinute() = runTest {
        var calls = 0
        val thrower = object : ScanIo by io {
            override suspend fun recStart(devices: IntArray): FidRecStart? { calls++; throw IllegalStateException("no daemon") }
        }
        val session = FidScanSession(thrower, sink, 0L)
        session.tick()
        io.now += 30_000L
        session.tick()
        assertEquals(1, calls)
        io.now += 31_000L
        session.tick()
        assertEquals(2, calls)
    }

    @Test
    fun zeroRegistrationStartBacksOffAndIsLogged() = runTest {
        io.startResult = FidRecStart(2, 0)
        val s = session()
        s.tick()
        assertEquals(1, io.recStartCalls)
        assertTrue(eLines().any { it.contains("no fid registered") })
        assertFalse(s.pushRunning)
        io.status = recStatus(false, "/a", 0)
        io.now += 30_000L
        s.tick()
        assertEquals(1, io.recStartCalls)
        io.now += 31_000L
        s.tick()
        assertEquals(2, io.recStartCalls)
    }

    @Test
    fun stoppedRecorderWaitsAMinuteAfterAGoodStart() = runTest {
        val s = session()
        s.tick()
        io.status = recStatus(false, "/a", 5)
        io.now += 10_000L
        s.tick()
        assertEquals(1, io.recStartCalls)
        io.now += 40_000L
        s.tick()
        assertEquals(1, io.recStartCalls)
        io.now += 10_000L
        s.tick()
        assertEquals(2, io.recStartCalls)
    }

    // --- I1: per-batch time guard, failing chunks ---

    @Test
    fun slowBatchHalvesTheNextCallSizeForTheSession() = runTest {
        io.catalogText = BIG_CATALOG
        io.listText = BIG_LIST
        var first = true
        io.readCostMs = { if (first) 1500L.also { first = false } else 0L }
        val s = session()
        val t0 = io.now
        s.tick()
        assertEquals(listOf(128, 64, 64, 4), io.batches.map { it.size })
        assertEquals(130, vLines().size)
        assertEquals("E t$t0 slow batch 1500 ms dev=1012 from=Engine.ENGINE_X1 size=64", eLines().single())
        assertEquals(1, s.sweeps)
        io.batches.clear()
        io.now += 5 * MIN
        s.tick()
        assertEquals(listOf(64, 64, 64, 64, 4), io.batches.map { it.size })
    }

    @Test
    fun aBatchOfExactlyTheLimitIsNotSlow() = runTest {
        io.catalogText = BIG_CATALOG
        io.listText = BIG_LIST
        io.readCostMs = { SLOW_BATCH_MS }
        session().tick()
        assertEquals(listOf(128, 128, 4), io.batches.map { it.size })
        assertTrue(eLines().isEmpty())
    }

    @Test
    fun batchSizeNeverGoesBelowEight() = runTest {
        io.catalogText = BIG_CATALOG
        io.listText = BIG_LIST
        io.readCostMs = { 1500L }
        val s = session()
        s.tick()
        assertEquals(listOf(128, 64, 32, 16, 16, 4), io.batches.map { it.size })
        assertEquals(130, vLines().size)
        io.batches.clear()
        io.now += 5 * MIN
        s.tick()
        assertTrue(io.batches.all { it.size <= 2 * MIN_BATCH_SYMBOLS })
        assertEquals(16, io.batches.count { it.size == 2 * MIN_BATCH_SYMBOLS })
    }

    @Test
    fun chunkFailingInTwoSweepsIsSkippedInTheThird() = runTest {
        io.catalogText = BIG_CATALOG
        io.listText = BIG_LIST
        io.failBatch = { batch -> batch.any { it.fid == 1 } }
        val s = session()
        s.tick()
        assertEquals("E t${io.now} batch failed dev=1012 from=Engine.ENGINE_X1 size=64 ms=0: no answer", eLines().single())
        io.now += 5 * MIN
        s.tick()
        assertEquals(
            listOf(
                "E t${io.now} batch failed dev=1012 from=Engine.ENGINE_X1 size=64 ms=0: no answer",
                "E t${io.now} skipping 64 symbols from dev=1012 Engine.ENGINE_X1 (failed twice)",
            ),
            eLines().drop(1),
        )
        assertEquals(0, vLines().size)
        assertEquals(0, s.sweeps)
        io.batches.clear()
        io.now += 5 * MIN
        s.tick()
        assertEquals(listOf(128, 4), io.batches.map { it.size })
        assertTrue(io.batches.flatten().all { it.fid > 64 })
        assertEquals(66, vLines().size)
        assertEquals(1, s.sweeps)
        assertEquals(3, eLines().size)
    }

    @Test
    fun aSuccessfulReadResetsTheFailureCount() = runTest {
        var failing = true
        io.failBatch = { failing }
        val s = session()
        s.tick()
        failing = false
        io.now += 5 * MIN
        s.tick()
        failing = true
        io.now += 5 * MIN
        s.tick()
        failing = false
        io.batches.clear()
        io.now += 5 * MIN
        s.tick()
        assertEquals(6, io.batches.single().size)
        assertTrue(eLines().none { it.contains("skipping") })
        assertEquals(2, s.sweeps)
    }

    @Test
    fun budgetCrossedInALaterSweepStopsWritingAtOnce() = runTest {
        val s = session()
        s.tick()
        assertEquals(3, vLines().size)
        io.values[1] = 91
        io.values[2] = 92
        io.values[3] = 93
        io.status = recStatus(true, "/a", BUDGET_BYTES - sink.bytes - 1)
        io.now += 5 * MIN
        assertEquals(TickResult.BUDGET_REACHED, s.tick())
        assertEquals(4, vLines().size)
        assertTrue(fLines().none { it.contains("changed=") })
        assertEquals(1, s.sweeps)
    }

    @Test
    fun failedSweepIsNotCounted() = runTest {
        io.readNull = true
        val s = session()
        s.tick()
        assertEquals(0, s.sweeps)
        io.readNull = false
        io.now += 5 * MIN
        s.tick()
        assertEquals(1, s.sweeps)
    }

    // --- I2: push start line, backoff, minimum restart interval ---

    @Test
    fun pushStartWritesAPLineAfterEveryAttempt() = runTest {
        val slow = object : ScanIo by io {
            override suspend fun recStart(devices: IntArray): FidRecStart? {
                io.recStartCalls++
                io.now += 15_000L
                return io.startResult
            }
        }
        io.startResult = FidRecStart(48, 1234)
        val t0 = io.now
        FidScanSession(slow, sink, 0L).tick()
        assertEquals("P t$t0 push start ms=15000 devices=48 registered=1234 error=-", pLines().single())
        assertTrue(eLines().isEmpty())

        val other = FakeSink()
        io.startResult = null
        val t1 = io.now
        FidScanSession(slow, other, 0L).tick()
        assertEquals(
            listOf("P t$t1 push start ms=15000 devices=- registered=- error=no answer", "E t$t1 recStart: no answer"),
            other.lines.filter { it.startsWith("P ") || it.startsWith("E ") },
        )
    }

    @Test
    fun pushStartFailuresBackOffUpToThirtyMinutes() = runTest {
        io.startResult = null
        val calls = mutableListOf<Long>()
        val rec = object : ScanIo by io {
            override suspend fun recStart(devices: IntArray): FidRecStart? {
                calls += io.now
                return io.startResult
            }
        }
        val s = FidScanSession(rec, sink, 0L)
        repeat(3 * 60 * 6) {
            s.tick()
            io.now += 10_000L
        }
        val gaps = calls.zipWithNext { a, b -> (b - a) / 1000 }
        assertEquals(listOf(60L, 120L, 240L, 480L, 960L, 1800L, 1800L), gaps.take(7))
        assertTrue(gaps.all { it <= MAX_PUSH_BACKOFF_MS / 1000 })
    }

    @Test
    fun pushBackoffResetsAfterAGoodStart() = runTest {
        io.startResult = null
        val s = session()
        s.tick() // fails: next try in 60 s
        io.now += 60_000L
        s.tick() // fails: next try in 120 s
        assertEquals(2, io.recStartCalls)
        io.startResult = FidRecStart(2, 5)
        io.now += 120_000L
        s.tick() // good start
        assertEquals(3, io.recStartCalls)
        io.startResult = null
        io.status = recStatus(false, "/a", 0)
        io.now += 60_000L
        s.tick() // the recorder stopped; the restart fails: next try in 60 s again, not 240 s
        assertEquals(4, io.recStartCalls)
        io.now += 50_000L
        s.tick()
        assertEquals(4, io.recStartCalls)
        io.now += 10_000L
        s.tick()
        assertEquals(5, io.recStartCalls)
    }

    // --- minors ---

    @Test
    fun emptyCatalogIsLoggedAndRetried() = runTest {
        val good = io.catalogText
        io.catalogText = ""
        val s = session()
        s.tick()
        assertEquals("E t${io.now} catalog: empty", eLines().single())
        io.now += 30_000L
        s.tick()
        assertEquals(1, io.catalogCalls)
        io.catalogText = good
        io.now += 31_000L
        s.tick()
        assertEquals(2, io.catalogCalls)
        assertEquals(3, vLines().size)
    }

    @Test
    fun sweepReadsOnlyTheListedItems() = runTest {
        io.listText = "Engine.ENGINE_B=2"
        session().tick()
        assertEquals(listOf("V 1012 Engine.ENGINE_B i=20 d=0.0"), vLines())
        assertEquals(listOf(5, 7), io.batches.flatten().map { it.tx })
        assertTrue(io.batches.flatten().all { it.fid == 2 })
    }

    @Test
    fun listHeaderAndSkipLinesAreWrittenOnce() = runTest {
        io.listText = "Engine.ENGINE_A=1\nStatistic.STATISTIC_C=3\nEngine.ENGINE_GONE=99"
        val s = session()
        s.tick()
        io.now += 5 * MIN
        s.tick()
        assertEquals(1, sink.lines.count { it.startsWith("# list:") })
        assertTrue("# list: /d/list.txt lines=3 read=2 devices=1012,1014" in sink.lines)
        assertEquals(listOf("# list skipped not_in_car: 1 Engine.ENGINE_GONE"), sink.lines.filter { it.startsWith("# list skipped") })
        assertEquals(s.listPlan?.items?.size, 2)
    }

    @Test
    fun noListLogsNotChosenAndNeverPolls() = runTest {
        io.listText = null
        val s = session()
        s.tick()
        assertEquals("E t${io.now} list: not chosen", eLines().single())
        assertTrue(io.batches.isEmpty() && io.recStartCalls == 0)
        io.now += 61_000L
        s.tick()
        assertEquals(2, eLines().size)
        assertTrue(io.batches.isEmpty() && io.recStartCalls == 0 && s.listPlan == null)
    }

    @Test
    fun listThrowsLogsTheReasonAndRetriesAfterRetryMs() = runTest {
        io.listThrows = java.io.IOException("file not found: list.txt")
        val s = session()
        s.tick()
        assertEquals("E t${io.now} list: file not found: list.txt", eLines().single())
        io.now += 30_000L
        s.tick()
        assertEquals(1, io.listCalls)
        io.listThrows = null
        io.now += 31_000L
        s.tick()
        assertEquals(2, io.listCalls)
        assertEquals(3, vLines().size)
    }

    @Test
    fun emptyPlanLogsNothingToReadAndRetries() = runTest {
        io.listText = "Engine.ENGINE_A_SET=1\nbad"
        val s = session()
        s.tick()
        assertEquals("E t${io.now} list: nothing to read", eLines().single())
        assertEquals(2, sink.lines.count { it.startsWith("# list skipped") })
        assertTrue(io.recStartCalls == 0 && io.batches.isEmpty())
        io.now += 30_000L
        s.tick()
        assertEquals(1, io.listCalls)
        io.now += 31_000L
        s.tick()
        assertEquals(2, io.listCalls)
        assertEquals(2, eLines().size)
        assertEquals(2, sink.lines.count { it.startsWith("# list skipped") })
        io.listText = DEFAULT_LIST
        io.now += 61_000L
        s.tick()
        assertEquals(3, vLines().size)
    }

    @Test
    fun pushStartsOnlyAfterTheListWithItsDevices() = runTest {
        io.listText = null
        val s = session()
        s.tick()
        assertEquals(0, io.recStartCalls)
        io.listText = "Power.POWER_X=1005\nGb.GB_X=1\nPower.POWER_Y=2"
        io.catalogText = ""
        io.now += 61_000L
        s.tick()
        assertEquals(0, io.recStartCalls)
        io.catalogText = "BYDAutoConstants.BYDAUTO_DEVICE_POWER=1005\nBYDAutoConstants.BYDAUTO_DEVICE_GB=1039\n" +
            "Power.POWER_X=1005\nGb.GB_X=1\nPower.POWER_Y=2"
        io.now += 61_000L
        s.tick()
        assertEquals(listOf(listOf(1005, 1039)), io.recDevices)
    }

    private fun devicesList(n: Int): Pair<String, String> {
        val devs = (1000..1000 + n).filter { it != EXCLUDED_DEVICE }.take(n)
        val cat = devs.joinToString("\n") { "BYDAutoConstants.BYDAUTO_DEVICE_D$it=$it" } + "\n" +
            devs.joinToString("\n") { "D$it.X_NAME=$it" }
        return devs.joinToString("\n") { "D$it.X_NAME=$it" } to cat
    }

    @Test
    fun moreThanMaxDevicesNeverStartsThePushAndLogsOnce() = runTest {
        val (list, cat) = devicesList(MAX_REC_DEVICES + 1)
        io.listText = list
        io.catalogText = cat
        val s = session()
        s.tick()
        io.now += 61_000L
        s.tick()
        assertEquals(0, io.recStartCalls)
        assertEquals(1, eLines().size)
        assertTrue(eLines().single().endsWith(" push: too many devices (65)"))
        assertTrue(vLines().isNotEmpty())
    }

    @Test
    fun exactlyMaxDevicesStartsThePush() = runTest {
        val (list, cat) = devicesList(MAX_REC_DEVICES)
        io.listText = list
        io.catalogText = cat
        session().tick()
        assertEquals(1, io.recStartCalls)
        assertEquals(MAX_REC_DEVICES, io.recDevices.single().size)
    }

    @Test
    fun lineBreaksInMessagesBecomeSpaces() = runTest {
        io.readThrows = java.io.IOException("binder\ndied")
        io.startResult = FidRecStart(0, 0, "no\r\nfile")
        val s = session()
        s.tick()
        s.finish("stop\nnow")
        val thrower = FakeSink()
        io.catalogThrows = IllegalStateException("a\nb")
        FidScanSession(io, thrower, 0L).tick()
        val all = sink.lines + thrower.lines
        assertTrue(all.none { '\n' in it || '\r' in it })
        assertTrue(eLines().any { it.endsWith("ms=0: binder died") })
        assertTrue(eLines().any { it.endsWith("recStart: no  file") })
        assertTrue(pLines().single().endsWith("error=no  file"))
        assertTrue(sink.lines.last().endsWith(" stop now"))
        assertTrue(thrower.lines.any { it.endsWith("catalog: a b") })
    }

    @Test
    fun engineStartAndStopWriteAStateLineWithoutASweep() = runTest {
        io.liveData = di(gear = 1, powerState = 1, engineRpm = 0)
        val s = session()
        s.tick()
        assertEquals(1, sLines().size)
        io.liveData = di(gear = 1, powerState = 1, engineRpm = 1200)
        io.now += 10_000L
        s.tick()
        assertEquals(2, sLines().size)
        assertTrue(sLines().last().contains(" rpm=1200 "))
        io.liveData = di(gear = 1, powerState = 1, engineRpm = 1300)
        io.now += 10_000L
        s.tick()
        assertEquals(2, sLines().size)
        // The engine stops in the same tick as the mode changes: still one S line.
        io.liveData = di(gear = 4, powerState = 1, engineRpm = 0)
        io.now += 10_000L
        s.tick()
        assertEquals(3, sLines().size)
        assertEquals(1, s.sweeps)
        assertEquals(1, fLines().size)
    }
}
