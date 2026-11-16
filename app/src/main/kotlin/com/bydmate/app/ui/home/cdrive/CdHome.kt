@file:Suppress("MagicNumber") // overlays placed on the backplate's pixel grid (tools/cdrive/LAYOUT.md)

package com.bydmate.app.ui.home.cdrive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
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
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.hybrid.AutoBadge
import com.bydmate.app.ui.home.hybrid.K
import com.bydmate.app.ui.home.hybrid.MockupFrame
import com.bydmate.app.ui.home.hybrid.SegmentBar
import com.bydmate.app.ui.home.hybrid.Txt
import com.bydmate.app.ui.home.hybrid.ValueUnit
import com.bydmate.app.ui.home.hybrid.at
import com.bydmate.app.ui.home.hybrid.celsius
import com.bydmate.app.ui.home.hybrid.groupThousands
import com.bydmate.app.ui.home.hybrid.rememberMusicVolume
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Retro home screen "C-Drive stack": the owner's Technics-style mockup as the backplate
 * (res/drawable-nodpi/cd_base, built by tools/cdrive/CdBackplate.java) with the live parts drawn
 * over it at the places listed in tools/cdrive/LAYOUT.md. Scaled and letterboxed like the RS stack.
 *
 * The keys on the left, power, the jack and the round keys under the knob are printed only; the
 * knob only shows the music volume.
 */
@Composable
fun CdHome(
    data: HomeData,
    music: NowPlaying,
    controls: MusicControls,
    tabs: List<RetroTab> = emptyList(),
    onTab: (RetroTab) -> Unit = {},
) {
    MockupFrame {
        Image(
            painterResource(R.drawable.cd_base), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
        )
        CdHeader(data)
        // Task 6: the spectrum over the printed LED matrix and the music deck (music, controls).
        CdCarData(data)
        CdVolumeMarker(rememberMusicVolume())
        // Task 7: the period keys, the trip chart and the two meters.
        CdTabKeys(tabs, onTab)
    }
}

/* ---------------------------------------------------------------- car data screen */

private val SocCyan = Color(0xFF79FFFF)
private val FuelYellow = Color(0xFFFFFD8F)
private val RangeCyan = Color(0xFFACFFFF)
private val SmallCyan = Color(0xFFB7FFFF)
private val TempCyan = Color(0xFFA2FFFF)
private val MileageCyan = Color(0xFFADFFFF)
private val SocBar = Color(0xFF11FF5F)
private val FuelBar = Color(0xFFFFD951)
private val BarRim = Color(0xFF5E928E)

/** The same values and formats as the RS stack's data screen, on this screen's places. */
@Composable
private fun CdCarData(d: HomeData) {
    Txt(d.socPercent?.let { "$it%" } ?: "—", 1232, 231, 41f, SocCyan, glow = true, width = 120)
    SegmentBar(d.socPercent, SocBar, Modifier.at(1158, 259, 192, 16), unlitRim = BarRim)
    Txt(d.fuelPercent?.let { "$it%" } ?: "—", 1488, 231, 41f, FuelYellow, glow = true, width = 120)
    SegmentBar(d.fuelPercent, FuelBar, Modifier.at(1416, 259, 190, 16), unlitRim = BarRim)

    // four digits ("1200") take a smaller face so they stay inside the cell
    val range = d.totalRangeKm?.toString() ?: "—"
    if (range.length >= 4) {
        ValueUnit(range, " км", 1679, 251, 42f, 32f, RangeCyan)
    } else {
        ValueUnit(range, " км", 1679, 251, 49f, 37f, RangeCyan)
    }
    // the EV range must stay left of the printed (i) at x 2031
    SmallKm(d.evRangeKm, 1963, 218, width = 68)
    SmallKm(d.fuelRangeKm, 1963, 260, width = 84)

    Txt(d.insideTemp.celsius(), 1243, 352, 42f, TempCyan, glow = true, width = 118)
    if (d.acOn == true && d.acAuto == true) {
        AutoBadge(1364, 354, w = 70, h = 31, textSize = 20f, rim = Color(0xFF7FD2CF), textColor = Color(0xFFBFEDEB))
    }
    Txt(d.outsideTemp.celsius(), 1610, 353, 42f, TempCyan, glow = true, width = 150)
    Txt(d.batteryTemp.celsius(), 1924, 353, 42f, TempCyan, glow = true, width = 140)

    ValueUnit(d.odometerKm?.let { groupThousands(it) } ?: "—", " км", 1268, 460, 49f, 37f, MileageCyan)
    ValueUnit(d.todayKm?.let { String.format(Locale.US, "%.1f", it) } ?: "—", " км", 1751, 460, 49f, 37f, MileageCyan)
}

/** "62 км" beside the printed bolt / pump; three digits and more take a smaller face. */
@Composable
private fun SmallKm(km: Int?, x: Int, cy: Int, width: Int) {
    val text = "${km ?: "—"} км"
    Txt(text, x, cy, if (text.length > 5) 26f else 31f, SmallCyan, glow = true, width = width)
}

