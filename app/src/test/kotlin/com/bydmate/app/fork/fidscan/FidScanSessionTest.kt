package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.helper.push.FidRecStart
import com.bydmate.app.helper.push.FidRecStatus
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
    var readThrows: Throwable? = null
    var readNull = false
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
        if (readNull) return null
        return items.map { if (it.tx == TX_INT) 0 to (values[it.fid] ?: 10 * it.fid) else 0 to 0 }
    }
    override suspend fun recStart(): FidRecStart? { recStartCalls++; return startResult }
    override suspend fun recStatus(): FidRecStatus? = status
    override suspend fun recStop() { stopCalls++ }
    override fun live(): DiParsData? = liveData
    override fun nowMs(): Long = now
    override fun timeText(ms: Long): String = "t$ms"
}

private fun recStatus(running: Boolean, path: String, bytes: Long) =
    FidRecStatus(running, emptyList(), 0, emptyList(), path, bytes)

@OptIn(ExperimentalCoroutinesApi::class)
class FidScanSessionTest {
    private val io = FakeIo()
    private val sink = FakeSink()
    private fun session(usedBefore: Long = 0L) = FidScanSession(io, sink, usedBefore)
    private fun vLines() = sink.lines.filter { it.startsWith("V ") }
    private fun fLines() = sink.lines.filter { it.startsWith("F ") }
    private fun eLines() = sink.lines.filter { it.startsWith("E ") }

    @Test
    fun firstSweepWritesEveryItemTwiceRead() = runTest {
        val s = session()
        assertEquals(TickResult.RUNNING, s.tick())
        assertEquals(3, vLines().size)
        assertTrue(fLines().single().endsWith("sweep=1 items=3"))
        val fIdx = sink.lines.indexOfFirst { it.startsWith("F ") }
        assertTrue(sink.lines.subList(fIdx + 1, sink.lines.size).all { it.startsWith("V ") })
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
        io.now += 10_000L
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
        assertTrue(eLines().any { it.contains("sweep: binder died") })
        io.readThrows = null
        io.now += 5 * MIN
        s.tick()
        assertEquals(3, vLines().size)
        assertEquals(2, s.sweeps)
    }

    @Test
    fun readBatchNullStopsTheSweepWithAnELine() = runTest {
        io.readNull = true
        val s = session()
        assertEquals(TickResult.RUNNING, s.tick())
        assertTrue(eLines().any { it.contains("readBatch") })
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
            override suspend fun recStart(): FidRecStart? = throw IllegalStateException("no daemon")
        }
        val session = FidScanSession(s, sink, 0L)
        assertEquals(TickResult.RUNNING, session.tick())
        assertTrue(eLines().any { it.contains("push: no daemon") })
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
            override suspend fun recStart(): FidRecStart? { calls++; throw IllegalStateException("no daemon") }
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
    fun stoppedRecorderRestartsWithoutWaitingAfterAGoodStart() = runTest {
        val s = session()
        s.tick()
        io.status = recStatus(false, "/a", 5)
        io.now += 10_000L
        s.tick()
        assertEquals(2, io.recStartCalls)
    }
}
