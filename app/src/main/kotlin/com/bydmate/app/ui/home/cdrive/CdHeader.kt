@file:Suppress("MagicNumber") // overlays placed on the backplate's pixel grid (tools/cdrive/LAYOUT.md)

package com.bydmate.app.ui.home.cdrive

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.common.K
import com.bydmate.app.ui.home.common.Txt
import com.bydmate.app.ui.home.common.at
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val ModeWhite = Color(0xFFF2FFF4)
private val GearWhite = Color(0xFFEBFFFF)
private val ReadyGreen = Color(0xFF56FFA4)
private val HeaderDim = Color(0xFF6E7673)

/** How often the READY lamp looks at the clock, so its 1 s hold can run out between data samples. */
private const val LAMP_TICK_MS = 250L

/**
 * The black panel at the top right: drive mode (with the leaf for ECO), gear letter and the
 * READY lamp. The printed dividers stay; everything else is drawn here.
 */
@Composable
internal fun CdHeader(data: HomeData) {
    ModeWord(data.driveProfile)
    val gear = gearLetter(data.gear)
    Txt(gear, 2344, 64, 46f, if (gear == "—") HeaderDim else GearWhite, glow = gear != "—", centre = true, width = 80)
    when (rememberReadyLamp(data.powerState)) {
        ReadyShown.READY -> Txt("READY", 2459, 64, 29.5f, ReadyGreen, glow = true, centre = true, width = 110)
        ReadyShown.ON -> Txt("ON", 2459, 64, 29.5f, HeaderDim, centre = true, width = 110)
        ReadyShown.OFF -> Txt("—", 2459, 64, 29.5f, HeaderDim, centre = true, width = 110)
    }
}

/** ECO sits right of the leaf as on the mockup; other modes are centred in the zone, smaller when long. */
@Composable
private fun ModeWord(mode: String?) {
    when {
        mode == null -> Txt("—", 2216, 64, 29.5f, HeaderDim, centre = true, width = 128)
        mode == "ECO" -> {
            Image(painterResource(R.drawable.cd_leaf), null, Modifier.at(2158, 44, 45, 41), contentScale = ContentScale.FillBounds)
            Txt(mode, 2212, 64, 29.5f, ModeWhite, glow = true, width = 70)
        }
        else -> {
            val size = when {
                mode.length <= 4 -> 29.5f
                mode.length <= 6 -> 26f
                else -> 21f
            }
            Txt(mode, 2216, 64, size, ModeWhite, glow = true, centre = true, width = 128)
        }
    }
}

/**
 * The lamp state for [powerState], re-read from the system clock every [LAMP_TICK_MS] while the
 * screen is started, so a short dip is held and a real fall still shows after the hold.
 */
@Composable
private fun rememberReadyLamp(powerState: Int?): ReadyShown {
    val lamp = remember { ReadyLamp(holdMs = 2_000) }
    val power by rememberUpdatedState(powerState)
    var shown by remember { mutableStateOf(lamp.update(powerState, SystemClock.elapsedRealtime())) }
    // a change shows at once (a rise lights READY now); the tick lets a held fall run out
    LaunchedEffect(powerState) { shown = lamp.update(powerState, SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shown = lamp.update(power, SystemClock.elapsedRealtime())
                delay(LAMP_TICK_MS)
            }
        }
    }
    return shown
}

/* ---------------------------------------------------------------- spectrum */

/** The printed LED matrix: 107 columns, 14.41 px apart, inside 562, 16, 1543 x 94 (LAYOUT.md). */
internal const val SPECTRUM_COLUMNS = 107
private const val SPEC_X = 562f
private const val SPEC_Y = 16
private const val SPEC_W = 1543
private const val SPEC_H = 94
private const val SPEC_PITCH = 14.41f

/** Dim while paused or silent: the print at 80 % brightness. */
private const val PAUSED_DIM = 0.2f
private const val MIN_SWING = 0.08f
private const val MAX_SWING = 0.15f
private const val MIN_PERIOD_S = 2.0
private const val MAX_PERIOD_S = 4.0
/** The phase wobble that keeps a column from beating like a metronome: depth (rad) and period ratio. */
private const val WOBBLE = 0.3
private const val WOBBLE_RATIO = 2.7

/** At most 30 redraws a second; the frames between are skipped. */
private const val MIN_FRAME_NS = 33_000_000L
private const val FRAME_WAKE_MS = 30L

