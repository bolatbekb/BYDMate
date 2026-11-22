package com.bydmate.app.fork.cells.collect

import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellSource
import com.bydmate.app.fork.cells.data.SourceReply
import com.bydmate.app.fork.cells.data.StreamHealth
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.stream.CellReading
import com.bydmate.app.fork.cells.stream.readingOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One answered snapshot: whether the helper still listens, and the reading for the chosen car. */
internal data class LinkRead(val listening: Boolean, val reading: CellReading)

/**
 * The collector's side of the helper: the two calls, their [StreamHealth] and the pacing of the
 * snapshot requests. Not thread-safe: the collector calls it under its own lock only.
 */
internal class CellLink(private val source: CellSource) {
    private val health = StreamHealth()
    private val status = MutableStateFlow(health.status)
    private val frameMs = MutableStateFlow(0L)
    private var lastRequestMs: Long? = null

    val stream: StateFlow<StreamStatus> = status.asStateFlow()

    /** Helper clock (elapsedRealtime) of the newest frame seen since we began to listen; 0 = none yet. */
    val lastFrameMs: StateFlow<Long> = frameMs.asStateFlow()

    /** We asked the helper to listen and did not let go since. */
    var listening = false
        private set

    /** on: ask for the stream (also the keepalive); off: let it go and forget the frames of this session. */
    suspend fun listen(on: Boolean) {
        listening = on
        if (!on) frameMs.value = 0L
        status.value = health.onListen(source.listen(on))
    }

    /** One request a minute while the car sends nothing or cannot match; a little while the helper is away. */
    fun mayRequest(nowMs: Long): Boolean {
        val last = lastRequestMs ?: return true
        return nowMs < last || nowMs - last >= gapMs()
    }

    /** One snapshot read for [profile]; null when the helper did not answer. [windowStartMs]: pages before it are stale. */
    suspend fun read(profile: CarProfile, windowStartMs: Long, nowMs: Long): LinkRead? {
        lastRequestMs = nowMs
        val r = source.snapshot()
        val snap = (r as? SourceReply.Ok)?.value
        val reading = snap?.let { readingOf(it, profile.cells, profile.probes, windowStartMs) }
        status.value = health.onSnapshot(r, reading)
        if (snap == null || reading == null) return null
        if (snap.lastFrameMs > frameMs.value) frameMs.value = snap.lastFrameMs
        return LinkRead(snap.listening, reading)
    }

    private fun gapMs(): Long = maxOf(
        health.minRequestGapMs(),
        when (health.status.state) {
            // The pack or the helper will not change by asking again soon.
            StreamState.MISMATCH, StreamState.HELPER_OUTDATED -> SLOW_GAP_MS
            // The helper may still be starting after a wake.
            StreamState.HELPER_MISSING -> MISSING_GAP_MS
            else -> MIN_GAP_MS
        },
    )

    private companion object {
        const val MIN_GAP_MS = 1_000L
        const val MISSING_GAP_MS = 5_000L
        const val SLOW_GAP_MS = 60_000L
    }
}
