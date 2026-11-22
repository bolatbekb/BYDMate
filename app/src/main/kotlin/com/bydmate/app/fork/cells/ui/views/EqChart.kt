package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.dayMonth
import com.bydmate.app.fork.cells.ui.levelColor
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.ui.skin.withTnum
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.ceil

// The «Выравнивание» chart: one Canvas, the spread over hours after a charge or over two weeks of parkings. fork-local.

private const val HEADROOM = 1.12
private const val GRID_ALPHA = 0.35f
private const val NORM_FILL = 0.07f
private const val FIRST_HOUR_FILL = 0.14f
private val HOUR_TICKS = (0..EQ_WINDOW_H step 3).toList()
private val DAY_TICKS = listOf(0, 3, 6, 9, 12, PARKING_DAYS)

/** The texts of the chart, resolved in composition. */
private data class EqTexts(val xTicks: List<Pair<Double, String>>, val norm: String, val service: String?, val firstHour: String, val previous: String?, val empty: String?)

internal data class EqInk(val ok: Color, val warn: Color, val bad: Color, val grid: Color, val muted: Color, val primary: Color, val secondary: Color, val line: Color, val hole: Color)

internal class EqGeom(val left: Float, val right: Float, val top: Float, val bottom: Float, val xMax: Double, val topMv: Int) {
    fun x(v: Double): Float = left + (right - left) * (v / xMax).toFloat().coerceIn(0f, 1f)
    fun y(mv: Int): Float = bottom - (bottom - top) * (mv.coerceIn(0, topMv) / topMv.toFloat())
}

/** The lines before the data is read: the measurement's own top lines, or the plateau's for the parkings. */
private fun defaultLines(e: Evaluation, mode: EqMode): Pair<Int, Int?> = when {
    mode == EqMode.PARKINGS -> ZoneRow.PLATEAU.normMv to ZoneRow.PLATEAU.serviceMv
    e.row == ZoneRow.TOP -> e.limits.normMv to e.limits.serviceMv
    else -> ZoneRow.TOP.normMv to ZoneRow.TOP.serviceMv
}

/** [model] null while the data is read (or when it failed): the frame and the lines only, no «nothing to draw». */
@Composable
internal fun EqChart(e: Evaluation, mode: EqMode, model: EqualizationModel?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.withTnum()
    val (norm, svc) = model?.let { it.normMv to it.serviceMv } ?: defaultLines(e, mode)
    val charge = mode == EqMode.AFTER_CHARGE
    val ticks = if (charge) {
        HOUR_TICKS.map { it.toDouble() to TextRes(R.string.fork_cells_dur_h, it).text() }
    } else {
        DAY_TICKS.map { it.toDouble() to dayMonth(e.input.ts - (PARKING_DAYS - it) * DAY_MS) }
    }
    val texts = EqTexts(
        ticks,
        TextRes(R.string.fork_cells_eq_norm, norm).text(),
        svc?.let { TextRes(R.string.fork_cells_eq_service, it).text() },
        stringResource(R.string.fork_cells_eq_first_hour),
        model?.previous?.lastOrNull()?.let { TextRes(R.string.fork_cells_eq_previous, it.mv).text() },
        model?.takeIf { it.points.isEmpty() }?.let { stringResource(if (charge) R.string.fork_cells_eq_empty_charge else R.string.fork_cells_eq_empty_parkings) },
    )
    val ink = EqInk(ConsumptionGood, AccentOrange, SocRed, CardBorder, TextMuted, TextPrimary, TextSecondary, levelColor(e.level), CardSurfaceElevated)
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val pts = model?.points.orEmpty()
        val prev = model?.previous.orEmpty()
        val peak = ((pts + prev).maxOfOrNull { it.mv } ?: 0).coerceAtLeast(norm)
        val xMax = model?.xMax ?: if (charge) EQ_WINDOW_H.toDouble() else PARKING_DAYS.toDouble()
        val g = EqGeom(40.dp.toPx(), size.width - 14.dp.toPx(), 18.dp.toPx(), size.height - 22.dp.toPx(), xMax, niceTop(peak))
        if (g.bottom <= g.top || g.right <= g.left) return@Canvas
        val l = Labels(measurer, base)
        drawFrame(g, texts, ink, l)
        drawLines(g, norm, svc, texts, ink, l)
        if (charge && model != null && !model.awakeAxis) drawFirstHour(g, texts.firstHour, ink, l)
        drawPrevious(g, prev, texts.previous, ink, l)
        drawCurve(g, pts, charge, norm, ink, l)
        texts.empty?.let {
            val t = l.centered(it, 14f, ink.secondary, ((g.right - g.left) * 0.8f).toInt())
            drawText(t, topLeft = Offset((g.left + g.right) / 2f - t.size.width / 2f, (g.top + g.bottom) / 2f - t.size.height / 2f))
        }
    }
}

