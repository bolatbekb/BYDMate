package com.bydmate.app.ui.home.graphite

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/* ---------------------------------------------------------------- media card motion (pure) */

/** A poll this far off the shown position is a seek (or a stall): jump to it. */
internal const val SEEK_TOLERANCE_MS = 1_500L

/** A polled position: [polledMs] as of [polledAtMs], running at [speed] while [playing]; [durationMs] when known. */
internal data class PolledPosition(
    val polledMs: Long,
    val polledAtMs: Long,
    val durationMs: Long?,
    val playing: Boolean,
    val speed: Float = 1f,
)

/**
 * The position to show at [nowMs] (the clock of [poll]): the polled one plus the time since ×
 * speed while playing; at most the duration when known, never below 0, never rewound by a clock
 * that went backwards.
 */
internal fun extrapolatePosition(poll: PolledPosition, nowMs: Long): Long {
    val run = if (poll.playing) ((nowMs - poll.polledAtMs).coerceAtLeast(0L) * poll.speed.coerceAtLeast(0f)).toLong() else 0L
    val pos = (poll.polledMs + run).coerceAtLeast(0L)
    val durationMs = poll.durationMs
    return if (durationMs != null && durationMs > 0) pos.coerceAtMost(durationMs) else pos
}

/**
 * Whether [poll] must replace [anchor], the poll the shown position runs from: when paused, when
 * play state, track length or speed changed, or when it is a seek ([isSeek]) at [nowMs].
 */
internal fun reanchors(anchor: PolledPosition, poll: PolledPosition, nowMs: Long): Boolean = when {
    !poll.playing || anchor.playing != poll.playing -> true
    anchor.durationMs != poll.durationMs || anchor.speed != poll.speed -> true
    else -> isSeek(poll.polledMs, extrapolatePosition(anchor, nowMs))
}

/** Whether a fresh poll should replace the running extrapolation. */
internal fun isSeek(polledMs: Long, shownMs: Long): Boolean = abs(polledMs - shownMs) > SEEK_TOLERANCE_MS

// Per bar: two periods (s) and phases, so no two bars move together.
private val EqSlow = floatArrayOf(0.71f, 0.53f, 0.83f, 0.61f)
private val EqFast = floatArrayOf(0.29f, 0.37f, 0.23f, 0.41f)
private val EqPhase = floatArrayOf(0.0f, 1.7f, 3.1f, 4.4f)

/** Height of equalizer bar [bar] (0..3) at [tSec], 0.25..1.0: two sines, 60 / 40. */
internal fun eqLevel(bar: Int, tSec: Float): Float {
    val i = bar.mod(EqSlow.size)
    val w = 2 * PI.toFloat()
    val s = 0.6f * sin(w * tSec / EqSlow[i] + EqPhase[i]) + 0.4f * sin(w * tSec / EqFast[i] + 2 * EqPhase[i])
    return (0.625f + 0.375f * s).coerceIn(0.25f, 1f)
}
