package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.bydmate.app.ui.home.hybrid.fs
import com.bydmate.app.ui.home.hybrid.px
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ---------------------------------------------------------------- «Расход энергии» */

@Composable
fun EnergyCard(series: EnergySeries?, period: ChartPeriod, onPeriod: (ChartPeriod) -> Unit, modifier: Modifier) {
    val p = LocalGr.current
    CardBody(modifier) {
        CardHeader(R.drawable.gr_ic_leaf, "Расход энергии") { PeriodTabs(period, onPeriod) }
        if (series != null) EnergyTotals(series)
        Box(Modifier.fillMaxWidth().padding(top = if (series == null) px(22) else 0.dp)) {
            EnergyChart(series)
            if (series?.empty == true) {
                Text(
                    "Нет поездок за период", maxLines = 1, softWrap = false,
                    style = grStyle(22f, 400, color = p.textTertiary, tnum = false),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private val TAB_LABELS = listOf(ChartPeriod.DAY to "День", ChartPeriod.WEEK to "Неделя", ChartPeriod.MONTH to "Месяц")

@Composable
private fun PeriodTabs(period: ChartPeriod, onPeriod: (ChartPeriod) -> Unit) {
    val p = LocalGr.current
    InsetBox(22f) {
        Row(Modifier.padding(px(5))) {
            for ((value, label) in TAB_LABELS) {
                val on = value == period
                Text(
                    label, maxLines = 1, softWrap = false,
                    style = grStyle(20f, 400, color = if (on) p.textPrimary else p.textSecondary, tnum = false),
                    modifier = Modifier
                        .then(if (on) Modifier.grPill(p.tabActive, p.tabActiveEdge, p.edgeStyle, 17f) else Modifier)
                        .clip(RoundedCornerShape(px(17)))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onPeriod(value) }
                        .padding(horizontal = px(24), vertical = px(10)),
                )
            }
        }
    }
}

@Composable
private fun EnergyTotals(s: EnergySeries) {
    val p = LocalGr.current
    Row(Modifier.fillMaxWidth().padding(top = px(22), bottom = px(18))) {
        TotalColumn(
            if (s.empty) "0" else grDec(s.totalKwh), "кВт·ч", s.kwhPer100?.let { grDec(it) + " кВт·ч / 100 км" }, p.electric,
            Modifier.weight(1.1f), first = true,
        )
        if (s.totalLitres != null) {
            TotalColumn(
                if (s.empty) "0" else grDec(s.totalLitres), "л", s.litresPer100?.let { grDec(it) + " л / 100 км" }, p.fuel,
                Modifier.weight(1f), first = false,
            )
        }
        TotalColumn(
            grInt(s.totalKm.roundToInt()), "км", s.electricShare?.let { "$it% на электротяге" }, null,
            Modifier.weight(1f), first = false,
        )
    }
}

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
                withStyle(SpanStyle(fontSize = fs(9f), letterSpacing = fs(0f))) { append(" ") }
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

@Composable
private fun EnergyChart(series: EnergySeries?) {
    val p = LocalGr.current
    val measurer = rememberTextMeasurer()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(px(266))
            .drawWithCache {
                if (series == null) return@drawWithCache onDrawBehind {}
                buildChart(this, measurer, series, p)
            },
    ) {}
}

private fun buildChart(
    scope: androidx.compose.ui.draw.CacheDrawScope, tm: TextMeasurer, s: EnergySeries, p: GrPalette,
): androidx.compose.ui.draw.DrawResult = with(scope) {
    val u = px(1).toPx()
    val W = size.width / u
    val H = size.height / u
    val n = s.kwh.size
    val padL = 64f; val padR = 8f; val padB = 34f; val top = 8f
    val cw = W - padL - padR; val ch = H - padB - top
    val base = top + ch * 0.62f
    val upH = base - top - 4f; val dnH = top + ch - base - 4f
    val maxK = (s.kwh.maxOrNull() ?: 0.0).let { if (it > 0) it * 1.12 else 1.0 }
    val litres = s.litres
    val maxL = (litres?.maxOrNull() ?: 0.0).let { if (it > 0) it * 1.5 else 1.0 }
    val slot = if (n > 0) cw / n else cw
    val bw = max(4f, min(40f, slot * (if (n > 20) 0.5f else 0.42f)))
    val r = min(bw / 2, 8f)

    fun layout(text: String, size: Float, weight: Int, color: Color): TextLayoutResult =
        tm.measure(text, grStyle(size, weight, color = color, tnum = false))

    val grid = p.divider
    val dash = PathEffect.dashPathEffect(floatArrayOf(2 * u, 6 * u))
    val baseC = p.textPrimary.copy(alpha = 0.22f)

    class Lbl(val l: TextLayoutResult, val x: Float, val baseline: Float, val align: Int)
    val labels = ArrayList<Lbl>()
    fun addLbl(text: String, size: Float, weight: Int, color: Color, x: Float, y: Float, align: Int) =
        layout(text, size, weight, color).let { labels.add(Lbl(it, x * u, y * u, align)) }

    val axisFmt = { v: Double -> grDec(v, if (v < 10) 1 else 0) }
    val showAxes = !s.empty
    val hasFuel = litres != null
    val botY = base + dnH
    val gridYs = ArrayList<Float>()
    if (showAxes) {
        listOf(0.5f, 1f).forEach { f ->
            gridYs.add(base - upH * f)
            addLbl(axisFmt(maxK * f), 17f, 400, p.textTertiary, padL - 14, base - upH * f + 6, 1)
        }
        if (hasFuel) {
            gridYs.add(botY)
            addLbl(grDec(maxL), 17f, 400, p.textTertiary, padL - 14, botY + 6, 1)
        }
        addLbl("0", 17f, 400, p.textTertiary, padL - 14, base + 6, 1)
    }

    val bars = ArrayList<Triple<Path, Color, Float>>()
    for (i in 0 until n) {
        val cx = padL + slot * i + slot / 2
        val x = cx - bw / 2
        val cur = i == s.current
        val op = if (cur) 1f else p.dimOpacity
        val hk = (s.kwh[i] / maxK * upH).toFloat()
        val hl = ((litres?.getOrNull(i) ?: 0.0) / maxL * dnH).toFloat()
        if (hk > 0f) {
            val rr = min(r, hk)
            bars.add(Triple(Path().apply {
                addRoundRect(RoundRect(x * u, (base - 2 - hk) * u, (x + bw) * u, (base - 2) * u,
                    CornerRadius(rr * u), CornerRadius(rr * u), CornerRadius.Zero, CornerRadius.Zero))
            }, p.electric, op))
        }
        if (hl > 0f) {
            val rr = min(r, hl)
            bars.add(Triple(Path().apply {
                addRoundRect(RoundRect(x * u, (base + 2) * u, (x + bw) * u, (base + 2 + hl) * u,
                    CornerRadius.Zero, CornerRadius.Zero, CornerRadius(rr * u), CornerRadius(rr * u)))
            }, p.fuel, op))
        }
        if (cur) {
            if (s.kwh[i] > 0) addLbl(grDec(s.kwh[i]), 18f, 600, p.electric, cx, base - hk - 14, 0)
            if (hl > 0f) addLbl(grDec(litres!![i]), 18f, 600, p.fuel, cx, base + hl + 26, 0)
        }
        if (n <= 12 || i % 3 == 0 || i == n - 1) {
            addLbl(s.labels.getOrElse(i) { "" }, 18f, if (cur) 600 else 400, if (cur) p.textPrimary else p.textTertiary, cx, H - 6, 0)
        }
    }

    onDrawBehind {
        val x1 = padL * u; val x2 = (W - padR) * u
        for (y in gridYs) {
            drawLine(grid, Offset(x1, y * u), Offset(x2, y * u), u, pathEffect = dash)
        }
        drawLine(baseC, Offset(x1, base * u), Offset(x2, base * u), u)
        for ((path, color, a) in bars) drawPath(path, color, alpha = a)
        for (l in labels) {
            val w = l.l.size.width
            val left = when (l.align) { 1 -> l.x - w; else -> l.x - w / 2f }
            drawText(l.l, topLeft = Offset(left, l.baseline - l.l.firstBaseline))
        }
    }
}
