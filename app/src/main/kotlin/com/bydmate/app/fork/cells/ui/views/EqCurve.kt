package com.bydmate.app.fork.cells.ui.views

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.bydmate.app.fork.cells.ui.levelColor

// The measurement's own curve on the «Выравнивание» chart: the line, the dots and their values. fork-local.

private const val MAX_VALUE_LABELS = 16

/**
 * The measurement's curve in its level's colour. After a charge: grey dots in the first hour, green within the norm, orange
 * above (no red for a snapshot). Parkings: each dot in its own level, hollow when preliminary, with its value when they are
 * few. The measurement itself is ringed and always labelled.
 */
@Suppress("LongParameterList") // why: the curve, its mode and norm, the colours and the labels
internal fun DrawScope.drawCurve(g: EqGeom, pts: List<EqPoint>, charge: Boolean, norm: Int, ink: EqInk, l: Labels) {
    if (pts.isEmpty()) return
    if (pts.size > 1) drawPath(polyline(g, pts), ink.line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    val labelAll = !charge && pts.size <= MAX_VALUE_LABELS
    pts.forEachIndexed { i, p ->
        val c = Offset(g.x(p.x), g.y(p.mv))
        val r = when {
            p.current -> 7.dp
            charge -> 3.5.dp
            else -> 5.dp
        }.toPx()
        drawDot(c, r, p, dotColor(p, charge, norm, ink), ink)
        val firstAfterCharge = charge && i == 0
        if (labelAll || p.current || firstAfterCharge) valueLabel(g, c, r, p, ink, l)
    }
}

private fun dotColor(p: EqPoint, charge: Boolean, norm: Int, ink: EqInk): Color = when {
    p.grey -> ink.muted
    charge -> if (p.mv <= norm) ink.ok else ink.warn
    else -> p.level?.let { levelColor(it) } ?: ink.muted
}

/** Filled, or hollow when preliminary; the measurement itself with a ring. */
private fun DrawScope.drawDot(c: Offset, r: Float, p: EqPoint, color: Color, ink: EqInk) {
    if (p.preliminary) {
        drawCircle(ink.hole, r, c)
        drawCircle(color, r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
    } else {
        drawCircle(color, r, c)
    }
    if (p.current) drawCircle(ink.primary, r + 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
}

/** The value above the dot, below it when there is no room above. */
@Suppress("LongParameterList") // why: one label's place, value and look
private fun DrawScope.valueLabel(g: EqGeom, c: Offset, r: Float, p: EqPoint, ink: EqInk, l: Labels) {
    val t = l.measure("${p.mv}", 11.5f, if (p.current) ink.primary else ink.secondary, bold = p.current)
    val above = c.y - r - t.size.height - 4.dp.toPx()
    val y = if (above < g.top) c.y + r + 4.dp.toPx() else above
    drawText(t, topLeft = Offset((c.x - t.size.width / 2f).within(g.left, g.right - t.size.width), y))
}
