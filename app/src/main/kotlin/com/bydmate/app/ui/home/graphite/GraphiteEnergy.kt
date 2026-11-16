package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bydmate.app.R
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import com.bydmate.app.ui.home.common.fs
import com.bydmate.app.ui.home.common.px
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

private const val TABS_PAD = 5f
private const val TABS_TOUCH_MARGIN = 20f // the 54 px block takes touches over 94 px, up to the card top

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
 * Day / week / month. One tap handler over the whole block plus [TABS_TOUCH_MARGIN] above and
 * below (the tabs themselves are only 44 px tall); the tab is picked by x.
 */
@Composable
private fun PeriodTabs(period: ChartPeriod, onPeriod: (ChartPeriod) -> Unit) {
    val p = LocalGr.current
    val widths = remember { IntArray(TAB_LABELS.size) }
    val latest = rememberUpdatedState(onPeriod)
    Box(
        Modifier.wrapContentHeight(Alignment.CenterVertically, unbounded = true)
            .touchMarginVertical(TABS_TOUCH_MARGIN, Modifier.pointerInput(Unit) {
                detectTapGestures { latest.value(TAB_LABELS[tabAt(it.x, px(TABS_PAD).toPx(), widths)].first) }
            }),
    ) {
        InsetBox(22f) {
            Row(Modifier.padding(px(TABS_PAD))) {
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
