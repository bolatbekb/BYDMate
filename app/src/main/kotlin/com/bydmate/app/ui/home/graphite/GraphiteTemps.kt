package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.common.px

/* ---------------------------------------------------------------- «Температуры» */

@Composable
internal fun TempsCard(data: HomeData, pm25: List<Pm25Side>?, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    Column(modifier.grCard(p)) {
        // Without the PM2.5 line the columns keep the whole card; with it they end 16 above the line.
        val bottom = if (pm25 != null) 16 else 30
        Row(Modifier.fillMaxWidth().weight(1f)) {
            TempColumn(
                Modifier.weight(1f), bottom, false, R.drawable.gr_ic_tree, lang.words.outside,
                data.outsideTemp?.let { grDec(it.toDouble(), lang = lang) }, data.outsideTemp?.let { tempFraction(TempKind.OUTSIDE, it) },
                listOf(0f to p.coldDeep, 1f to p.cold),
                outsideNote(data.outsideTemp, lang)?.let { Triple(R.drawable.gr_ic_snow, it, p.cold) },
            )
            TempColumn(
                Modifier.weight(1f), bottom, true, R.drawable.gr_ic_seat, lang.words.cabin,
                data.insideTemp?.let { grDec(it.toDouble(), lang = lang) }, data.insideTemp?.let { tempFraction(TempKind.CABIN, it) },
                listOf(0f to p.cold, 0.55f to p.fuel, 1f to p.heat),
                cabinNote(data.acAuto, data.acOn, data.acTemp, lang)?.let { Triple(R.drawable.gr_ic_fan, it, p.textTertiary) },
            )
            TempColumn(
                Modifier.weight(1f), bottom, true, R.drawable.gr_ic_battery, stringResource(R.string.home_gr_battery),
                data.batteryTemp?.let { grDec(it.toDouble(), lang = lang) }, data.batteryTemp?.let { tempFraction(TempKind.BATTERY, it) },
                listOf(0f to p.heat, 1f to p.heat), null,
            )
        }
        if (pm25 != null) Pm25Line(pm25, Modifier.padding(start = px(30), end = px(30), bottom = px(24)))
    }
}

@Composable
private fun TempColumn(
    modifier: Modifier,
    bottom: Int,
    divider: Boolean,
    icon: Int,
    title: String,
    value: String?,
    fraction: Float?,
    stops: List<Pair<Float, Color>>,
    note: Triple<Int, String, Color>?,
) {
    val p = LocalGr.current
    Column(
        modifier
            .drawBehind {
                if (divider) {
                    val u = px(1).toPx()
                    drawRect(p.divider, Offset(0f, px(30).toPx()), Size(u, size.height - px(30 + bottom).toPx()))
                }
            }
            .padding(start = px(30), top = px(30), end = px(30), bottom = px(bottom)),
    ) {
        Row(Modifier.fillMaxWidth().height(px(34)), verticalAlignment = Alignment.CenterVertically) {
            GrIcon(icon, 24f, p.textTertiary)
            Spacer(Modifier.width(px(12)))
            Text(
                title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = grStyle(24f, 450, 0.1f, p.textSecondary, tnum = false),
            )
        }
        Row(Modifier.padding(top = px(22)).fillMaxWidth()) {
            FitText(
                value ?: "—", grStyle(72f, 250, -3f, p.textPrimary), 72f, 52f,
                if (value != null) Modifier.weight(1f, fill = false) else Modifier,
            )
            if (value != null) {
                Text(
                    "°C", maxLines = 1, softWrap = false,
                    style = grStyle(34f, 400, color = p.textSecondary, tnum = false),
                    modifier = Modifier.padding(top = px(10)),
                )
            }
        }
        TempBar(fraction, stops, Modifier.padding(top = px(26)))
        if (note != null) {
            Row(Modifier.padding(top = px(18)), verticalAlignment = Alignment.CenterVertically) {
                GrIcon(note.first, 20f, note.third)
                Spacer(Modifier.width(px(8)))
                Text(
                    note.second, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    style = grStyle(19f, 400, color = p.textTertiary, tnum = false),
                )
            }
        }
    }
}

/**
 * The PM2.5 line under the temperatures (InsetBox 18, padding 12 × 20): fan + «PM2.5», then
 * «Салон 12 отлично» / «Снаружи 35 хорошо», µg/m³, the word coloured by its grade.
 */
@Composable
private fun Pm25Line(sides: List<Pm25Side>, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    InsetBox(18f, modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = px(20), vertical = px(12)), verticalAlignment = Alignment.CenterVertically) {
            GrIcon(R.drawable.gr_ic_fan, 22f, p.textTertiary)
            Spacer(Modifier.width(px(10)))
            Text("PM2.5", maxLines = 1, softWrap = false, style = grStyle(19f, 400, color = p.textSecondary, tnum = false))
            Spacer(Modifier.width(px(28)))
            FitText(
                buildAnnotatedString {
                    sides.forEachIndexed { i, side ->
                        if (i > 0) append("   ")
                        append(side.label + " ")
                        withStyle(SpanStyle(color = p.textPrimary, fontWeight = FontWeight(500))) { append(grInt(side.value, lang)) }
                        withStyle(SpanStyle(color = p.tone(side.tone))) { append(" " + side.quality) }
                    }
                },
                grStyle(19f, 400, color = p.textSecondary, tnum = false), 19f, 15f, Modifier.weight(1f),
            )
        }
    }
}

private fun GrPalette.tone(t: Tone): Color = when (t) {
    Tone.ELECTRIC -> electric
    Tone.FUEL -> fuel
    Tone.HEAT -> heat
}

/** Width of the fill of a bar [width] wide: nothing for an unknown value, else the clamped [fraction] of it. */
internal fun tempFillWidth(width: Float, fraction: Float?): Float =
    if (fraction == null) 0f else width * fraction.coerceIn(0f, 1f)

/**
 * 6 px scale without a knob (it only shows a value, it is not a slider): the whole bar is Track, the fill
 * runs from 0 to the value with round ends. The gradient [stops] span the WHOLE bar and are clipped at the
 * fill end, so one colour always means one temperature. Unknown value: the track only.
 */
@Composable
private fun TempBar(fraction: Float?, stops: List<Pair<Float, Color>>, modifier: Modifier) {
    val p = LocalGr.current
    val eased by animatedValue(fraction, "tempFill")
    Canvas(modifier.fillMaxWidth().height(px(6))) {
        val r = CornerRadius(px(3).toPx())
        drawRoundRect(p.track, size = size, cornerRadius = r)
        val w = tempFillWidth(size.width, if (fraction == null) null else eased)
        if (w > 0f) {
            val brush = ShaderBrush(LinearGradientShader(Offset.Zero, Offset(size.width, 0f), stops.map { it.second }, stops.map { it.first }))
            drawRoundRect(brush, size = Size(w, size.height), cornerRadius = CornerRadius(minOf(r.x, w / 2), minOf(r.y, w / 2)))
        }
    }
}
