package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import com.bydmate.app.ui.home.common.fs
import com.bydmate.app.ui.home.common.px
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ---------------------------------------------------------------- «Расход энергии» */

@Composable
fun EnergyCard(
    series: EnergySeries?, period: ChartPeriod, onPeriod: (ChartPeriod) -> Unit, hasFuelTank: Boolean, modifier: Modifier,
) {
    val p = LocalGr.current
    CardBody(modifier) {
        CardHeader(R.drawable.gr_ic_leaf, stringResource(R.string.home_gr_energy_title)) { PeriodTabs(period, onPeriod) }
        if (series != null) EnergyTotals(series, hasFuelTank)
        Box(Modifier.fillMaxWidth().padding(top = if (series == null) px(22) else 0.dp)) {
            EnergyChart(series, hasFuelTank)
            if (series?.empty == true) {
                Text(
                    stringResource(R.string.home_gr_energy_empty), maxLines = 1, softWrap = false,
                    style = grStyle(22f, 400, color = p.textTertiary, tnum = false),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private val TAB_LABELS = listOf(
    ChartPeriod.DAY to R.string.home_gr_period_day,
    ChartPeriod.WEEK to R.string.home_gr_period_week,
    ChartPeriod.MONTH to R.string.home_gr_period_month,
)

private const val TabsPad = 5f
private const val TabsTouchMargin = 20f // the 54 px block takes touches over 94 px, up to the card top

/** Index of the tab under [x]: tabs of [widths] side by side from [start]; the paddings go to the end tabs. */
internal fun tabAt(x: Float, start: Float, widths: IntArray): Int {
    var right = start
    for (i in widths.indices) {
        right += widths[i]
        if (x < right) return i
    }
    return widths.lastIndex
}

/**
 * Day / week / month. One tap handler over the whole block plus [TabsTouchMargin] above and
 * below (the tabs themselves are only 44 px tall); the tab is picked by x.
 */
@Composable
private fun PeriodTabs(period: ChartPeriod, onPeriod: (ChartPeriod) -> Unit) {
    val p = LocalGr.current
    val widths = remember { IntArray(TAB_LABELS.size) }
    val latest = rememberUpdatedState(onPeriod)
    Box(
        Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true)
            .touchMarginVertical(TabsTouchMargin, Modifier.pointerInput(Unit) {
                detectTapGestures { latest.value(TAB_LABELS[tabAt(it.x, px(TabsPad).toPx(), widths)].first) }
            }),
    ) {
        InsetBox(22f) {
            Row(Modifier.padding(px(TabsPad))) {
                TAB_LABELS.forEachIndexed { i, (value, labelRes) ->
                    val on = value == period
                    Text(
                        stringResource(labelRes), maxLines = 1, softWrap = false,
                        style = grStyle(20f, 400, color = if (on) p.textPrimary else p.textSecondary, tnum = false),
                        modifier = Modifier
                            .onSizeChanged { widths[i] = it.width }
                            .then(if (on) Modifier.grPill(p.tabActive, p.tabActiveEdge, p.edgeStyle, 17f) else Modifier)
                            .clip(RoundedCornerShape(px(17)))
                            .padding(horizontal = px(24), vertical = px(10)),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnergyTotals(s: EnergySeries, hasFuelTank: Boolean) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val w = lang.words
    val evShare = s.electricShare?.let { stringResource(R.string.home_gr_energy_ev_share, "$it") }
    Row(Modifier.fillMaxWidth().padding(top = px(22), bottom = px(18))) {
        TotalColumn(
            if (s.empty) "0" else grDec(s.totalKwh, lang = lang), w.kwh, s.kwhPer100?.let { per100(it, w.kwh, lang) }, p.electric,
            Modifier.weight(1.1f), first = true,
        )
        // A car with a tank always shows its litres, «0» for a window without fuel.
        if (s.totalLitres != null || hasFuelTank) {
            TotalColumn(
                if (s.empty || s.totalLitres == null) "0" else grDec(s.totalLitres, lang = lang), w.litre,
                s.litresPer100?.let { per100(it, w.litre, lang) }, p.fuel,
                Modifier.weight(1f), first = false,
            )
        }
        TotalColumn(
            grInt(s.totalKm.roundToInt(), lang), w.km, evShare, null,
            Modifier.weight(1f), first = false,
        )
    }
}

/** «12,3 кВт·ч / 100 км» in [lang]. */
private fun per100(v: Double, unit: String, lang: GrLang): String = grDec(v, lang = lang) + " $unit / 100 " + lang.words.km

@Composable
private fun TotalColumn(value: String, unit: String, sub: String?, dot: Color?, modifier: Modifier, first: Boolean) {
    val p = LocalGr.current
    val line = p.divider
    Column(
        modifier
            .then(
                if (first) Modifier else Modifier
                    .drawBehind { drawLine(line, Offset.Zero, Offset(0f, size.height), px(1).toPx()) }
                    .padding(start = px(26)),
            ),
    ) {
        Text(
            buildAnnotatedString {
                append(value)
                withStyle(SpanStyle(fontSize = fs(14f), letterSpacing = fs(0f))) { append(" ") }
                withStyle(SpanStyle(fontSize = fs(22f), color = p.textSecondary, letterSpacing = fs(0f), fontWeight = FontWeight(400))) {
                    append(unit)
                }
            },
            maxLines = 1, softWrap = false,
            style = grStyle(46f, 300, -1.5f, p.textPrimary).copy(lineHeight = fs(46f * 1.1f)),
        )
        if (sub != null) {
            Row(Modifier.padding(top = px(6)), verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) {
                    Box(Modifier.size(px(10)).clip(RoundedCornerShape(px(3))).drawBehind { drawRect(dot) })
                    Spacer(Modifier.width(px(8)))
                }
                FitText(sub, grStyle(19f, 400, color = p.textTertiary, tnum = false), 19f, 15f, Modifier)
            }
        }
    }
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

/**
 * The chart; its bars grow in from the baseline whenever a series for another period arrives
 * (and on the first one). [grow] holds the elapsed ms of that grow-in, read only while drawing.
 */
@Composable
private fun EnergyChart(series: EnergySeries?, hasFuelTank: Boolean) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val measurer = rememberTextMeasurer()
    val shownPeriod = series?.period
    val n = series?.kwh?.size ?: 0
    // A fresh Animatable per period: the new series' first frame already draws at 0 (no flash).
    val grow = remember(shownPeriod) { Animatable(0f) }
    LaunchedEffect(grow) {
        if (shownPeriod == null) return@LaunchedEffect
        val total = growTotalMs(n)
        grow.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }
    // The labels are measured when the cache builds (new series, size, scheme or language), not per frame.
    val chart = remember(series, hasFuelTank, p, lang, measurer, grow) {
        Modifier.drawWithCache {
            if (series == null) return@drawWithCache onDrawBehind {}
            buildChart(this, measurer, series, hasFuelTank, p, lang) { grow.value }
        }
    }
    Canvas(Modifier.fillMaxWidth().height(px(266)).then(chart)) {}
}

private const val VALUE_FADE_MS = 150f
private const val AXIS_L = 64f
private const val AXIS_R = 56f

/**
 * kWh (Electric, left axis) and litres (Fuel, right axis) side by side per bucket from one zero
 * baseline; each series is scaled to its own max × 1.12. Without litres: one centred kWh bar.
 */
private fun buildChart(
    scope: androidx.compose.ui.draw.CacheDrawScope, tm: TextMeasurer, s: EnergySeries, hasFuelTank: Boolean, p: GrPalette,
    lang: GrLang, growMs: () -> Float,
): androidx.compose.ui.draw.DrawResult = with(scope) {
    val u = px(1).toPx()
    val W = size.width / u
    val H = size.height / u
    val n = s.kwh.size
    // A car with a tank keeps the litres axis (0..1, no bars) for a window without fuel.
    val hasFuel = s.litres != null || hasFuelTank
    val litres = s.litres ?: if (hasFuel) List(n) { 0.0 } else null
    val padL = AXIS_L; val padR = if (hasFuel) AXIS_R else 8f; val padB = 34f; val top = 28f
    val cw = W - padL - padR
    val base = H - padB
    val plotH = base - top - 4f
    val maxK = (s.kwh.maxOrNull() ?: 0.0).let { if (it > 0) it * 1.12 else 1.0 }
    val maxL = (litres?.maxOrNull() ?: 0.0).let { if (it > 0) it * 1.12 else 1.0 }
    val slot = if (n > 0) cw / n else cw

    val grid = p.divider
    val dash = PathEffect.dashPathEffect(floatArrayOf(2 * u, 6 * u))
    val baseC = p.textPrimary.copy(alpha = 0.22f)

    class Lbl(val l: TextLayoutResult, val x: Float, val baseline: Float, val align: Int, val value: Boolean = false)
    val labels = ArrayList<Lbl>()
    fun layout(text: String, size: Float, weight: Int, color: Color): TextLayoutResult =
        tm.measure(text, grStyle(size, weight, color = color, tnum = false))
    // align: 0 centre, 1 right edge at x, 2 left edge at x; x and y in mockup px
    fun addLbl(l: TextLayoutResult, x: Float, y: Float, align: Int) { labels.add(Lbl(l, x * u, y * u, align)) }
    fun addLbl(text: String, size: Float, weight: Int, color: Color, x: Float, y: Float, align: Int) =
        addLbl(layout(text, size, weight, color), x, y, align)

    val axisFmt = { v: Double -> grDec(v, if (v < 10) 1 else 0, lang) }
    val showAxes = !s.empty
    val gridYs = ArrayList<Float>()
    if (showAxes) {
        val lx = padL - 14; val rx = W - padR + 14
        listOf(0f, 0.5f, 1f).forEach { f ->
            val y = base - plotH * f
            if (f > 0f) gridYs.add(y)
            addLbl(if (f == 0f) "0" else axisFmt(maxK * f), 17f, 400, p.textTertiary, lx, y + 6, 1)
            if (hasFuel) addLbl(if (f == 0f) "0" else axisFmt(maxL * f), 17f, 400, p.textTertiary, rx, y + 6, 2)
        }
        addLbl(lang.words.kwh, 16f, 400, p.textTertiary, lx, top - 12, 1)
        if (hasFuel) addLbl(lang.words.litre, 16f, 400, p.textTertiary, rx, top - 12, 2)
    }

    class Bar(val path: Path, val color: Color, val alpha: Float, val index: Int)
    val bars = ArrayList<Bar>()
    fun bar(i: Int, x: Float, w: Float, h: Float, color: Color, alpha: Float) {
        val rr = min(min(w / 2, 6f), h) * u
        bars.add(Bar(Path().apply {
            addRoundRect(RoundRect(x * u, (base - 2 - h) * u, (x + w) * u, (base - 2) * u,
                CornerRadius(rr), CornerRadius(rr), CornerRadius.Zero, CornerRadius.Zero))
        }, color, alpha, i))
    }
    val x1 = padL; val x2 = W - padR
    for (i in 0 until n) {
        val slotLeft = padL + slot * i
        val cx = slotLeft + slot / 2
        val pair = barPair(slotLeft, slot, n, hasFuel)
        val cur = i == s.current
        val op = if (cur) 1f else p.dimOpacity
        val k = s.kwh[i]
        val l = litres?.getOrNull(i) ?: 0.0
        val hk = (k / maxK * plotH).toFloat()
        val hl = (l / maxL * plotH).toFloat()
        if (hk > 0f) bar(i, pair.kwhX, pair.kwhW, hk, p.electric, op)
        if (hasFuel && hl > 0f) bar(i, pair.fuelX, pair.fuelW, hl, p.fuel, op)
        if (cur) valueLabels(
            if (k > 0) layout(grDec(k, lang = lang), 18f, 600, p.electric) else null,
            if (hasFuel && l > 0) layout(grDec(l, lang = lang), 18f, 600, p.fuel) else null,
            pair, base - 2 - hk - 14, base - 2 - hl - 14, u, x1, x2,
        ) { lay, x, y, align -> labels.add(Lbl(lay, x * u, y * u, align, value = true)) }
        if (n <= 12 || i % 3 == 0 || i == n - 1) {
            addLbl(s.labels.getOrElse(i) { "" }, 18f, if (cur) 600 else 400, if (cur) p.textPrimary else p.textTertiary, cx, H - 6, 0)
        }
    }

    onDrawBehind {
        for (y in gridYs) {
            drawLine(grid, Offset(x1 * u, y * u), Offset(x2 * u, y * u), u, pathEffect = dash)
        }
        drawLine(baseC, Offset(x1 * u, base * u), Offset(x2 * u, base * u), u)
        // Each bar scales up from the baseline by its own eased growth; the cached paths stay.
        val ms = growMs()
        val total = growTotalMs(n).toFloat()
        val pivot = Offset(0f, (base - 2) * u)
        for (b in bars) {
            val g = FastOutSlowInEasing.transform(barGrowth(ms, b.index, n))
            if (g <= 0f) continue
            if (g >= 1f) drawPath(b.path, b.color, alpha = b.alpha)
            else scale(1f, g, pivot) { drawPath(b.path, b.color, alpha = b.alpha) }
        }
        // The current values fade in over the last 150 ms of the grow-in.
        val valueAlpha = ((ms - (total - VALUE_FADE_MS)) / VALUE_FADE_MS).coerceIn(0f, 1f)
        for (l in labels) {
            if (l.value && valueAlpha <= 0f) continue
            val w = l.l.size.width
            val left = when (l.align) { 1 -> l.x - w; 2 -> l.x; else -> l.x - w / 2f }
            drawText(l.l, topLeft = Offset(left, l.baseline - l.l.firstBaseline), alpha = if (l.value) valueAlpha else Float.NaN)
        }
    }
}

/**
 * The current bucket's values: each centred over its own bar at [yk] / [yl] (baselines, mockup
 * px); when the two would collide they go side by side, kWh ending and litres starting at the
 * pair's middle, both over the taller bar. Kept between [minX] and [maxX] (the plot).
 */
private fun valueLabels(
    kwh: TextLayoutResult?, lit: TextLayoutResult?, pair: BarPair, yk: Float, yl: Float, u: Float,
    minX: Float, maxX: Float, add: (TextLayoutResult, Float, Float, Int) -> Unit,
) {
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
            add(kwh, kx, yk, 0); add(lit, lx, yl, 0)
        } else {
            val y = min(yk, yl)
            val mid = (pair.kwhX + pair.kwhW + pair.fuelX) / 2
            val total = w(kwh) + 6 + w(lit)
            // Shift the pair of labels inside the plot, keeping the 6 px between them.
            val start = (mid - 3 - w(kwh)).coerceIn(minX, max(minX, maxX - total))
            add(kwh, start + w(kwh), y, 1); add(lit, start + w(kwh) + 6, y, 2)
        }
    } else {
        kwh?.let { add(it, clampCentre(kc, it), yk, 0) }
        lit?.let { add(it, clampCentre(lc, it), yl, 0) }
    }
}
