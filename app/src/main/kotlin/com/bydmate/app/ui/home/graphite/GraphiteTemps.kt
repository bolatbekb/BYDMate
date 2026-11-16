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
import androidx.compose.ui.graphics.drawscope.clipRect
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
                listOf(0f to p.coldDeep, 1f to p.cold), true,
                outsideNote(data.outsideTemp, lang)?.let { Triple(R.drawable.gr_ic_snow, it, p.cold) },
            )
            TempColumn(
                Modifier.weight(1f), bottom, true, R.drawable.gr_ic_seat, lang.words.cabin,
                data.insideTemp?.let { grDec(it.toDouble(), lang = lang) }, data.insideTemp?.let { tempFraction(TempKind.CABIN, it) },
                listOf(0f to p.cold, 0.55f to p.fuel, 1f to p.heat), false,
                cabinNote(data.acAuto, data.acOn, data.acTemp, lang)?.let { Triple(R.drawable.gr_ic_fan, it, p.textTertiary) },
            )
            TempColumn(
                Modifier.weight(1f), bottom, true, R.drawable.gr_ic_battery, stringResource(R.string.home_gr_battery),
                data.batteryTemp?.let { grDec(it.toDouble(), lang = lang) }, data.batteryTemp?.let { tempFraction(TempKind.BATTERY, it) },
                listOf(0f to p.heat, 1f to p.heat), true, null,
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
    trackAfter: Boolean,
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
                style = grStyle(23f, 450, 0.1f, p.textSecondary, tnum = false),
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
        TempBar(fraction, stops, trackAfter, Modifier.padding(top = px(26)))
        if (note != null) {
            Row(Modifier.padding(top = px(18)), verticalAlignment = Alignment.CenterVertically) {
                GrIcon(note.first, 20f, note.third)
                Spacer(Modifier.width(px(8)))
                Text(
                    note.second, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    style = grStyle(20f, 400, color = p.textTertiary, tnum = false),
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

/**
 * 6 px bar. [trackAfter]: the gradient [stops] span only 0..[fraction] and Track fills the rest;
 * otherwise they span the whole bar. The 16 px knob has a 4 px ring in the card colour.
 */
@Composable
private fun TempBar(fraction: Float?, stops: List<Pair<Float, Color>>, trackAfter: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val known = fraction != null
    val eased by animatedValue(fraction ?: 0f, "tempKnob")
    Canvas(modifier.fillMaxWidth().height(px(6))) {
        val f = eased
        val r = CornerRadius(px(3).toPx())
        drawRoundRect(p.track, size = size, cornerRadius = r)
        val w = if (trackAfter) size.width * (if (known) f else 0f) else size.width
        if (w > 0f) {
            // the stops' gradient from 0 to w, as Brush.horizontalGradient(colorStops, 0, w) makes it
            val brush = ShaderBrush(LinearGradientShader(Offset.Zero, Offset(w, 0f), stops.map { it.second }, stops.map { it.first }))
            clipRect(0f, 0f, w, size.height) { drawRoundRect(brush, size = size, cornerRadius = r) }
        }
        if (known) {
            val c = Offset(size.width * f, size.height / 2)
            drawCircle(p.cardTop, px(12).toPx(), c)
            drawCircle(p.textPrimary, px(8).toPx(), c)
        }
    }
}