/** A fixed pseudo-random number in [0, 1) for column [c] and channel [k]. */
private fun hash01(c: Int, k: Int): Double {
    var h = c * 0x9E3779B1.toInt() xor (k * 0x85EBCA77.toInt())
    h = h xor (h ushr 16)
    h *= 0x7FEB352D
    h = h xor (h ushr 15)
    h *= 0x846CA68B.toInt()
    h = h xor (h ushr 16)
    return (h.toLong() and 0xFFFFFFFFL) / 4_294_967_296.0
}

/**
 * How much column [column] of the printed matrix is darkened at [timeSec], 0..1 (0 = the print as is).
 *
 * Playing: each column swings between 0 and its own depth (8–15 %) with its own period (2–4 s) and
 * phase; a slow phase wobble makes the motion a smooth noise rather than a metronome. Paused or
 * silent: a static [PAUSED_DIM].
 */
internal fun spectrumDim(timeSec: Double, column: Int, playing: Boolean): Float {
    if (!playing) return PAUSED_DIM
    val swing = MIN_SWING + (MAX_SWING - MIN_SWING) * hash01(column, 1).toFloat()
    val period = MIN_PERIOD_S + (MAX_PERIOD_S - MIN_PERIOD_S) * hash01(column, 2)
    val phase = 2 * PI * hash01(column, 3)
    val wobblePhase = 2 * PI * hash01(column, 4)
    val w = 2 * PI / period
    val wave = sin(w * timeSec + phase + WOBBLE * sin(w / WOBBLE_RATIO * timeSec + wobblePhase))
    return (swing * (0.5 + 0.5 * wave)).toFloat().coerceIn(0f, swing)
}

/** How long play ↔ pause takes to blend between the moving and the static dim. */
private const val BLEND_S = 0.4

/**
 * The dim of [column] at [timeSec] while blending from paused ([blend] 0, a static [PAUSED_DIM])
 * to playing ([blend] 1, [spectrumDim]'s motion), eased so neither end jumps.
 */
internal fun spectrumBlendDim(timeSec: Double, column: Int, blend: Float): Float {
    val b = blend.coerceIn(0f, 1f)
    val e = b * b * (3 - 2 * b)
    return PAUSED_DIM + (spectrumDim(timeSec, column, playing = true) - PAUSED_DIM) * e
}

/**
 * Lays a translucent black strip over each column of the printed LED matrix, so the print's own
 * colours stay and only their brightness moves. Animates while [playing] (and while easing into
 * the pause), at most 30 frames a second, only while composed and the screen is started;
 * otherwise one static dim frame. The canvas has its own layer, so a tick redraws only it.
 */
@Composable
internal fun CdSpectrum(playing: Boolean) {
    val time = remember { mutableDoubleStateOf(0.0) }
    val blend = remember { mutableFloatStateOf(if (playing) 1f else 0f) }
    var running by remember { mutableStateOf(playing) }
    LaunchedEffect(playing) { if (playing) running = true }
    val play by rememberUpdatedState(playing)
    val owner = LocalLifecycleOwner.current
    if (running) {
        LaunchedEffect(owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var last = 0L
                while (true) {
                    delay(FRAME_WAKE_MS) // do not wake on every vsync; the frame time below stays real
                    withFrameNanos { now ->
                        if (now - last >= MIN_FRAME_NS) {
                            val dt = if (last == 0L) 0.0 else (now - last) / 1e9
                            last = now
                            time.doubleValue = now / 1e9
                            val step = (dt / BLEND_S).toFloat()
                            blend.floatValue = if (play) {
                                (blend.floatValue + step).coerceAtMost(1f)
                            } else {
                                (blend.floatValue - step).coerceAtLeast(0f)
                            }
                        }
                    }
                    if (!play && blend.floatValue == 0f) running = false
                }
            }
        }
    }
    Canvas(Modifier.at(SPEC_X.toInt(), SPEC_Y, SPEC_W, SPEC_H).graphicsLayer()) {
        val s = K * density
        // read in the draw phase: a tick redraws this layer, it does not recompose
        val t = time.doubleValue
        val b = blend.floatValue
        val colW = SPEC_PITCH * s
        for (c in 0 until SPECTRUM_COLUMNS) {
            val dim = spectrumBlendDim(t, c, b)
            if (dim > 0f) {
                drawRect(Color.Black.copy(alpha = dim), Offset(c * colW, 0f), Size(colW, size.height))
            }
        }
    }
}
