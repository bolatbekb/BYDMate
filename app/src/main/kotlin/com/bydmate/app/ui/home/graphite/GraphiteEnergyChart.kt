package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.bydmate.app.ui.home.common.px
import kotlin.math.max
import kotlin.math.min

/* ---------------------------------------------------------------- «Расход энергии» chart */

/**
 * The chart; its bars grow in from the baseline whenever a series for another period arrives
 * (and on the first one). [grow] holds the elapsed ms of that grow-in, read only while drawing.
 */
@Composable
internal fun EnergyChart(series: EnergySeries?, hasFuelTank: Boolean) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val measurer = rememberTextMeasurer()
    val shownPeriod = series?.period
    val n = series?.kwh?.size ?: 0
    // A fresh Animatable per period. Grown at once unless the user moved to another period: opening
    // the home or returning to it must not replay the grow-in (the values are fully visible at total).
    val grow = remember(shownPeriod) {
        val grown = shownPeriod == null || !shouldGrow(GraphiteWarm.lastShownPeriod, shownPeriod)
        Animatable(if (grown) growTotalMs(n).toFloat() else 0f)
    }
    LaunchedEffect(grow) {
        if (shownPeriod == null) return@LaunchedEffect
        GraphiteWarm.lastShownPeriod = shownPeriod
        val total = growTotalMs(n)
        if (grow.value < total) grow.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }
    // The labels are measured when the cache builds (new series, size, scheme or language), not per frame.
    val font = GraphiteLookState.font
    val chart = remember(series, hasFuelTank, p, lang, font, measurer, grow) {
        Modifier.drawWithCache {
            if (series == null) return@drawWithCache onDrawBehind {}
            val frame = EnergyFrame(series, hasFuelTank, px(1).toPx(), size.width, size.height)
            val content = EnergyChartContent(frame, series, measurer, p, lang)
            onDrawBehind { drawEnergy(content, grow.value) }
        }
    }
    Canvas(Modifier.fillMaxWidth().height(px(266)).then(chart)) {}
}

/** One bucket's bars, mockup px: kWh on the left, litres on the right; [fuelW] 0 = no litres bar. */
internal data class BarPair(val kwhX: Float, val kwhW: Float, val fuelX: Float, val fuelW: Float)

private const val PAIR_GAP = 4f

/**
 * The bars of a slot [slot] wide starting at [slotLeft], centred in it. With fuel: two bars
 * min(18, slot × 0.2 — 0.24 past 20 buckets), at least 3, 4 apart; without: one bar as wide as
 * the old single-series chart, min(40, slot × 0.42 — 0.5 past 20), at least 4.
 */
internal fun barPair(slotLeft: Float, slot: Float, n: Int, hasFuel: Boolean): BarPair {
    val dense = n > 20
    if (!hasFuel) {
        val w = max(4f, min(40f, slot * (if (dense) 0.5f else 0.42f)))
        val x = slotLeft + (slot - w) / 2
        return BarPair(x, w, x + w, 0f)
    }
    val w = max(3f, min(18f, slot * (if (dense) 0.24f else 0.2f)))
    val x = slotLeft + (slot - (2 * w + PAIR_GAP)) / 2
    return BarPair(x, w, x + w + PAIR_GAP, w)
}

private const val VALUE_FADE_MS = 150f
private const val AXIS_L = 64f
private const val AXIS_R = 56f

/**
 * The chart's frame in mockup px for a canvas [width] × [height] (canvas px, [u] to a mockup px):
 * kWh (Electric, left axis) and litres (Fuel, right axis) side by side per bucket from one zero
 * baseline; each series is scaled to its own max × 1.12. Without litres: one centred kWh bar.
 */
private class EnergyFrame(s: EnergySeries, hasFuelTank: Boolean, val u: Float, width: Float, height: Float) {
    val w = width / u
    val h = height / u
    val n = s.kwh.size
    // A car with a tank keeps the litres axis (0..1, no bars) for a window without fuel.
    val hasFuel = s.litres != null || hasFuelTank
    val litres = s.litres ?: if (hasFuel) List(n) { 0.0 } else null
    val padL = AXIS_L
    val padR = if (hasFuel) AXIS_R else 8f
    val top = 28f
    val base = h - 34f
    val plotH = base - top - 4f
    val maxK = (s.kwh.maxOrNull() ?: 0.0).let { if (it > 0) it * 1.12 else 1.0 }
    val maxL = (litres?.maxOrNull() ?: 0.0).let { if (it > 0) it * 1.12 else 1.0 }
    private val cw = w - padL - padR
    val slot = if (n > 0) cw / n else cw
    val x1 = padL
    val x2 = w - padR
}

