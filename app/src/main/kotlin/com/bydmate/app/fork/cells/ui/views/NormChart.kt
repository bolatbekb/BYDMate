package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.levelColor
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.ui.skin.withTnum
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.ConsumptionGood
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

// The «Норма по заряду» chart: one Canvas, the charge bands with their norm and service number on a log scale. fork-local.

private val Y_MARKS = intArrayOf(0, 10, 20, 50, 90, 150, 300)
private val X_MARKS = intArrayOf(0, 10, 15, 50, 85, 100)
private const val WIDE_ZONE = 40
private const val TOP_MV = 320
private const val BAND_OK = 0.12f
private const val BAND_WARN = 0.09f
private const val BAND_BAD = 0.09f
private const val BAND_LIT = 1.6f
private const val HALO = 0.22f
private const val CHIP = 0.85f

/** The texts of the chart, resolved in composition and measured in the draw pass. */
private data class NormTexts(
    val zones: List<String>,
    val upto: List<String>,
    val service: List<String?>,
    val here: String,
    val hereValue: String,
    val previous: String?,
    val full: String,
)

private data class NormInk(val ok: Color, val warn: Color, val bad: Color, val axis: Color, val muted: Color, val primary: Color, val secondary: Color, val level: Color, val chip: Color)

private class NormGeom(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    fun x(soc: Int): Float = left + (right - left) * soc / 100f
    fun y(mv: Int): Float = bottom - (bottom - top) * normChartY(mv)
}

@Composable
internal fun NormChart(model: NormChartModel, e: Evaluation, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.withTnum()
    val texts = NormTexts(
        zones = listOf(R.string.fork_cells_norm_zone_low, R.string.fork_cells_norm_zone_plateau, R.string.fork_cells_norm_zone_top).map { stringResource(it) },
        upto = model.zones.map { TextRes(R.string.fork_cells_norm_upto, it.normMv).text() },
        service = model.zones.map { z ->
            val res = if (z.socTo - z.socFrom > WIDE_ZONE) R.string.fork_cells_norm_service else R.string.fork_cells_norm_service_short
            z.serviceMv?.let { TextRes(res, it).text() }
        },
        here = stringResource(R.string.fork_cells_norm_here),
        hereValue = TextRes(R.string.fork_cells_norm_here_value, socArg(e.input.soc), e.spreadMv).text(),
        previous = model.previous?.let { TextRes(R.string.fork_cells_norm_previous, it.second).text() },
        full = TextRes(R.string.fork_cells_val_percent, 100).text(),
    )
    val ink = NormInk(ConsumptionGood, AccentOrange, SocRed, CardBorder, TextMuted, TextPrimary, TextSecondary, levelColor(model.level), CardSurfaceElevated)
    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val g = NormGeom(40.dp.toPx(), size.width - 6.dp.toPx(), 38.dp.toPx(), size.height - 20.dp.toPx())
        if (g.bottom <= g.top || g.right <= g.left) return@Canvas
        drawBands(g, model.zones, model.lit, ink)
        val l = Labels(measurer, base)
        drawZoneLines(g, model.zones, texts, ink, l)
        drawAxes(g, texts, ink, l)
        drawHere(g, model, texts, ink, l)
    }
}

