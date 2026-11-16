@file:Suppress("MagicNumber", "TooManyFunctions") // one panel: small overlays placed on the mockup's pixel grid

package com.bydmate.app.ui.home.hybrid

import android.content.Context
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.ReelCassette
import com.bydmate.app.ui.home.ReelCassetteRotation
import com.bydmate.app.ui.home.ReelWindow
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.labelLine
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Retro home screen "RS stack": the owner's mockup of a black hi-fi control centre, used as the
 * backplate, with the live parts drawn over it — the track, the turning reels, the car's
 * numbers, the two meter needles, the volume marker and the lit mode and play keys. Scaled
 * uniformly to fit, letterboxed in black.
 *
 * The keys on the left, POWER, the jack and the round keys under the knob are printed only; the
 * ECO / NORMAL / SPORT keys and the knob only show the car's mode and the music volume.
 */
@Composable
fun HybridHome(
    data: HomeData,
    music: NowPlaying,
    controls: MusicControls,
    tabs: List<RetroTab> = emptyList(),
    onTab: (RetroTab) -> Unit = {},
) {
    MockupFrame {
        Image(
            painterResource(R.drawable.rs_base), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
        )
        ModeKeys(data.driveProfile)
        Deck(music, controls)
        CarData(data)
        VolumeMarker(rememberMusicVolume())
        Meters(data)
        TabKeys(tabs, onTab)
    }
}

/* ---------------------------------------------------------------- header */

private data class KeyRect(val x: Int, val y: Int, val w: Int, val h: Int)

private val ModeRects = mapOf(
    "ECO" to KeyRect(1865, 14, 230, 72),
    "NORMAL" to KeyRect(2110, 14, 210, 72),
    "SPORT" to KeyRect(2333, 14, 195, 72),
)

/** The three printed mode keys get their words here; the current mode's key is lit amber. */
@Composable
private fun ModeKeys(current: String?) {
    ModeRects.forEach { (mode, r) ->
        val on = mode == current
        if (on) {
            // the lit face was cut with a 10 px margin of glow round the key
            Image(
                painterResource(R.drawable.rs_lit_mode), null,
                Modifier.at(r.x - 10, r.y - 10, r.w + 21, r.h + 21), contentScale = ContentScale.FillBounds,
            )
            if (mode == "ECO") {
                Image(painterResource(R.drawable.rs_leaf), null, Modifier.at(1895, 22, 71, 57), contentScale = ContentScale.FillBounds)
            }
        }
        val cx = if (mode == "ECO") 2020 else r.x + r.w / 2
        Txt(mode, cx, r.y + r.h / 2, 31f, if (on) Color(0xFFFFF4DC) else Rs.White, glow = on, centre = true, width = r.w)
    }
}

/* ---------------------------------------------------------------- deck */

@Composable
private fun Deck(music: NowPlaying, controls: MusicControls) {
    Txt(music.labelLine().replace(" — ", " – "), 420, 209, 35f, Rs.Cyan, glow = true, width = 500)
    Txt(music.clockText(), 1027, 209, 35f, Rs.Cyan, glow = true, right = true, width = 140)
    TrackBar(if (music.hasTrack) music.progress else 0f, Modifier.at(420, 240, 611, 17))
    val spin = rememberSpin(music.isPlaying || music.winding, if (music.winding) 900f else 150f)
    Cassette(ReelCassetteRotation.current, music.progress) { spin.value }
    TransportKeys(music, controls)
}

/** "03:24" from the play position, "--:--" with nothing playing. */
private fun NowPlaying.clockText(): String {
    val ms = positionMs ?: return "--:--"
    val s = (ms / 1000).toInt()
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

/** The mockup's progress strip: cyan blocks along a dark teal track, lit up to [progress]. */
@Composable
private fun TrackBar(progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        drawRoundRect(Rs.CyanTrack, cornerRadius = CornerRadius(size.height / 2))
        val pitch = 26.5f * K * density
        val w = 22f * K * density
        val n = (size.width / pitch).toInt()
        val lit = (progress.coerceIn(0f, 1f) * n).roundToInt()
        for (i in 0 until lit) {
            val x = i * pitch
            drawRect(Rs.Cyan.copy(alpha = 0.3f), Offset(x - 2, -2f), Size(w + 4, size.height + 4))
            drawRect(Rs.Cyan, Offset(x, 1f), Size(w, size.height - 2))
        }
    }
}

/** Reel centres in the printed tape window. */
private const val REEL_L_X = 536
private const val REEL_R_X = 829
private const val REEL_Y = 343
private const val REEL_SIZE = 84