/** One text, canvas px; [align] 0 centre, 1 right edge at x, 2 left edge at x; a [value] fades in. */
private class ChartLabel(val layout: TextLayoutResult, val x: Float, val baseline: Float, val align: Int, val value: Boolean)

/** One bar, its path in canvas px; [index] is its bucket, for the staggered grow-in. */
private class ChartBar(val path: Path, val color: Color, val alpha: Float, val index: Int)

/**
 * Everything the chart draws for one series and size, measured and built once per cache build:
 * the grid rows, the bars and the labels, each in drawing order.
 */
private class EnergyChartContent(
    val frame: EnergyFrame,
    private val s: EnergySeries,
    private val tm: TextMeasurer,
    private val p: GrPalette,
    private val lang: GrLang,
) {
    val grid = p.divider
    val dash = PathEffect.dashPathEffect(floatArrayOf(2 * frame.u, 6 * frame.u))
    val baseline = p.textPrimary.copy(alpha = 0.22f)
    val gridYs = ArrayList<Float>()
    val labels = ArrayList<ChartLabel>()
    val bars = ArrayList<ChartBar>()

    init {
        if (!s.empty) axes()
        for (i in 0 until frame.n) bucket(i)
    }

    private fun style(size: Float, weight: Int, color: Color): TextStyle = grStyle(size, weight, color = color, tnum = false)

    /** [x] and [y] in mockup px. */
    private fun add(l: TextLayoutResult, x: Float, y: Float, align: Int, value: Boolean = false) {
        labels.add(ChartLabel(l, x * frame.u, y * frame.u, align, value))
    }

    private fun text(text: String, style: TextStyle, x: Float, y: Float, align: Int) = add(tm.measure(text, style), x, y, align)

    private fun axisNumber(v: Double): String = grDec(v, if (v < 10) 1 else 0, lang)

    /** The axis numbers and units, and the grid rows. */
    private fun axes() {
        val f = frame
        val lx = f.padL - 14
        val rx = f.w - f.padR + 14
        listOf(0f, 0.5f, 1f).forEach { k ->
            val y = f.base - f.plotH * k
            if (k > 0f) gridYs.add(y)
            text(if (k == 0f) "0" else axisNumber(f.maxK * k), style(19f, 400, p.textTertiary), lx, y + 6, 1)
            if (f.hasFuel) text(if (k == 0f) "0" else axisNumber(f.maxL * k), style(19f, 400, p.textTertiary), rx, y + 6, 2)
        }
        // Left-aligned at the chart edge: a wide unit (千瓦时) must not cross into the card padding.
        text(lang.words.kwh, style(19f, 400, p.textTertiary), 0f, f.top - 12, 2)
        if (f.hasFuel) text(lang.words.litre, style(19f, 400, p.textTertiary), rx, f.top - 12, 2)
    }

    /** Bucket [i]: its bars, the values over them when it is the current one, and its label. */
    private fun bucket(i: Int) {
        val f = frame
        val slotLeft = f.padL + f.slot * i
        val pair = barPair(slotLeft, f.slot, f.n, f.hasFuel)
        val k = s.kwh[i]
        val l = f.litres?.getOrNull(i) ?: 0.0
        val hk = (k / f.maxK * f.plotH).toFloat()
        val hl = (l / f.maxL * f.plotH).toFloat()
        if (hk > 0f) bar(i, pair.kwhX, pair.kwhW, hk, p.electric)
        if (f.hasFuel && hl > 0f) bar(i, pair.fuelX, pair.fuelW, hl, p.fuel)
        if (i == s.current) {
            values(
                if (k > 0) tm.measure(grDec(k, lang = lang), style(19f, 600, p.electric)) else null,
                if (f.hasFuel && l > 0) tm.measure(grDec(l, lang = lang), style(19f, 600, p.fuel)) else null,
                pair, f.base - 2 - hk - 14, f.base - 2 - hl - 14,
            )
        }
        bucketLabel(i, slotLeft + f.slot / 2)
    }

    /** A bar of [h] up from the baseline; the current bucket's at full opacity. */
    private fun bar(i: Int, x: Float, w: Float, h: Float, color: Color) {
        val u = frame.u
        val base = frame.base
        val rr = min(min(w / 2, 6f), h) * u
        val path = Path().apply {
            addRoundRect(RoundRect(x * u, (base - 2 - h) * u, (x + w) * u, (base - 2) * u,
                CornerRadius(rr), CornerRadius(rr), CornerRadius.Zero, CornerRadius.Zero))
        }
        bars.add(ChartBar(path, color, if (i == s.current) 1f else p.dimOpacity, i))
    }

    /** Up to 12 buckets each is labelled, past that every third and the last; the current one bold. */
    private fun bucketLabel(i: Int, cx: Float) {
        val n = frame.n
        if (n > 12 && i % 3 != 0 && i != n - 1) return
        val cur = i == s.current
        text(s.labels.getOrElse(i) { "" }, style(19f, if (cur) 600 else 400, if (cur) p.textPrimary else p.textTertiary), cx, frame.h - 6, 0)
    }

    /**
     * The current bucket's values: each centred over its own bar at [yk] / [yl] (baselines, mockup
     * px); when the two would collide they go side by side, kWh ending and litres starting at the
     * pair's middle, both over the taller bar. Kept inside the plot.
     */
    private fun values(kwh: TextLayoutResult?, lit: TextLayoutResult?, pair: BarPair, yk: Float, yl: Float) {
        val u = frame.u
        val minX = frame.x1
        val maxX = frame.x2
        fun w(l: TextLayoutResult) = l.size.width / u
        fun h(l: TextLayoutResult) = l.size.height / u
        fun clampCentre(cx: Float, l: TextLayoutResult) = cx.coerceIn(minX + w(l) / 2, max(minX + w(l) / 2, maxX - w(l) / 2))
        val kc = pair.kwhX + pair.kwhW / 2
        val lc = pair.fuelX + pair.fuelW / 2
        if (kwh != null && lit != null) {
            val kx = clampCentre(kc, kwh); val lx = clampCentre(lc, lit)
            val apartX = kx + w(kwh) / 2 + 4 <= lx - w(lit) / 2
            val apartY = kotlin.math.abs(yk - yl) >= max(h(kwh), h(lit))
            // The label over the shorter bar sits lower: it must not run into the taller bar beside it.
            val crosses = if (yk > yl) kx + w(kwh) / 2 + 2 > pair.fuelX else lx - w(lit) / 2 - 2 < pair.kwhX + pair.kwhW
            if (!crosses && (apartX || apartY)) {
                add(kwh, kx, yk, 0, value = true); add(lit, lx, yl, 0, value = true)
            } else {
                val y = min(yk, yl)
                val mid = (pair.kwhX + pair.kwhW + pair.fuelX) / 2
                val total = w(kwh) + 6 + w(lit)
                // Shift the pair of labels inside the plot, keeping the 6 px between them.
                val start = (mid - 3 - w(kwh)).coerceIn(minX, max(minX, maxX - total))
                add(kwh, start + w(kwh), y, 1, value = true); add(lit, start + w(kwh) + 6, y, 2, value = true)
            }
        } else {
            kwh?.let { add(it, clampCentre(kc, it), yk, 0, value = true) }
            lit?.let { add(it, clampCentre(lc, it), yl, 0, value = true) }
        }
    }
}

