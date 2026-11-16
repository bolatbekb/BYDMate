package com.bydmate.app.ui.home.graphite

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/* ---------------------------------------------------------------- media card motion (pure) */

/** A poll this far off the shown position is a seek (or a stall): jump to it. */
internal const val SEEK_TOLERANCE_MS = 1_500L

/**
 * The position to show at [nowMs]: [polledMs] as of [polledAtMs] (same clock), plus the time
 * since × [speed] while [playing]; at most [durationMs] when known, never below 0, never rewound
 * by a clock that went backwards.
 */
internal fun extrapolatePosition(
    polledMs: Long, polledAtMs: Long, nowMs: Long, durationMs: Long?, playing: Boolean, speed: Float = 1f,
): Long {
    val run = if (playing) ((nowMs - polledAtMs).coerceAtLeast(0L) * speed.coerceAtLeast(0f)).toLong() else 0L
    val pos = (polledMs + run).coerceAtLeast(0L)
    return if (durationMs != null && durationMs > 0) pos.coerceAtMost(durationMs) else pos
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
