package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import kotlin.math.max
import kotlin.math.min

/* ---------------------------------------------------------------- value and chart motion */

private val ValueSpec = tween<Float>(500, easing = FastOutSlowInEasing)

/** The short ease of a quantized value (rpm): at most 150 ms, so a step never keeps the frames running. */
internal val RpmSpec = tween<Float>(RPM_TWEEN_MS, easing = FastOutSlowInEasing)
internal const val RPM_TWEEN_MS = 150
internal const val RPM_STEP = 100
private const val RPM_HYSTERESIS = 50

/**
 * Engine speed in [RPM_STEP]-rpm steps with a ±[RPM_HYSTERESIS] dead band: [held] (the step shown
 * so far) stays until [raw] is more than a step plus the band (150 rpm) away from it, then it
 * snaps to the nearest step of [raw]. Null stays null; negative reads as 0.
 */
internal fun quantizeRpm(raw: Int?, held: Int?): Int? {
    if (raw == null) return null
    val r = raw.coerceAtLeast(0)
    if (held != null && kotlin.math.abs(r - held) <= RPM_STEP + RPM_HYSTERESIS) return held
    return (r + RPM_STEP / 2) / RPM_STEP * RPM_STEP
}

/** [rpm] as a held step (see [quantizeRpm]); recomposes only when the step changes. */
@Composable
internal fun rememberQuantizedRpm(rpm: Int?): Int? {
    var held by remember { mutableStateOf<Int?>(null) }
    val q = quantizeRpm(rpm, held)
    if (q != held) held = q
    return q
}

/** Ease only between two known values: the first one after "unknown" (open, data arriving) is snapped. */
internal fun shouldAnimate(previous: Float?, target: Float?): Boolean = previous != null && target != null

/** The chart grows in only when the user moves to another period; the first series (and a return home) shows grown. */
internal fun shouldGrow(lastShown: ChartPeriod?, period: ChartPeriod): Boolean =
    lastShown != null && lastShown != period

/**
 * [target] eased over 500 ms on every change between known values; while it is null the value is 0,
 * and the first known value is snapped (no sweep from 0). Read `.value` inside a draw lambda so a
 * change redraws without recomposing.
 */
@Suppress("UnusedParameter") // [label] names the call site, as animateFloatAsState's did
@Composable
internal fun animatedValue(target: Float?, label: String, spec: AnimationSpec<Float> = ValueSpec): State<Float> {
    val anim = remember { Animatable(target ?: 0f) }
    var previous by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == null) {
            anim.snapTo(0f)
        } else if (target == anim.value && !anim.isRunning) {
            // already there (the first composition with a known value): no 500 ms tween to nowhere
        } else if (shouldAnimate(previous, target)) {
            anim.animateTo(target, spec)
        } else {
            anim.snapTo(target)
        }
        previous = target
    }
    return anim.asState()
}

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