/** The grid, the baseline, the bars at [ms] into their grow-in, and the labels. */
private fun DrawScope.drawEnergy(c: EnergyChartContent, ms: Float) {
    val f = c.frame
    val u = f.u
    for (y in c.gridYs) {
        drawLine(c.grid, Offset(f.x1 * u, y * u), Offset(f.x2 * u, y * u), u, pathEffect = c.dash)
    }
    drawLine(c.baseline, Offset(f.x1 * u, f.base * u), Offset(f.x2 * u, f.base * u), u)
    // Each bar scales up from the baseline by its own eased growth; the cached paths stay.
    val total = growTotalMs(f.n).toFloat()
    val pivot = Offset(0f, (f.base - 2) * u)
    for (b in c.bars) {
        val g = FastOutSlowInEasing.transform(barGrowth(ms, b.index, f.n))
        if (g <= 0f) continue
        if (g >= 1f) drawPath(b.path, b.color, alpha = b.alpha)
        else scale(1f, g, pivot) { drawPath(b.path, b.color, alpha = b.alpha) }
    }
    // The current values fade in over the last 150 ms of the grow-in.
    val valueAlpha = ((ms - (total - VALUE_FADE_MS)) / VALUE_FADE_MS).coerceIn(0f, 1f)
    for (l in c.labels) {
        if (l.value && valueAlpha <= 0f) continue
        drawLabel(l, valueAlpha)
    }
}

private fun DrawScope.drawLabel(l: ChartLabel, valueAlpha: Float) {
    val w = l.layout.size.width
    val left = when (l.align) {
        1 -> l.x - w
        2 -> l.x
        else -> l.x - w / 2f
    }
    drawText(l.layout, topLeft = Offset(left, l.baseline - l.layout.firstBaseline), alpha = if (l.value) valueAlpha else Float.NaN)
}