/* ---------------------------------------------------------------- volume */

private const val KNOB_X = 2315
private const val KNOB_Y = 329

/** Marker angle (degrees, maths convention) at each printed mark 0…10 of the knob's scale. */
private val VolumeMarks = floatArrayOf(226.5f, 202.5f, 176f, 150f, 121.5f, 89.5f, 58f, 30f, 4f, -22.5f, -47f)

/** Marker angle for music volume 0..1: straight between the printed marks, clamped to the scale. */
internal fun cdVolumeAngle(volume: Float): Float {
    val t = volume.coerceIn(0f, 1f) * (VolumeMarks.size - 1)
    val i = t.toInt().coerceAtMost(VolumeMarks.size - 2)
    val f = t - i
    return VolumeMarks[i] + (VolumeMarks[i + 1] - VolumeMarks[i]) * f
}

/** The white marker on the printed knob, as the mockup drew it: a bar with a thin black outline. */
@Composable
private fun CdVolumeMarker(volume: Float?) {
    if (volume == null) return
    Canvas(Modifier.at(KNOB_X - 110, KNOB_Y - 110, 220, 220)) {
        val s = K * density
        val a = Math.toRadians(cdVolumeAngle(volume).toDouble()).toFloat()
        val d = Offset(cos(a), -sin(a))
        drawLine(Color.Black, center + d * (50f * s), center + d * (103f * s), 12f * s, cap = StrokeCap.Butt)
        drawLine(Color.White, center + d * (52f * s), center + d * (101f * s), 8f * s, cap = StrokeCap.Butt)
    }
}

/* ---------------------------------------------------------------- tab keys */

private class TabKey(val x: Int, val w: Int, val lampX: Int)

private const val KEY_Y = 1007
private const val KEY_H = 114
private const val LIT_Y = 1002
private const val LIT_H = 133
private const val GLYPHS_X = 882
private const val GLYPHS_Y = 1016
private const val GLYPHS_W = 1302
private const val GLYPHS_H = 99
private const val LAMP_Y = 978
private val TabIconLit = Color(0xFFD3F4A4)

/** Машина, Поездки, Зарядки, Автоматизации, Настройки — the bottom bar's order. */
private val TabKeys = listOf(
    TabKey(882, 246, 953), TabKey(1140, 254, 1219), TabKey(1406, 255, 1485),
    TabKey(1672, 260, 1751), TabKey(1944, 240, 2018),
)

/**
 * The printed tab keys open the app's tabs. A key lights up while held: the lit face, its icon
 * in pale green and its lamp turned green; the other lamps stay amber as printed.
 */
@Composable
private fun CdTabKeys(tabs: List<RetroTab>, onTab: (RetroTab) -> Unit) {
    val icons = ImageBitmap.imageResource(R.drawable.cd_tab_icons)
    val words = ImageBitmap.imageResource(R.drawable.cd_tab_words)
    tabs.take(TabKeys.size).forEachIndexed { i, tab ->
        val k = TabKeys[i]
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        if (pressed) LitKey(k, icons, words)
        Box(
            Modifier.at(k.x, KEY_Y, k.w, KEY_H)
                .semantics { contentDescription = tab.label }
                .clickable(interactionSource = source, indication = null, role = Role.Button) { onTab(tab) },
        )
    }
}

/** Draws [k] lit, in the order LAYOUT.md gives: face, tinted icon, word (both clipped to the key), lamp. */
@Composable
private fun LitKey(k: TabKey, icons: ImageBitmap, words: ImageBitmap) {
    Image(
        painterResource(R.drawable.cd_key_lit), null,
        Modifier.at(k.x - 10, LIT_Y, k.w + 20, LIT_H), contentScale = ContentScale.FillBounds,
    )
    Canvas(Modifier.at(k.x, KEY_Y, k.w, KEY_H)) {
        clipRect {
            glyphs(icons, k, ColorFilter.tint(TabIconLit, BlendMode.Modulate))
            glyphs(words, k, null)
        }
    }
    Image(
        painterResource(R.drawable.cd_led_green), null,
        Modifier.at(k.lampX, LAMP_Y, 101, 27), contentScale = ContentScale.FillBounds,
    )
}

/** One glyph layer, placed where it was cut from, in a canvas that covers key [k]. */
private fun DrawScope.glyphs(layer: ImageBitmap, k: TabKey, filter: ColorFilter?) {
    val s = K * density
    drawImage(
        layer,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(layer.width, layer.height),
        dstOffset = IntOffset(((GLYPHS_X - k.x) * s).roundToInt(), ((GLYPHS_Y - KEY_Y) * s).roundToInt()),
        dstSize = IntSize((GLYPHS_W * s).roundToInt(), (GLYPHS_H * s).roundToInt()),
        colorFilter = filter,
    )
}
