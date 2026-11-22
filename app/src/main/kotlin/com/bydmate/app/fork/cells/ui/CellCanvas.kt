package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.SpreadPoint
import com.bydmate.app.ui.skin.withTnum
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * The cell pictures of the battery check, shared by the window's views: each is one Canvas, drawn
 * once per change, no appearance animation. Colours come from the active skin. fork-local.
 */
object CellCanvas {
    /** One bar per cell around the median line; dashed lines at ± half the zone's norm; colour only off normal. */
    @Composable
    fun DeviationStrip(e: Evaluation, modifier: Modifier) {
        val colors = ChartColors(TextMuted, ConsumptionGood, CardBorder, TextPrimary)
        val notice = AccentOrange
        val strong = SocRed
        // the skin's colours are keys: a skin switch with the window open repaints
        val cls = remember(e, colors.bar, notice, strong) { e.classes.map { cellColor(it, e.level) } }
        Canvas(modifier) { drawStrip(e, cls, colors) }
    }

    /** 184 tiles in [columns] columns: number in each, deviation on the off-normal ones, which alone take a colour. */
    @Composable
    fun CellGrid(e: Evaluation, columns: Int, modifier: Modifier) {
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val base = LocalTextStyle.current
        val muted = TextMuted
        val dark = NavyDark
        val notice = AccentOrange
        val strong = SocRed
        val tile = CardBorder.copy(alpha = TILE_ALPHA)
        val labels = remember(e, measurer, base, muted, dark) {
            val num = base.merge(TextStyle(fontSize = 10.sp)).withTnum()
            val dev = base.merge(TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = dark)).withTnum()
            List(e.classes.size) { i ->
                val off = e.classes[i] != CellClass.NORMAL
                TileText(
                    measurer.measure("${i + 1}", num.copy(color = if (off) dark else muted)),
                    if (off) measurer.measure(signedMv(e.deviationsMv[i]), dev) else null,
                )
            }
        }
        val fills = remember(e, tile, notice, strong) { e.classes.map { if (it == CellClass.NORMAL) tile else cellColor(it, e.level) } }
        Canvas(modifier) { drawGrid(fills, labels, max(1, columns)) }
    }

    /**
     * Spread per rest period, oldest left: the line, a dashed norm per point's own zone, dots by the evaluated level from
     * [levels] (by id; without one, only the norm band, never red); hollow = preliminary.
     */
    @Composable
    fun SpreadHistory(points: List<SpreadPoint>, selectedId: Long?, levels: Map<Long, Level>, modifier: Modifier) {
        val measurer = rememberTextMeasurer()
        val colors = ChartColors(TextMuted, ConsumptionGood, CardBorder, TextPrimary)
        val watch = AccentOrange
        val bad = SocRed
        val dots = remember(points, levels, colors.norm, watch, bad) { points.map { levelColor(pointLevel(it, levels[it.id])) } }
        val hole = CardSurfaceElevated
        val label = LocalTextStyle.current.merge(TextStyle(fontSize = 11.sp, color = TextMuted)).withTnum()
        Canvas(modifier) { drawHistory(HistoryInput(points, selectedId, dots, hole), colors, measurer, label) }
    }
}

private class ChartColors(val bar: Color, val norm: Color, val axis: Color, val strong: Color)

private class TileText(val num: TextLayoutResult, val dev: TextLayoutResult?)

private class HistoryInput(val points: List<SpreadPoint>, val selectedId: Long?, val dots: List<Color>, val hole: Color)

private const val TILE_ALPHA = 0.55f
private const val NORM_ALPHA = 0.7f
private const val LINE_ALPHA = 0.6f
private const val HISTORY_HEADROOM = 1.18

