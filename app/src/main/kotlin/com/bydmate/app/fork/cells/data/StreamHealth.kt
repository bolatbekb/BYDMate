package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellReading
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot
import com.bydmate.app.fork.cells.stream.CellTx

enum class StreamState { IDLE, CONNECTING, OK, NO_STREAM, MISMATCH, HELPER_MISSING, HELPER_OUTDATED, LISTEN_FAILED }

/** [reportedCells]/[reportedProbes] are the pack's own counts on MISMATCH; [detail] the helper's text. */
data class StreamStatus(
    val state: StreamState,
    val reportedCells: Int = -1,
    val reportedProbes: Int = -1,
    val detail: String = "",
)

/**
 * What the collector, the window and the car-screen row say about the stream, from the answers of
 * the [CellSource]. Also paces the collector: while the car sends nothing or the listener is refused,
 * one request a minute is enough.
 */
class StreamHealth {

    @Volatile var status: StreamStatus = StreamStatus(StreamState.IDLE)
        private set

    fun onListen(r: SourceReply<CellListenReply>): StreamStatus = set(
        when (r) {
            is SourceReply.Ok -> afterListen(r.value)
            SourceReply.HelperMissing -> StreamStatus(StreamState.HELPER_MISSING)
            SourceReply.HelperOutdated -> StreamStatus(StreamState.HELPER_OUTDATED)
            is SourceReply.Failed -> StreamStatus(StreamState.LISTEN_FAILED, detail = r.detail)
        },
    )

    /** [reading] is readingOf() of the snapshot for the chosen car; null when there is none. */
    fun onSnapshot(r: SourceReply<CellStreamSnapshot>, reading: CellReading?): StreamStatus = set(
        when (r) {
            is SourceReply.Ok -> fromSnapshot(r.value, reading)
            SourceReply.HelperMissing -> StreamStatus(StreamState.HELPER_MISSING)
            SourceReply.HelperOutdated -> StreamStatus(StreamState.HELPER_OUTDATED)
            is SourceReply.Failed -> StreamStatus(StreamState.CONNECTING, detail = r.detail)
        },
    )

    fun minRequestGapMs(): Long = if (status.state in SLOW_STATES) SLOW_GAP_MS else 0L

    private fun set(next: StreamStatus): StreamStatus {
        status = next
        return next
    }

    private fun afterListen(reply: CellListenReply): StreamStatus = when (reply.registration) {
        CellTx.REG_FAILED, CellTx.REG_NOT_IN_MAP -> StreamStatus(StreamState.LISTEN_FAILED, detail = reply.detail)
        CellTx.REG_OFF -> StreamStatus(StreamState.IDLE)
        // Registered, or the keepalive of a live listener: a verdict on the stream itself stays.
        else -> if (status.state == StreamState.NO_STREAM) status else waiting()
    }

    private fun fromSnapshot(s: CellStreamSnapshot, reading: CellReading?): StreamStatus {
        if (!s.listening) {
            val refused = s.registration == CellTx.REG_FAILED || s.registration == CellTx.REG_NOT_IN_MAP
            // The snapshot carries the verdict but not the text: the listen answer's text stays.
            return if (refused) {
                StreamStatus(StreamState.LISTEN_FAILED, detail = status.detail.takeIf { status.state == StreamState.LISTEN_FAILED }.orEmpty())
            } else {
                // The collector re-sends listen.
                StreamStatus(StreamState.CONNECTING)
            }
        }
        // Quiet since the listener came up (no frame yet) or since the last frame.
        if (s.nowMs - maxOf(s.listeningSinceMs, s.lastFrameMs) >= NO_STREAM_AFTER_MS) return StreamStatus(StreamState.NO_STREAM)
        return when (reading) {
            is CellReading.Complete -> StreamStatus(StreamState.OK)
            is CellReading.Mismatch -> StreamStatus(StreamState.MISMATCH, reading.cells, reading.probes)
            else -> waiting()
        }
    }

    /**
     * No verdict in this answer: OK, MISMATCH and CONNECTING stay; anything else is connecting now
     * (a listening snapshot that is not quiet means frames are coming again after NO_STREAM).
     */
    private fun waiting(): StreamStatus =
        if (status.state in KEPT_STATES) status else StreamStatus(StreamState.CONNECTING)

    private companion object {
        const val SLOW_GAP_MS = 60_000L
        const val NO_STREAM_AFTER_MS = 60_000L
        val SLOW_STATES = setOf(StreamState.NO_STREAM, StreamState.LISTEN_FAILED)
        val KEPT_STATES = setOf(StreamState.OK, StreamState.MISMATCH, StreamState.CONNECTING)
    }
}