/** A round axis top above [peak] × 1.12: steps of 10, 50 or 100 mV. */
private fun niceTop(peak: Int): Int {
    val p = peak.coerceAtLeast(10) * HEADROOM
    val step = when {
        p > 150 -> 100
        p > 60 -> 50
        else -> 10
    }
    return (ceil(p / step) * step).toInt()
}

/** Faint grid lines with their mV on the left, the hours or dates under the plot. */
private fun DrawScope.drawFrame(g: EqGeom, texts: EqTexts, ink: EqInk, l: Labels) {
    val step = when {
        g.topMv > 300 -> 100
        g.topMv > 60 -> 50
        else -> 10
    }
    val marks = (0..g.topMv step step).toList().let { m -> if (m.size > 6) m.filterIndexed { i, _ -> i % 2 == 0 } else m }
    for (v in marks) {
        drawLine(ink.grid.copy(alpha = GRID_ALPHA), Offset(g.left, g.y(v)), Offset(g.right, g.y(v)), 1.dp.toPx())
        val t = l.measure("$v", 11f, ink.muted)
        drawText(t, topLeft = Offset(g.left - t.size.width - 7.dp.toPx(), g.y(v) - t.size.height / 2f))
    }
    texts.xTicks.forEachIndexed { i, (x, label) ->
        val t = l.measure(label, 11f, ink.muted)
        val left = when (i) {
            0 -> g.x(x)
            texts.xTicks.lastIndex -> g.x(x) - t.size.width
            else -> g.x(x) - t.size.width / 2f
        }
        drawText(t, topLeft = Offset(left, g.bottom + 5.dp.toPx()))
    }
}

/** The norm (a soft green floor and a dashed line) and the service number (dashed red) when it is on the scale. */
@Suppress("LongParameterList") // why: the two numbers and their labels, all needed at the call
private fun DrawScope.drawLines(g: EqGeom, norm: Int, svc: Int?, texts: EqTexts, ink: EqInk, l: Labels) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
    val ny = g.y(norm)
    drawRect(ink.ok.copy(alpha = NORM_FILL), Offset(g.left, ny), Size(g.right - g.left, g.bottom - ny))
    drawLine(ink.ok, Offset(g.left, ny), Offset(g.right, ny), 1.2.dp.toPx(), pathEffect = dash)
    val nt = l.measure(texts.norm, 11.5f, ink.ok)
    drawText(nt, topLeft = Offset(g.right - nt.size.width, ny - nt.size.height - 2.dp.toPx()))
    if (svc == null || svc > g.topMv) return
    val sy = g.y(svc)
    drawLine(ink.bad, Offset(g.left, sy), Offset(g.right, sy), 1.2.dp.toPx(), pathEffect = dash)
    texts.service?.let {
        val st = l.measure(it, 11.5f, ink.bad)
        drawText(st, topLeft = Offset(g.right - st.size.width, sy - st.size.height - 2.dp.toPx()))
    }
}

/** The first hour after the charge end: grey, «< 1 ч — без оценки». */
private fun DrawScope.drawFirstHour(g: EqGeom, label: String, ink: EqInk, l: Labels) {
    val x1 = g.x(1.0)
    drawRect(ink.muted.copy(alpha = FIRST_HOUR_FILL), Offset(g.left, g.top), Size(x1 - g.left, g.bottom - g.top))
    val t = l.centered(label, 10.5f, ink.secondary, (88.dp.toPx()).toInt())
    drawText(t, topLeft = Offset(((g.left + x1) / 2f - t.size.width / 2f).coerceAtLeast(g.left), g.bottom - t.size.height - 6.dp.toPx()))
}

/** The previous full charge: dashed grey with small dots and its last value. */
private fun DrawScope.drawPrevious(g: EqGeom, prev: List<EqPoint>, label: String?, ink: EqInk, l: Labels) {
    if (prev.isEmpty()) return
    if (prev.size > 1) {
        drawPath(
            polyline(g, prev),
            ink.muted,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
    }
    prev.forEach { drawCircle(ink.muted, 2.5.dp.toPx(), Offset(g.x(it.x), g.y(it.mv))) }
    val last = prev.last()
    label?.let {
        val t = l.measure(it, 11f, ink.muted)
        val at = Offset(g.x(last.x), g.y(last.mv))
        val x = (at.x - t.size.width / 2f).coerceIn(g.left, g.right - t.size.width)
        val y = (at.y - t.size.height - 8.dp.toPx()).coerceAtLeast(g.top)
        drawText(t, topLeft = Offset(x, y))
    }
}

internal fun polyline(g: EqGeom, pts: List<EqPoint>): Path = Path().apply {
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(g.x(p.x), g.y(p.mv)) else lineTo(g.x(p.x), g.y(p.mv)) }
}
