package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellReading
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.MAX_CELL_PAGES
import com.bydmate.app.fork.cells.stream.MAX_PROBE_PAGES
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamHealthTest {

    private val health = StreamHealth()
    private val complete = CellReading.Complete(IntArray(184) { 3300 }, IntArray(24) { 20 }, 0L, 0L)

    private fun snap(
        nowMs: Long,
        lastFrameMs: Long = 0L,
        listening: Boolean = true,
        registration: Int = CellTx.REG_OK,
        listeningSinceMs: Long = 0L,
    ) = SourceReply.Ok(
        CellStreamSnapshot(
            nowMs = nowMs, listening = listening, registration = registration, listeningSinceMs = listeningSinceMs,
            lastFrameMs = lastFrameMs, frames = 0L, rejected = 0L, declaredCells = -1, declaredProbes = -1,
            cellMv = IntArray(MAX_CELL_PAGES * 3), cellPageMs = LongArray(MAX_CELL_PAGES),
            probeC = IntArray(MAX_PROBE_PAGES * 6), probePageMs = LongArray(MAX_PROBE_PAGES),
            hottest = -1, hottestC = 0, coldest = -1, coldestC = 0,
        ),
    )

    @Test fun `starts idle and asks nothing to wait`() {
        assertEquals(StreamState.IDLE, health.status.state)
        assertEquals(0L, health.minRequestGapMs())
    }

    @Test fun `no frames for a minute of listening is NO_STREAM and slows requests`() {
        health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_OK, "OK")))
        assertEquals(StreamState.CONNECTING, health.onSnapshot(snap(nowMs = 59_999L), CellReading.NoFrames).state)
        assertEquals(0L, health.minRequestGapMs())

        assertEquals(StreamState.NO_STREAM, health.onSnapshot(snap(nowMs = 60_000L), CellReading.NoFrames).state)
        assertEquals(60_000L, health.minRequestGapMs())
        // The keepalive listen does not hide it.
        assertEquals(StreamState.NO_STREAM, health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_OK, "OK"))).state)
    }

    @Test fun `frames resume to OK`() {
        health.onSnapshot(snap(nowMs = 90_000L), CellReading.NoFrames)
        assertEquals(StreamState.NO_STREAM, health.status.state)

        assertEquals(StreamState.OK, health.onSnapshot(snap(nowMs = 150_000L, lastFrameMs = 149_900L), complete).state)
        assertEquals(0L, health.minRequestGapMs())
    }

    @Test fun `the stream stopping for a minute is NO_STREAM`() {
        health.onSnapshot(snap(nowMs = 10_000L, lastFrameMs = 9_900L), complete)
        val stale = CellReading.Incomplete(62, 4)
        assertEquals(StreamState.OK, health.onSnapshot(snap(nowMs = 69_000L, lastFrameMs = 9_900L), stale).state)
        assertEquals(StreamState.NO_STREAM, health.onSnapshot(snap(nowMs = 69_900L, lastFrameMs = 9_900L), stale).state)
    }

    @Test fun `mismatch reports the numbers`() {
        val s = health.onSnapshot(snap(nowMs = 5_000L, lastFrameMs = 4_900L), CellReading.Mismatch(120, 16))
        assertEquals(StreamStatus(StreamState.MISMATCH, reportedCells = 120, reportedProbes = 16), s)
        // Until a matching reading.
        assertEquals(s, health.onSnapshot(snap(nowMs = 6_000L, lastFrameMs = 5_900L), CellReading.Incomplete(1, 0)))
        assertEquals(StreamState.OK, health.onSnapshot(snap(nowMs = 7_000L, lastFrameMs = 6_900L), complete).state)
    }

    @Test fun `not listening asks to reconnect`() {
        health.onSnapshot(snap(nowMs = 7_000L, lastFrameMs = 6_900L), complete)
        val s = health.onSnapshot(snap(nowMs = 300_000L, listening = false, registration = CellTx.REG_OFF), CellReading.NoFrames)
        assertEquals(StreamState.CONNECTING, s.state)
    }

    @Test fun `incomplete readings keep the state and leave idle for connecting`() {
        assertEquals(StreamState.CONNECTING, health.onSnapshot(snap(nowMs = 1_000L, lastFrameMs = 900L), CellReading.Incomplete(3, 1)).state)
        health.onSnapshot(snap(nowMs = 2_000L, lastFrameMs = 1_900L), complete)
        assertEquals(StreamState.OK, health.onSnapshot(snap(nowMs = 3_000L, lastFrameMs = 2_900L), CellReading.Incomplete(3, 1)).state)
    }

    @Test fun `a refused registration is LISTEN_FAILED with its detail and slows requests`() {
        val s = health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_NOT_IN_MAP, "not in feature map")))
        assertEquals(StreamStatus(StreamState.LISTEN_FAILED, detail = "not in feature map"), s)
        assertEquals(60_000L, health.minRequestGapMs())
        // The snapshot carries the same verdict without the text: the text stays.
        val again = health.onSnapshot(snap(nowMs = 1_000L, listening = false, registration = CellTx.REG_NOT_IN_MAP), CellReading.NoFrames)
        assertEquals(s, again)

        val failed = health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_FAILED, "ClassNotFoundException: x")))
        assertEquals(StreamStatus(StreamState.LISTEN_FAILED, detail = "ClassNotFoundException: x"), failed)
    }

    @Test fun `helper answers map to their states`() {
        assertEquals(StreamState.HELPER_MISSING, health.onListen(SourceReply.HelperMissing).state)
        assertEquals(StreamState.HELPER_OUTDATED, health.onSnapshot(SourceReply.HelperOutdated, null).state)
        assertEquals(StreamState.CONNECTING, health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_SENT, "sent"))).state)
        health.onSnapshot(snap(nowMs = 2_000L, lastFrameMs = 1_900L), complete)
        // A keepalive listen keeps OK.
        assertEquals(StreamState.OK, health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_OK, "OK"))).state)
        assertEquals(StreamState.IDLE, health.onListen(SourceReply.Ok(CellListenReply(CellTx.REG_OFF, ""))).state)
        // a transport failure is not the car's refusal
        assertEquals(StreamStatus(StreamState.CONNECTING, detail = "status -1"), health.onListen(SourceReply.Failed("status -1")))
        assertEquals(StreamStatus(StreamState.CONNECTING, detail = "timeout"), health.onSnapshot(SourceReply.Failed("timeout"), null))
    }
}
