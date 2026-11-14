@file:Suppress("TooManyFunctions") // Canvas drawing of one hardware panel: many small private painters by design

package com.bydmate.app.ui.home.onkyo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HubXs = floatArrayOf(140f, 252f)
private const val HUB_Y = 100f
private val SixSlots = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f)

private val WindowRect = RoundRect(78f, 58f, 314f, 142f, CornerRadius(6f))
private val TapeInner = Color(0xFF5E4128)
private val TapeOuter = Color(0xFF2A1B10)

/**
 * Smoked cassette door, 392 x 290 dp: the cassette close-up seen through the glass. Tape packs
 * follow [progress]; the hubs turn by [hubAngle] (read at draw time, so spinning only redraws).
 */
@Composable
internal fun CassetteDoor(progress: Float, hubAngle: () -> Float, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val amcs = remember(tm) {
        tm.measure("AMCS", TextStyle(color = Color(0xFF9EA2A8), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.SansSerif))
    }
    val caption = remember(tm) {
        tm.measure(
            "2 MOTOR / BLADE BATTERY / DM-i DIRECT DRIVE",
            TextStyle(color = Ok.Light, fontSize = 10.sp, letterSpacing = 1.sp, fontFamily = FontFamily.SansSerif),
        )
    }
    Canvas(modifier.size(392.dp, 290.dp)) {
        val k = 1.dp.toPx()
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            drawDoorAndShell()
            drawWindow(progress)
            drawAmberBlock()
            val a = hubAngle()
            HubXs.forEach { drawHub(it, a) }
            drawRoundRect(Color(0xFF050506), WindowRect.topLeft(), WindowRect.size(), CornerRadius(6f), style = Stroke(2f))
            drawTapeScale()
            drawArrow()
            drawGlare()
            drawScrew(34f, 252f)
            drawScrew(358f, 252f)
        }
        drawAtBaseline(amcs, 196f * k, 210f * k)
        drawAtBaseline(caption, 196f * k, 276f * k)
    }
}

private fun RoundRect.topLeft() = Offset(left, top)
private fun RoundRect.size() = Size(width, height)

private fun DrawScope.drawDoorAndShell() {
    // smoked door
    drawRoundRect(
        Brush.verticalGradient(0f to Color(0xFF18191B), 1f to Color(0xFF0C0C0D), startY = 1f, endY = 289f),
        Offset(1f, 1f), Size(390f, 288f), CornerRadius(16f),
    )
    drawRoundRect(Ok.Edge, Offset(1f, 1f), Size(390f, 288f), CornerRadius(16f), style = Stroke(2f))
    // cassette shell and label area
    drawRoundRect(Ok.Bar, Offset(26f, 18f), Size(340f, 172f), CornerRadius(12f))
    drawRoundRect(Color(0xFF050506), Offset(26f, 18f), Size(340f, 172f), CornerRadius(12f), style = Stroke(3f))
    drawRoundRect(Color(0xFF38393D), Offset(40f, 30f), Size(312f, 148f), CornerRadius(8f))
    drawRoundRect(Color(0xFF141416), WindowRect.topLeft(), WindowRect.size(), CornerRadius(6f))
}

/** Window: faint grid and the two brown tape packs, clipped to the rounded window. */
private fun DrawScope.drawWindow(progress: Float) {
    val clip = Path().apply { addRoundRect(WindowRect) }
    clipPath(clip) {
        val grid = Color(0xFF6A6C70).copy(alpha = 0.45f)
        var x = 81f
        while (x < WindowRect.right) { drawLine(grid, Offset(x, WindowRect.top), Offset(x, WindowRect.bottom), 1f); x += 9f }
        var y = 63f
        while (y < WindowRect.bottom) { drawLine(grid, Offset(WindowRect.left, y), Offset(WindowRect.right, y), 1f); y += 9f }
        val p = progress.coerceIn(0f, 1f)
        drawTapePack(HubXs[0], 24f + 34f * (1f - p))
        drawTapePack(HubXs[1], 24f + 34f * p)
    }
}