private fun DrawScope.drawStrip(e: Evaluation, cls: List<Color>, c: ChartColors) {
    val n = e.deviationsMv.size
    if (n == 0 || size.width <= 0f) return
    val mx = max(max(e.limits.normMv.toDouble(), e.deviationsMv.maxOf { abs(it) }), 1.0)
    val mid = size.height / 2f
    val usable = mid - 4.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    drawLine(c.axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
    for (sign in intArrayOf(1, -1)) {
        val y = mid - (sign * e.limits.normMv / 2.0 / mx * usable).toFloat()
        drawLine(c.norm.copy(alpha = NORM_ALPHA), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
    }
    val step = size.width / n
    val gap = if (step > 3.dp.toPx()) 1.dp.toPx() else 0f
    for (i in 0 until n) {
        val d = e.deviationsMv[i]
        val h = max(1.5.dp.toPx(), (abs(d) / mx * usable).toFloat())
        drawRect(cls.getOrElse(i) { c.bar }, Offset(i * step + gap / 2f, if (d > 0) mid - h else mid), Size(max(1f, step - gap), h))
    }
}

private fun DrawScope.drawGrid(fills: List<Color>, labels: List<TileText>, columns: Int) {
    val n = fills.size
    if (n == 0 || size.width <= 0f) return
    val rows = ceil(n / columns.toDouble()).toInt()
    val gap = 3.dp.toPx()
    val w = (size.width - gap * (columns - 1)) / columns
    val h = (size.height - gap * (rows - 1)) / rows
    if (w <= 0f || h <= 0f) return
    val r = CornerRadius(4.dp.toPx())
    for (i in 0 until n) {
        val tile = Rect(Offset((i % columns) * (w + gap), (i / columns) * (h + gap)), Size(w, h))
        drawRoundRect(fills[i], tile.topLeft, tile.size, r)
        labels.getOrNull(i)?.let { drawTileText(it, tile) }
    }
}

/** The number at the top left when it fits; the deviation at the bottom when both fit. */
private fun DrawScope.drawTileText(t: TileText, tile: Rect) {
    val pad = 4.dp.toPx()
    if (t.num.size.height + pad > tile.height) return
    drawText(t.num, topLeft = Offset(tile.left + pad, tile.top + pad / 2f))
    val dev = t.dev ?: return
    if (dev.size.width <= tile.width && t.num.size.height + dev.size.height <= tile.height) {
        drawText(dev, topLeft = Offset(tile.left + (tile.width - dev.size.width) / 2f, tile.bottom - dev.size.height - pad / 2f))
    }
}

private class HistoryGeom(val left: Float, val top: Float, val bottom: Float, val step: Float, val scaleTop: Int) {
    fun x(i: Int): Float = left + step * (i + 0.5f)
    fun y(v: Int): Float = top + (bottom - top) * (1f - v / scaleTop.toFloat())
}

private fun DrawScope.drawHistory(input: HistoryInput, c: ChartColors, measurer: TextMeasurer, label: TextStyle) {
    val pts = input.points
    if (pts.isEmpty() || size.width <= 0f) return
    val norms = pts.map { it.row.normMv * if (it.cold) 2 else 1 }
    val peak = max(pts.maxOf { it.spreadMv }, norms.max()).coerceAtLeast(10) * HISTORY_HEADROOM
    val left = 36.dp.toPx()
    val g = HistoryGeom(left, 18.dp.toPx(), size.height - 20.dp.toPx(), (size.width - left) / pts.size, niceTop(peak))
    drawLine(c.axis, Offset(g.left, g.bottom), Offset(size.width, g.bottom), strokeWidth = 1.dp.toPx())
    for (v in intArrayOf(0, g.scaleTop / 2, g.scaleTop)) {
        val t = measurer.measure("$v", label)
        drawText(t, topLeft = Offset(g.left - t.size.width - 6.dp.toPx(), g.y(v) - t.size.height / 2f))
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    norms.forEachIndexed { i, n ->
        drawLine(c.norm.copy(alpha = NORM_ALPHA), Offset(g.x(i) - g.step / 2f, g.y(n)), Offset(g.x(i) + g.step / 2f, g.y(n)), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
    }
    if (pts.size > 1) {
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(g.x(i), g.y(p.spreadMv)) else path.lineTo(g.x(i), g.y(p.spreadMv)) }
        drawPath(path, c.bar.copy(alpha = LINE_ALPHA), style = Stroke(width = 2.dp.toPx()))
    }
    drawHistoryPoints(input, g, c, measurer, label)
    val first = measurer.measure(dayMonth(pts.first().ts), label)
    drawText(first, topLeft = Offset((g.x(0) - first.size.width / 2f).coerceAtLeast(g.left), g.bottom + 4.dp.toPx()))
    if (pts.size > 1) {
        val last = measurer.measure(dayMonth(pts.last().ts), label)
        drawText(last, topLeft = Offset((g.x(pts.lastIndex) - last.size.width / 2f).coerceAtMost(size.width - last.size.width), g.bottom + 4.dp.toPx()))
    }
}

/** Dots by level (hollow = preliminary), the selected one ringed; the values of the selected and of the last one. */
private fun DrawScope.drawHistoryPoints(input: HistoryInput, g: HistoryGeom, c: ChartColors, measurer: TextMeasurer, label: TextStyle) {
    val pts = input.points
    pts.forEachIndexed { i, p ->
        val center = Offset(g.x(i), g.y(p.spreadMv))
        val selected = p.id == input.selectedId
        val r = (if (selected) 7 else 5).dp.toPx()
        val dot = input.dots.getOrElse(i) { c.bar }
        drawCircle(if (p.preliminary) input.hole else dot, r, center)
        if (p.preliminary) drawCircle(dot, r, center, style = Stroke(2.dp.toPx()))
        if (selected) drawCircle(c.strong, r + 3.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        if (selected || i == pts.lastIndex) {
            val t = measurer.measure("${p.spreadMv}", label.copy(color = c.strong))
            drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - r - t.size.height - 4.dp.toPx()))
        }
    }
}

/** The axis top above [peak]: a round 10, 50 or 100 mV step. */
private fun niceTop(peak: Double): Int {
    val stepMv = when {
        peak > 300 -> 100
        peak > 60 -> 50
        else -> 10
    }
    return (ceil(peak / stepMv) * stepMv).toInt().coerceAtLeast(stepMv)
}
