package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.ui.skin.withTnum
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.abs

// The vertical ruler of «Линейка»: one Canvas, the zone's norm / watch / service bands and an arrow at the spread. fork-local.

private const val BAND_ALPHA = 0.82f

private class RulerInk(val ok: Color, val warn: Color, val bad: Color, val primary: Color, val secondary: Color, val muted: Color)

private class RulerWords(val norm: String, val watch: String, val service: String, val unit: String)

@Composable
internal fun RulerScale(model: RulerModel, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.withTnum()
    val words = RulerWords(
        stringResource(R.string.fork_cells_ruler_norm),
        stringResource(R.string.fork_cells_ruler_watch),
        stringResource(R.string.fork_cells_ruler_service),
        stringResource(R.string.fork_cells_ruler_mv),
    )
    val ink = RulerInk(ConsumptionGood, AccentOrange, SocRed, TextPrimary, TextSecondary, TextMuted)
    Canvas(modifier) {
        if (size.height <= 0f || size.width <= 0f) return@Canvas
        drawRuler(model, words, ink, Labels(measurer, base))
    }
}

/** The bar sits right of the middle: the value and the marks on its left, the band names on its right. */
private fun DrawScope.drawRuler(m: RulerModel, words: RulerWords, ink: RulerInk, l: Labels) {
    val top = 10.dp.toPx()
    val bottom = size.height - 10.dp.toPx()
    val barW = 26.dp.toPx()
    val barX = size.width * 0.42f
    fun y(v: Int): Float = bottom - (bottom - top) * (v.coerceIn(0, m.topMv) / m.topMv.toFloat())
    val svc = m.serviceMv
    val upper = svc ?: m.topMv
    clipPath(Path().apply { addRoundRect(RoundRect(barX, top, barX + barW, bottom, CornerRadius(5.dp.toPx()))) }) {
        drawRect(ink.ok.copy(alpha = BAND_ALPHA), Offset(barX, y(m.normMv)), Size(barW, bottom - y(m.normMv)))
        drawRect(ink.warn.copy(alpha = BAND_ALPHA), Offset(barX, y(upper)), Size(barW, y(m.normMv) - y(upper)))
        if (svc != null) drawRect(ink.bad.copy(alpha = BAND_ALPHA), Offset(barX, top), Size(barW, y(svc) - top))
    }
    val gap = 10.dp.toPx()
    fun name(text: String, color: Color, from: Float, to: Float) {
        val t = l.measure(text, 12.5f, color, bold = true)
        if (to - from >= t.size.height) drawText(t, topLeft = Offset(barX + barW + gap, (from + to) / 2f - t.size.height / 2f))
    }
    name(words.norm, ink.ok, y(m.normMv), bottom)
    name(words.watch, ink.warn, y(upper), y(m.normMv))
    if (svc != null) name(words.service, ink.bad, top, y(svc))
    val vy = y(m.valueMv)
    for (v in listOfNotNull(0, m.normMv, svc)) {
        if (abs(y(v) - vy) < 22.dp.toPx()) continue
        val t = l.measure("$v", 11.5f, ink.muted)
        drawText(t, topLeft = Offset(barX - 8.dp.toPx() - t.size.width, y(v) - t.size.height / 2f))
    }
    drawValue(barX, barW, vy, m.valueMv, words.unit, ink, l)
}

/** A line across the bar, a triangle pointing at it, the number and «мВ» left of the triangle. */
@Suppress("LongParameterList") // why: the geometry of one arrow, all needed at the call
private fun DrawScope.drawValue(barX: Float, barW: Float, vy: Float, value: Int, unit: String, ink: RulerInk, l: Labels) {
    drawLine(ink.primary, Offset(barX - 3.dp.toPx(), vy), Offset(barX + barW + 3.dp.toPx(), vy), 2.dp.toPx())
    val tip = barX - 3.dp.toPx()
    val back = tip - 12.dp.toPx()
    drawPath(Path().apply { moveTo(tip, vy); lineTo(back, vy - 7.dp.toPx()); lineTo(back, vy + 7.dp.toPx()); close() }, ink.primary)
    val num = l.measure("$value", 17f, ink.primary, bold = true)
    val u = l.measure(unit, 10.5f, ink.secondary)
    val right = back - 6.dp.toPx()
    val h = (num.size.height + u.size.height).toFloat()
    val y0 = (vy - num.size.height * 0.7f).within(0f, size.height - h)
    drawText(num, topLeft = Offset(right - num.size.width, y0))
    drawText(u, topLeft = Offset(right - u.size.width, y0 + num.size.height - 2.dp.toPx()))
}