private fun DrawScope.drawTapePack(cx: Float, r: Float) {
    val c = Offset(cx, HUB_Y)
    drawCircle(Brush.radialGradient(0f to TapeInner, 0.4f to TapeInner, 1f to TapeOuter, center = c, radius = r), r, c)
}

private fun DrawScope.drawAmberBlock() {
    drawRoundRect(
        Brush.verticalGradient(0f to Color(0xFFF6C04A), 1f to Color(0xFFC8861C), startY = 84f, endY = 116f),
        Offset(184f, 84f), Size(24f, 32f), CornerRadius(2f),
    )
    drawRoundRect(Color(0xFF8A5A10), Offset(184f, 84f), Size(24f, 32f), CornerRadius(2f), style = Stroke(1f))
}

/** Chrome hub with six dark slots and a white toothed centre; only the slots and teeth turn. */
private fun DrawScope.drawHub(cx: Float, angle: Float) {
    val c = Offset(cx, HUB_Y)
    drawCircle(
        Brush.radialGradient(
            0f to Color.White, 0.45f to Color(0xFFC7CBD1), 1f to Color(0xFF6B7077),
            center = Offset(cx - 27f + 0.38f * 54f, HUB_Y - 27f + 0.32f * 54f), radius = 40.5f,
        ),
        27f, c,
    )
    drawCircle(Color(0xFF3A3C41), 27f, c, style = Stroke(1.5f))
    rotate(angle, c) {
        SixSlots.forEach { a -> rotate(a, c) { drawRect(Ok.Bar, Offset(cx - 2f, HUB_Y - 26f), Size(4f, 10f)) } }
    }
    drawCircle(Color(0xFFF4F2EC), 14f, c)
    drawCircle(Color(0xFF9EA2A8), 14f, c, style = Stroke(1f))
    drawCircle(Color(0xFF141416), 8f, c)
    rotate(angle, c) {
        SixSlots.forEach { a -> rotate(a, c) { drawRect(Color(0xFFF4F2EC), Offset(cx - 1.5f, HUB_Y - 8.5f), Size(3f, 4f)) } }
    }
}

/** Tick scale on the label under the window. */
private fun DrawScope.drawTapeScale() {
    drawRect(Ok.Bar, Offset(130f, 148f), Size(132f, 22f))
    for (i in 0 until 25) {
        val x = 134f + i * 5.2f
        drawLine(Color(0xFFA8ABB0), Offset(x, 150f), Offset(x, if (i % 4 == 0) 166f else 158f), 1f)
    }
}

/** Outlined direction arrow under "AMCS". */
private fun DrawScope.drawArrow() {
    val p = Path().apply { moveTo(170f, 228f); lineTo(226f, 238f); lineTo(170f, 248f); close() }
    drawPath(p, Color(0xFF7E8187), style = Stroke(1.5f))
}

/** Diagonal reflection on the smoked glass. */
private fun DrawScope.drawGlare() {
    val p = Path().apply { moveTo(26f, 18f); lineTo(220f, 18f); lineTo(120f, 190f); lineTo(26f, 190f); close() }
    drawPath(
        p,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.16f), 0.4f to Color.White.copy(alpha = 0.02f), 1f to Color.Transparent,
            start = Offset(26f, 18f), end = Offset(220f, 190f),
        ),
    )
}

/** Knurled door screw with a slotted head. */
private fun DrawScope.drawScrew(cx: Float, cy: Float) {
    val c = Offset(cx, cy)
    drawCircle(Ok.Bar, 14f, c)
    drawCircle(Color(0xFF7E8187), 14f, c, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
    drawCircle(Color(0xFF45474C), 7f, c)
    rotate(-35f, c) { drawLine(Color(0xFF1E1F22), Offset(cx - 6f, cy), Offset(cx + 6f, cy), 2f) }
}