/** Green up to the norm, orange to the service number (or the top), red above it; the zone of the measurement a bit brighter. */
private fun DrawScope.drawBands(g: NormGeom, zones: List<NormZone>, lit: Int, ink: NormInk) {
    zones.forEachIndexed { i, z ->
        val k = if (i == lit) BAND_LIT else 1f
        val x0 = g.x(z.socFrom)
        val w = g.x(z.socTo) - x0
        val svcY = g.y(z.serviceMv ?: TOP_MV)
        drawRect(ink.ok.copy(alpha = BAND_OK * k), Offset(x0, g.y(z.normMv)), Size(w, g.bottom - g.y(z.normMv)))
        drawRect(ink.warn.copy(alpha = BAND_WARN * k), Offset(x0, svcY), Size(w, g.y(z.normMv) - svcY))
        if (z.serviceMv != null) drawRect(ink.bad.copy(alpha = BAND_BAD * k), Offset(x0, g.top), Size(w, svcY - g.top))
        if (z.socFrom > 0) {
            drawLine(ink.axis, Offset(x0, g.top), Offset(x0, g.bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        }
    }
}

/**
 * The norm (solid green, «до N») and the service number (dashed red) of each band. A wide band has both labels above their
 * lines; a narrow band hangs both under their lines (the zone name is above the plot), and a service label that would touch
 * the norm label is left out: the dashed red line still says it.
 */
private fun DrawScope.drawZoneLines(g: NormGeom, zones: List<NormZone>, texts: NormTexts, ink: NormInk, l: Labels) {
    val pad = 5.dp.toPx()
    val gap = 2.dp.toPx()
    zones.forEachIndexed { i, z ->
        val x0 = g.x(z.socFrom)
        val x1 = g.x(z.socTo)
        val wide = z.socTo - z.socFrom > WIDE_ZONE
        val ny = g.y(z.normMv)
        drawLine(ink.ok, Offset(x0, ny), Offset(x1, ny), 1.5.dp.toPx())
        val upto = l.measure(texts.upto[i], 11f, ink.ok, bold = true)
        val uptoTop = if (wide || z.serviceMv == null) ny - upto.size.height - gap else ny + gap
        drawText(upto, topLeft = Offset(x0 + pad, uptoTop))
        val svc = z.serviceMv ?: return@forEachIndexed
        val sy = g.y(svc)
        drawLine(ink.bad, Offset(x0, sy), Offset(x1, sy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        val t = l.measure(texts.service[i] ?: return@forEachIndexed, 11f, ink.bad)
        val top = if (wide) sy - t.size.height - gap else sy + gap
        val clash = !wide && top + t.size.height > uptoTop - gap && top < uptoTop + upto.size.height
        if (!clash) drawText(t, topLeft = Offset(if (wide) x0 + pad else x1 - pad - t.size.width, top))
    }
}

/** Zone names over the plot, the mV marks on the left, the charge marks under it. */
private fun DrawScope.drawAxes(g: NormGeom, texts: NormTexts, ink: NormInk, l: Labels) {
    val spans = listOf(0 to 15, 15 to 85, 85 to 100)
    spans.forEachIndexed { i, (a, b) ->
        val w = (g.x(b) - g.x(a) + 16.dp.toPx()).toInt()
        val t = l.centered(texts.zones.getOrElse(i) { "" }, 11.5f, ink.secondary, w)
        drawText(t, topLeft = Offset((g.x(a) + g.x(b)) / 2f - t.size.width / 2f, g.top - t.size.height - 4.dp.toPx()))
    }
    for (v in Y_MARKS) {
        val t = l.measure("$v", 11f, ink.muted)
        drawText(t, topLeft = Offset(g.left - t.size.width - 7.dp.toPx(), g.y(v) - t.size.height / 2f))
    }
    for (s in X_MARKS) {
        val t = l.measure(if (s == 100) texts.full else "$s", 11f, ink.muted)
        val x = when (s) {
            0 -> g.x(0)
            100 -> g.x(100) - t.size.width
            else -> g.x(s) - t.size.width / 2f
        }
        drawText(t, topLeft = Offset(x, g.bottom + 4.dp.toPx()))
    }
}

/** «Вы здесь» in the level's colour with its value on a chip; the previous measurement of the zone as a ring, its label opposite. */
private fun DrawScope.drawHere(g: NormGeom, model: NormChartModel, texts: NormTexts, ink: NormInk, l: Labels) {
    val (soc, spread) = model.here ?: return
    val c = Offset(g.x(soc.coerceAtMost(99)), g.y(spread.coerceAtMost(TOP_MV)))
    val title = l.measure(texts.here, 16f, ink.primary, bold = true)
    val value = l.measure(texts.hereValue, 12.5f, ink.secondary)
    val w = maxOf(title.size.width, value.size.width).toFloat()
    val toLeft = c.x + 16.dp.toPx() + w > g.right
    model.previous?.let { (_, mv) ->
        val p = Offset(c.x, g.y(mv.coerceAtMost(TOP_MV)))
        drawCircle(ink.secondary, 5.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        texts.previous?.let { chip(l.measure(it, 11.5f, ink.secondary), p, !toLeft, ink.chip) }
    }
    drawLine(ink.level.copy(alpha = 0.7f), c, Offset(c.x, g.bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
    drawCircle(ink.level.copy(alpha = HALO), 13.dp.toPx(), c)
    drawCircle(ink.level, 6.5.dp.toPx(), c)
    drawCircle(ink.chip, 6.5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
    val h = (title.size.height + value.size.height).toFloat()
    val x = if (toLeft) c.x - 16.dp.toPx() - w else c.x + 16.dp.toPx()
    val y = (c.y - h / 2f).within(g.top, g.bottom - h)
    val pad = 6.dp.toPx()
    drawRoundRect(ink.chip.copy(alpha = CHIP), Offset(x - pad, y - pad / 2f), Size(w + pad * 2, h + pad), CornerRadius(pad))
    drawText(title, topLeft = Offset(x, y))
    drawText(value, topLeft = Offset(x, y + title.size.height))
}

/** A label on a soft chip beside [at]: to the left when [left], else to the right. */
private fun DrawScope.chip(t: TextLayoutResult, at: Offset, left: Boolean, bg: Color) {
    val gap = 10.dp.toPx()
    val pad = 4.dp.toPx()
    val x = if (left) at.x - gap - t.size.width else at.x + gap
    val y = at.y - t.size.height / 2f
    drawRoundRect(bg.copy(alpha = CHIP), Offset(x - pad, y - pad / 2f), Size(t.size.width + pad * 2, t.size.height + pad), CornerRadius(pad))
    drawText(t, topLeft = Offset(x, y))
}
