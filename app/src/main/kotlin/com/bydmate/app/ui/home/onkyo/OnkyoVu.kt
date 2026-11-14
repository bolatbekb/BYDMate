package com.bydmate.app.ui.home.onkyo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val VuGreen = Color(0xFF7CFF9A)
private val VuBg = Brush.verticalGradient(
    0f to Color(0xFF0E2A15), 0.62f to Color(0xFF134E24), 0.63f to Color(0xFF1E7A38), 1f to Color(0xFF156A2E),
)

// The mockup's SVG geometry: a 480 x 300 viewBox, pivot at (240, 290), scale from 138° to 42°.
private const val VB_W = 480f
private const val VB_H = 300f
private const val VU_CX = 240f
private const val VU_CY = 290f
private const val A0 = 138f
private const val A1 = 42f

private fun rad(deg: Float) = deg * PI.toFloat() / 180f
private fun angleAt(f: Float) = rad(A0 + (A1 - A0) * f)

/**
 * Where [value] sits on a scale whose [marks] are equally spaced but not equally valued, the
 * way real VU faces crowd the top end. 0..1; 0 when unknown.
 */
private fun fractionOn(marks: List<Int>, value: Int?): Float {
    val v = value?.toFloat() ?: return 0f
    if (v <= marks.first()) return 0f
    if (v >= marks.last()) return 1f
    val seg = marks.size - 1
    for (i in 0 until seg) {
        val a = marks[i].toFloat(); val b = marks[i + 1].toFloat()
        if (v <= b) return (i + (v - a) / (b - a)) / seg
    }
    return 1f
}

/**
 * One big green-lit meter: dense scale, numbers on [marks], a white needle at [value].
 * [sway] (degrees, read at draw time) wobbles the needle while music plays.
 */
@Composable
internal fun VuMeter(
    title: String,
    unit: String,
    marks: List<Int>,
    value: Int?,
    valueText: String,
    sway: () -> Float,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer(cacheSize = 24)
    val frac = fractionOn(marks, value)
    Box(modifier.background(VuBg)) {
        Canvas(Modifier.fillMaxSize()) { drawVuFace(tm, title, unit, marks) }
        Canvas(Modifier.fillMaxSize()) {
            val (s, o) = fit()
            val a = angleAt(frac) - rad(sway())
            drawLine(
                Color(0xFFE6F2E8),
                o + Offset(VU_CX, VU_CY) * s,
                o + Offset(VU_CX + 214f * cos(a), VU_CY - 214f * sin(a)) * s,
                2f * s,
            )
        }
        Label(
            valueText, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
            size = 20.sp, color = Color(0xFFCFFFD9), weight = FontWeight.ExtraBold, spacing = 0.sp,
        )
    }
}

/** Scale factor and offset that fit the viewBox inside the canvas ("xMidYMid meet"). */
private fun DrawScope.fit(): Pair<Float, Offset> {
    val s = min(size.width / VB_W, size.height / VB_H)
    return s to Offset((size.width - VB_W * s) / 2f, (size.height - VB_H * s) / 2f)
}

private fun DrawScope.drawVuFace(tm: TextMeasurer, title: String, unit: String, marks: List<Int>) {
    val (s, o) = fit()
    fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
    fun style(units: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
        color = VuGreen, fontSize = (units * s / density).sp, fontWeight = weight,
        letterSpacing = if (spacing > 0f) (spacing / units).em else 0.em, fontFamily = FontFamily.SansSerif,
    )
    fun text(t: String, x: Float, y: Float, st: TextStyle, anchor: Float = 0.5f) {
        val at = p(x, y)
        drawAtBaseline(tm.measure(t, st), at.x, at.y, anchor)
    }

    text(unit, 240f, 40f, style(16f, FontWeight.Bold, 1f))
    for (i in 0 until 40) {
        val a = angleAt(i / 39f)
        val big = i % 6 == 0
        val len = if (big) 30f else if (i % 2 == 1) 12f else 20f
        drawLine(
            VuGreen,
            p(VU_CX + 190f * cos(a), VU_CY - 190f * sin(a)),
            p(VU_CX + (190f + len) * cos(a), VU_CY - (190f + len) * sin(a)),
            (if (big) 3f else 2f) * s,
        )
    }
    val numStyle = style(15f, FontWeight.Bold)
    marks.forEachIndexed { i, m ->
        val a = angleAt(i / (marks.size - 1f))
        text(m.toString(), VU_CX + 236f * cos(a), VU_CY - 236f * sin(a) + 5f, numStyle)
    }
    val pctStyle = style(11f, FontWeight.Normal)
    listOf("0", "25", "50", "75", "100%").forEachIndexed { i, l ->
        val a = angleAt(i / 4f)
        text(l, VU_CX + 172f * cos(a), VU_CY - 172f * sin(a) + 4f, pctStyle)
    }
    text(title, 240f, 232f, style(15f, FontWeight.Bold, 2f))
    text("BYDMATE", 30f, 226f, style(11f, FontWeight.ExtraBold), anchor = 0f)
}
