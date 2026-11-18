package com.bydmate.app.ui.home.graphite

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** One shared ~12-fps tick for the media card's slow motion (equalizer, cover glow). */
internal const val STEP_TICK_MS = 83L

/**
 * Seconds that advance in [tickMs] steps while [lifecycle] is STARTED and stand still (and are
 * kept) while it is not. [get]/[set] hold the seconds, [now] is a millisecond clock. Never returns.
 */
internal suspend fun runStepClock(
    lifecycle: Lifecycle,
    tickMs: Long,
    now: () -> Long,
    get: () -> Float,
    set: (Float) -> Unit,
) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val start = now() - (get() * 1000).toLong()
        while (true) {
            set((now() - start) / 1000f)
            delay(tickMs)
        }
    }
}

/**
 * The shared step clock in seconds: runs while [running] and the screen is started, one frame per
 * tick for every reader together. Read `.value` only in a draw lambda.
 */
@Composable
internal fun rememberStepClock(running: Boolean): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(running, owner) {
        if (running) {
            runStepClock(owner.lifecycle, STEP_TICK_MS, SystemClock::elapsedRealtime, { clock.floatValue }, { clock.floatValue = it })
        }
    }
    return clock
}

internal const val GLOW_MIN = 0.10f
internal const val GLOW_MAX = 0.28f
private const val GLOW_HALF_S = 2f

/** The cover glow alpha at [seconds]: 0.10 to 0.28, FastOutSlowIn over 2 s each way (a 4-s breath). */
internal fun glowBreath(seconds: Float): Float {
    val x = seconds / (2 * GLOW_HALF_S)
    val phase = (x - kotlin.math.floor(x)) * 2f // 0..2
    val t = if (phase <= 1f) phase else 2f - phase
    return GLOW_MIN + (GLOW_MAX - GLOW_MIN) * FastOutSlowInEasing.transform(t)
}