/** Photo hubs turn inside the printed reels; a drawn cassette covers the whole window instead. */
@Composable
private fun Cassette(cassette: ReelCassette, progress: Float, angle: () -> Float) {
    val left = cassette.reelLeft
    val right = cassette.reelRight
    if (left != null && right != null) {
        PhotoReel(left, REEL_L_X, angle)
        PhotoReel(right, REEL_R_X, angle)
    } else {
        ReelWindow(progress, angle, Modifier.at(381, 276, 527, 128), cassette)
    }
}

/**
 * One photographed hub (black, with its spokes) turning inside the printed chrome flange, which
 * stays still with its light and clamps; only this small canvas redraws while it spins.
 */
@Composable
private fun PhotoReel(@DrawableRes res: Int, cx: Int, angle: () -> Float) {
    val bmp: ImageBitmap = ImageBitmap.imageResource(res)
    Canvas(Modifier.at(cx - REEL_SIZE / 2, REEL_Y - REEL_SIZE / 2, REEL_SIZE, REEL_SIZE)) {
        // played side A: both reels turn anticlockwise
        rotate(-angle(), center) {
            drawImage(
                bmp, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** A clock in degrees for the reels, advancing at [degPerSec] while [running]. */
@Composable
private fun rememberSpin(running: Boolean, degPerSec: Float): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, degPerSec) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                t.floatValue = (t.floatValue + (now - last) / 1e9f * degPerSec) % 360f
                last = now
            }
        }
    }
    return t
}

private val TransportX = listOf(298, 432, 563, 695, 828, 962)

@Composable
private fun TransportKeys(music: NowPlaying, controls: MusicControls) {
    val playing = music.isPlaying
    if (playing) {
        Image(painterResource(R.drawable.rs_lit_play), null, Modifier.at(692, 442, 131, 77), contentScale = ContentScale.FillBounds)
    }
    Canvas(Modifier.at(740, 462, 36, 36)) {
        val p = Path().apply {
            moveTo(size.width * 0.12f, 0f); lineTo(size.width * 0.95f, size.height / 2); lineTo(size.width * 0.12f, size.height); close()
        }
        if (playing) drawPath(p, Color(0xFF2CF060).copy(alpha = 0.35f), style = Stroke(6f))
        drawPath(p, if (playing) Color(0xFF39F26A) else Rs.Cream)
    }
    val actions = listOf(
        "Назад" to controls::previous, "Стоп" to controls::stop, "Пауза" to controls::pause,
        "Играть" to controls::play, "Вперёд" to controls::next, "Извлечь" to controls::stop,
    )
    actions.forEachIndexed { i, (name, action) ->
        PressArea(Modifier.at(TransportX[i], 448, if (i == 5) 104 else 122, 66), name, action)
    }
}

