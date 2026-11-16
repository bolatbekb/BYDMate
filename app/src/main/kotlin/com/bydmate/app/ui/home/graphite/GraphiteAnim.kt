package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlin.math.max
import kotlin.math.min

/* ---------------------------------------------------------------- value and chart motion */

private val ValueSpec = tween<Float>(500, easing = FastOutSlowInEasing)

/**
 * [target] eased over 500 ms on every change; the first composition starts at [target] (no
 * sweep from 0). Read `.value` inside a draw lambda so a change redraws without recomposing.
 */
@Composable
internal fun animatedValue(target: Float, label: String): State<Float> =
    animateFloatAsState(target, ValueSpec, label = label)

private const val GROW_MS = 400
private const val GROW_STAGGER_MS = 12
private const val GROW_CAP_MS = 700

/** Length of the chart's grow-in for [n] buckets: 400 ms each, 12 ms apart, at most 700 ms. */
internal fun growTotalMs(n: Int): Int = min(GROW_CAP_MS, GROW_MS + GROW_STAGGER_MS * max(0, n - 1))

/**
 * Linear 0..1 growth of bucket [i] of [n] at [elapsedMs] into the grow-in. The stagger shrinks
 * when 12 ms apart would run past the cap, so the last bucket ends with [growTotalMs].
 */
internal fun barGrowth(elapsedMs: Float, i: Int, n: Int): Float {
    val stagger = if (n <= 1) 0f else (growTotalMs(n) - GROW_MS).toFloat() / (n - 1)
    return ((elapsedMs - i * stagger) / GROW_MS).coerceIn(0f, 1f)
}

/** Engine speed on the fuel dial's inner ring: 0..6 000 rpm. */
internal fun rpmFraction(rpm: Int?): Float = ((rpm ?: 0) / 6000f).coerceIn(0f, 1f)