/** An invisible touch area over a printed key: darker while held, or lit green for a tab key. */
@Composable
private fun PressArea(modifier: Modifier, name: String, onClick: () -> Unit, litOnPress: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .semantics { contentDescription = name }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
    ) {
        if (pressed) {
            Canvas(Modifier.fillMaxSize()) {
                val r = CornerRadius(6f * K * density)
                if (litOnPress) {
                    drawRoundRect(Rs.GreenLed.copy(alpha = 0.22f), cornerRadius = r)
                    drawRoundRect(Rs.GreenLed.copy(alpha = 0.8f), cornerRadius = r, style = Stroke(3f * K * density))
                } else {
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = r)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- car data screen */

@Composable
private fun CarData(d: HomeData) {
    Txt(d.socPercent?.let { "$it%" } ?: "—", 1225, 207, 48f, Rs.Cyan, glow = true)
    SegmentBar(d.socPercent, Rs.GreenLed, Modifier.at(1146, 237, 216, 22))
    Txt(d.fuelPercent?.let { "$it%" } ?: "—", 1508, 207, 48f, Rs.White, glow = true)
    SegmentBar(d.fuelPercent, Rs.Yellow, Modifier.at(1433, 237, 200, 22))
    // four digits ("1200") take a smaller face so they stay clear of the printed slash
    val range = d.totalRangeKm?.toString() ?: "—"
    if (range.length >= 4) ValueUnit(range, " км", 1705, 233, 54f, 34f) else ValueUnit(range, " км", 1705, 233, 64f, 38f)
    Txt("${d.evRangeKm ?: "—"} км", 2012, 204, 29f, Rs.White)
    Txt("${d.fuelRangeKm ?: "—"} км", 2012, 245, 29f, Rs.White)

    Txt(d.insideTemp.celsius(), 1245, 347, 48f, Rs.Cyan, glow = true, width = 180)
    if (d.acOn == true && d.acAuto == true) AutoBadge(1382, 349)
    Txt(d.outsideTemp.celsius(), 1642, 347, 48f, Rs.Cyan, glow = true)
    Txt(d.batteryTemp.celsius(), 1983, 347, 48f, Rs.Cyan, glow = true)

    ValueUnit(d.odometerKm?.let { groupThousands(it) } ?: "—", " км", 1290, 475, 52f, 38f)
    ValueUnit(d.todayKm?.let { String.format(Locale.US, "%.1f", it) } ?: "—", " км", 1770, 475, 52f, 38f)
}

/** "22°C", "−3°C" (a true minus sign) or "—". */
internal fun Int?.celsius(): String = when {
    this == null -> "—"
    this < 0 -> "−${-this}°C"
    else -> "$this°C"
}

/** "73 482" with a thin space between thousands. */
internal fun groupThousands(n: Int): String = String.format(Locale.US, "%,d", n).replace(',', ' ')

/* ---------------------------------------------------------------- volume */

/** Music volume 0..1, read from the system once a second; null when unavailable. */
@Composable
internal fun rememberMusicVolume(): Float? {
    val context = LocalContext.current
    val v = remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@LaunchedEffect
        while (true) {
            v.value = runCatching {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else null
            }.getOrNull()
            delay(1000)
        }
    }
    return v.value
}

private const val KNOB_X = 2367
private const val KNOB_Y = 306

/** The orange marker on the printed knob, turned to [volume]: 0 lower left, 1 lower right. */
@Composable
private fun VolumeMarker(volume: Float?) {
    if (volume == null) return
    Canvas(Modifier.at(KNOB_X - 110, KNOB_Y - 110, 220, 220)) {
        val s = K * density
        val c = center
        val a = Math.toRadians(225.0 - 270.0 * volume.coerceIn(0f, 1f)).toFloat()
        val d = Offset(cos(a), -sin(a))
        drawLine(Rs.Amber.copy(alpha = 0.3f), c + d * (46f * s), c + d * (100f * s), 14f * s)
        drawLine(Color(0xFFFF7A10), c + d * (46f * s), c + d * (100f * s), 6f * s)
        drawLine(Color(0xFFFFD08A), c + d * (50f * s), c + d * (98f * s), 2f * s)
    }
}

/* ---------------------------------------------------------------- meters */

/** The meter screen on the mockup: needles are clipped to it. */
private val MeterScreen = KeyRect(375, 596, 2142, 330)

@Composable
private fun Meters(d: HomeData) {
    val m = MeterScreen
    val rpm = d.engineRpm?.let { it / 1000.0 }
    MeterNeedle(RpmScale, rpm, m.x, m.y, Modifier.at(m.x, m.y, 878, m.h))
    Txt(rpm?.let { String.format(Locale.US, "%.2f", it) } ?: "—", 868, 830, 76f, Rs.Green, glow = true, centre = true, width = 300)

    CompressorLabels.forEachIndexed { i, (x, y) ->
        Txt(String.format(Locale.US, "%.1f", i * 0.5), x, y, 34f, Rs.White, centre = true, width = 90)
    }
    MeterNeedle(CompressorScale, d.compressorKw, 1512, m.y, Modifier.at(1512, m.y, 1005, m.h))
    ValueUnit(d.compressorKw?.let { String.format(Locale.US, "%.1f", it) } ?: "—", " kW", 1930, 860, 76f, 52f, Rs.Green)

    val engineOn = d.engineRpm?.let { it > 0 }
    val acOn = d.acOn
    Txt(statusText(engineOn, "Работает", "Остановлен"), 1380, 728, 31f, if (engineOn == true) Color(0xFF39E08A) else Rs.Dim, centre = true, width = 240)
    Txt(statusText(acOn, "Активен", "Выключен"), 1382, 887, 31f, if (acOn == true) Rs.Lime else Rs.Dim, centre = true, width = 240)
    // printed lamps go dark when their unit is off
    if (engineOn != true) Box(Modifier.at(1334, 614, 84, 62).background(Rs.Screen.copy(alpha = 0.72f)))
    if (acOn != true) Box(Modifier.at(1348, 774, 70, 70).background(Rs.Screen.copy(alpha = 0.72f)))
}

private fun statusText(on: Boolean?, yes: String, no: String): String = when (on) {
    true -> yes
    false -> no
    null -> "—"
}

/* ---------------------------------------------------------------- tab keys */

private val TabRects = listOf(
    KeyRect(873, 1020, 250, 135), KeyRect(1140, 1020, 245, 135), KeyRect(1403, 1020, 247, 135),
    KeyRect(1668, 1020, 255, 135), KeyRect(1940, 1020, 240, 135),
)

/** The printed tab keys open the app's tabs in the order the bottom bar has them. */
@Composable
private fun TabKeys(tabs: List<RetroTab>, onTab: (RetroTab) -> Unit) {
    tabs.take(TabRects.size).forEachIndexed { i, tab ->
        val r = TabRects[i]
        PressArea(Modifier.at(r.x, r.y, r.w, r.h), tab.label, { onTab(tab) }, litOnPress = true)
    }
}
