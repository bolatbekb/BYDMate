package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.common.fs
import com.bydmate.app.ui.home.common.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ---------------------------------------------------------------- «Общий запас хода» */

@Composable
fun RangeCard(data: HomeData, sohPercent: Float?, tankL: Double?, capacityKwh: Double?, compact: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val w = lang.words
    val total = data.totalRangeKm?.takeIf { it > 0 }
    // Compact: the card is 76 shorter (the PM2.5 line below); only the gaps shrink.
    val g = if (compact) RangeGaps.Compact else RangeGaps.Regular
    CardBody(modifier, bottom = g.bottom) {
        CardHeader(R.drawable.gr_ic_gauge, stringResource(R.string.home_gr_range_title)) {
            data.odometerKm?.let {
                Text(
                    stringResource(R.string.home_gr_odometer, "${grInt(it, lang)} ${w.km}"), maxLines = 1, softWrap = false,
                    style = grStyle(21f, 400, color = p.textTertiary, tnum = false),
                )
            }
        }
        Row(Modifier.padding(top = px(g.number)).height(px(148))) {
            Text(
                total?.let { grInt(it, lang) } ?: "—", maxLines = 1, softWrap = false,
                style = grStyle(148f, 250, -6f, p.textPrimary).copy(lineHeight = fs(148f)),
                modifier = Modifier.alignByBaseline(),
            )
            if (total != null) {
                Spacer(Modifier.width(px(12)))
                Text(
                    w.km, maxLines = 1, softWrap = false,
                    style = grStyle(40f, 300, color = p.textSecondary, tnum = false),
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        RangeBar(total, data.evRangeKm, Modifier.padding(top = px(g.bar)))
        if (total != null) RangeScaleRow(rangeScale(total).ticks, total, Modifier.padding(top = px(g.scale)))
        Box(Modifier.padding(top = px(g.divider)).fillMaxWidth().height(px(1)).background(p.divider))
        // A car with a tank keeps room for the rpm line under both gauges.
        val rpmSlot = data.fuelPercent != null
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = px(g.gauges))) {
            RangeGauge(
                Modifier.weight(1f), R.drawable.gr_ic_battery, R.drawable.gr_ic_bolt, stringResource(R.string.home_gr_battery),
                data.socPercent,
                p.electric,
                kmLine(data.evRangeKm, null, lang),
                shimmer = data.chargingStatus == 2,
                rpmSlot = rpmSlot,
            )
            Box(Modifier.width(px(26)))
            Box(Modifier.width(px(1)).fillMaxHeight().background(p.divider))
            Box(Modifier.width(px(26)))
            RangeGauge(
                Modifier.weight(1f), R.drawable.gr_ic_fuel, R.drawable.gr_ic_fuel, stringResource(R.string.home_gr_fuel),
                data.fuelPercent,
                p.fuel,
                kmLine(data.fuelRangeKm, litresFromPercent(data.fuelPercent, tankL)?.let { "($it ${w.litre})" }, lang),
                rpm = data.engineRpm,
                rpmSlot = rpmSlot,
            )
        }
        RangeFooter(data, sohPercent, capacityKwh, g.footerPad, Modifier.padding(top = px(g.footer)))
    }
}

/** The range card's vertical gaps, mockup px; Compact takes 78 off (the card loses 76). */
private data class RangeGaps(
    val number: Int, val bar: Int, val scale: Int, val divider: Int, val gauges: Int, val footer: Int,
    val footerPad: Int, val bottom: Int,
) {
    companion object {
        val Regular = RangeGaps(number = 10, bar = 26, scale = 12, divider = 26, gauges = 24, footer = 24, footerPad = 18, bottom = 30)
        val Compact = RangeGaps(number = 0, bar = 16, scale = 8, divider = 14, gauges = 14, footer = 12, footerPad = 12, bottom = 22)
    }
}

private fun kmLine(km: Int?, tail: String?, lang: GrLang): Pair<String, String?> =
    (if (km == null) "—" else "${grInt(km, lang)} ${lang.words.km}") to tail

/** EV then fuel share of [total] with a 3 px gap, rounded ends; the split eases to new values. */
@Composable
private fun RangeBar(total: Int?, ev: Int?, modifier: Modifier) {
    val p = LocalGr.current
    val evKm = (ev ?: 0).coerceAtLeast(0)
    val fuelKm = if (total == null) 0 else (total - evKm).coerceAtLeast(0)
    val share by animatedValue(if (evKm + fuelKm > 0) evKm.toFloat() / (evKm + fuelKm) else 0f, "rangeSplit")
    val known = total != null
    Canvas(modifier.fillMaxWidth().height(px(14)).clip(RoundedCornerShape(px(7)))) {
        if (!known) {
            drawRect(p.track)
            return@Canvas
        }
        val f = share
        val gap = if (f > 0.001f && f < 0.999f) px(3).toPx() else 0f
        val evW = (size.width - gap) * f
        if (evW > 0f) drawRect(p.electric, size = Size(evW, size.height))
        val fx = evW + gap
        if (fx < size.width) {
            drawRect(
                Brush.horizontalGradient(listOf(p.fuel, p.fuelDeep), startX = fx, endX = size.width),
                topLeft = Offset(fx, 0f), size = Size(size.width - fx, size.height),
            )
        }
    }
}

/** Tick labels at value/total of the width; the first hugs the left edge, the last the right. */
@Composable
private fun RangeScaleRow(ticks: List<Int>, total: Int, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val style = grStyle(17f, 400, color = p.textTertiary)
    Layout(
        content = { ticks.forEach { Text(grInt(it, lang), maxLines = 1, softWrap = false, style = style) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, c ->
        val placeables = measurables.map { it.measure(c.copy(minWidth = 0, minHeight = 0)) }
        val w = c.maxWidth
        val h = placeables.maxOfOrNull { it.height } ?: 0
        layout(w, h) {
            placeables.forEachIndexed { i, pl ->
                val x = when (i) {
                    0 -> 0
                    placeables.lastIndex -> w - pl.width
                    else -> (ticks[i].toFloat() / total * w - pl.width / 2f).toInt().coerceIn(0, w - pl.width)
                }
                pl.place(IntOffset(x, 0))
            }
        }
    }
}

@Composable
private fun RangeGauge(
    modifier: Modifier,
    labelIcon: Int,
    centerIcon: Int,
    label: String,
    percent: Int?,
    accent: Color,
    line: Pair<String, String?>,
    shimmer: Boolean = false,
    rpm: Int? = null,
    rpmSlot: Boolean = false,
) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val ice = stringResource(R.string.home_gr_ice)
    val running = rpm?.takeIf { it > 0 }
    val engineOn = running != null
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GaugeDial(percent, accent, centerIcon, shimmer, running)
        Spacer(Modifier.width(px(18)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GrIcon(labelIcon, 22f, accent)
                Spacer(Modifier.width(px(12)))
                // Line boxes here and in the footer: Chinese lines must not grow the card (see FitText).
                FitText(label, grStyle(21f, 450, 0.1f, p.textSecondary, tnum = false), 21f, 21f, Modifier, lineBox = true)
            }
            Text(
                percentText(percent, p), maxLines = 1, softWrap = false,
                style = grStyle(58f, 300, -2f, p.textPrimary).copy(lineHeight = fs(58f)),
                modifier = Modifier.padding(top = px(12)),
            )
            FitText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = p.textPrimary, fontWeight = FontWeight(500))) { append(line.first) }
                    line.second?.let { append(" $it") }
                },
                grStyle(22f, 400, color = p.textSecondary, tnum = false), 22f, 18f,
                Modifier.padding(top = px(10)), lineBox = true,
            )
            // The rpm line has a fixed slot (in both gauges), so the engine starting moves nothing.
            if (rpmSlot || engineOn) {
                Box(Modifier.padding(top = px(6)).height(px(RPM_LINE_H)).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (running != null) {
                        FitText(
                            buildAnnotatedString {
                                append("$ice ")
                                withStyle(SpanStyle(fontWeight = FontWeight(500))) { append("${grInt(running, lang)} ${lang.words.rpm}") }
                            },
                            grStyle(19f, 400, color = p.fuel, tnum = false), 19f, 15f, Modifier,
                        )
                    }
                }
            }
        }
    }
}

private fun percentText(percent: Int?, p: GrPalette): AnnotatedString = buildAnnotatedString {
    append(percent?.toString() ?: "—")
    if (percent != null) {
        withStyle(SpanStyle(fontSize = fs(26f), color = p.textSecondary, fontWeight = FontWeight(400), letterSpacing = fs(0f))) {
            append("%")
        }
    }
}

/**
 * viewBox 150 → 164 box: arc r 62 / stroke 10 from -135° to +135°, 21 ticks r 44–49, hub r 30.
 * The fill eases to a new value; [shimmer] (charging) runs a highlight along it every 2.4 s;
 * [rpm] (engine running) adds an inner ring r 39 / stroke 4, rpm / 6 000.
 */
@Composable
private fun GaugeDial(percent: Int?, accent: Color, centerIcon: Int, shimmer: Boolean, rpm: Int?) {
    val p = LocalGr.current
    val frac by animatedValue(((percent ?: 0) / 100f).coerceIn(0f, 1f), "gauge")
    val ring = rpm != null
    val rpmFrac by animatedValue(rpmFraction(rpm), "rpm")
    // The transition exists only while charging: nothing ticks at rest.
    val phase: State<Float>? = if (shimmer) {
        rememberInfiniteTransition(label = "charge").animateFloat(
            0f, 1f, infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing)), label = "shimmer",
        )
    } else null
    Box(Modifier.size(px(164)), contentAlignment = Alignment.Center) {
        Box(
            // Its own layer: the shimmer and the rpm ring redraw only this box.
            Modifier.size(px(164)).graphicsLayer().drawWithCache {
                val k = size.width / 150f
                val c = Offset(75f * k, 75f * k)
                val r = 62f * k
                val sw = 10f * k
                val tl = Offset(c.x - r, c.y - r)
                val arcSize = Size(2 * r, 2 * r)
                val glow = blurPaint(accent.copy(alpha = 0.53f), 6f * px(1).toPx()).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = sw
                    strokeCap = android.graphics.Paint.Cap.ROUND
                }
                val oval = android.graphics.RectF(tl.x, tl.y, tl.x + 2 * r, tl.y + 2 * r)
                val tick = p.textPrimary.copy(alpha = 0.18f)
                val arcStroke = Stroke(sw, cap = StrokeCap.Round)
                val hubStroke = Stroke(k)
                val hub = p.textPrimary.copy(alpha = 0.06f)
                val tickLines = Array(21) { i ->
                    val a = (135.0 + 270.0 * i / 20) * PI / 180
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    Offset(c.x + 44f * k * ca, c.y + 44f * k * sa) to Offset(c.x + 49f * k * ca, c.y + 49f * k * sa)
                }
                val ri = 39f * k
                val tlInner = Offset(c.x - ri, c.y - ri)
                val innerSize = Size(2 * ri, 2 * ri)
                val innerStroke = Stroke(4f * k, cap = StrokeCap.Round)
                // A 30° white band at 180° of the sweep, turned to the highlight's angle when drawn.
                val clear = Color.White.copy(alpha = 0f)
                val band = Brush.sweepGradient(
                    0f to clear, 165f / 360f to clear, 0.5f to Color.White.copy(alpha = 0.35f), 195f / 360f to clear, 1f to clear,
                    center = c,
                )
                onDrawBehind {
                    val f = frac
                    drawArc(p.track, 135f, 270f, false, tl, arcSize, style = arcStroke)
                    if (f > 0f) {
                        drawIntoCanvas { it.nativeCanvas.drawArc(oval, 135f, 270f * f, false, glow) }
                        drawArc(accent, 135f, 270f * f, false, tl, arcSize, style = arcStroke)
                        val ph = phase?.value
                        if (ph != null) {
                            // The band runs from 15° before the fill to 15° past its end.
                            val head = 120f + (270f * f + 30f) * ph
                            val turn = head - 180f
                            rotate(turn, c) { drawArc(band, 135f - turn, 270f * f, false, tl, arcSize, style = arcStroke) }
                        }
                    }
                    for ((from, to) in tickLines) drawLine(tick, from, to, strokeWidth = 1.2f * k)
                    if (ring) {
                        val rf = rpmFrac
                        drawArc(p.track, 135f, 270f, false, tlInner, innerSize, style = innerStroke)
                        if (rf > 0f) drawArc(p.fuel, 135f, 270f * rf, false, tlInner, innerSize, style = innerStroke)
                    }
                    drawCircle(p.inset, 30f * k, c)
                    drawCircle(hub, 30f * k, c, style = hubStroke)
                }
            },
        )
        GrIcon(centerIcon, 32f, accent)
    }
}

private const val SHIMMER_MS = 2_400
private const val RPM_LINE_H = 19f * 1.2f

@Composable
private fun RangeFooter(data: HomeData, sohPercent: Float?, capacityKwh: Double?, padV: Int, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val eta = ChargeEta.minutesToFull(
        data.chargingStatus == 2, data.socPercent, data.liveCapacityKwh ?: capacityKwh, data.batteryPowerKw ?: data.powerKw,
    )
    val volts = stringResource(R.string.home_gr_footer_12v) + " "
    val insulation = stringResource(R.string.home_gr_footer_insulation) + " "
    val toFull = stringResource(R.string.home_gr_footer_to_full) + " "
    val cells = buildList<Triple<Int, String, String>> {
        sohPercent?.takeIf { it.isFinite() }?.let { add(Triple(R.drawable.gr_ic_battery, "SOH ", sohText(it))) }
        data.voltage12v?.takeIf { it.isFinite() }?.let { add(Triple(R.drawable.gr_ic_bolt, volts, voltageText(it, lang))) }
        data.insulationKohm?.let { add(Triple(R.drawable.gr_ic_power, insulation, insulationText(it, lang))) }
        if (eta != null) add(Triple(R.drawable.gr_ic_plug, toFull, grDuration(eta, lang)))
    }
    if (cells.isEmpty()) return
    InsetBox(22f, modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = px(20), vertical = px(padV))) {
            cells.forEachIndexed { i, (icon, label, value) ->
                // Columns are sized by their text length (like the mockup's auto columns), so the
                // longest cell gets the most room; FitText shrinks before anything ellipsizes.
                Row(
                    Modifier.weight((label.length + value.length + 5).toFloat()).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (i > 0) {
                        Box(Modifier.width(px(1)).fillMaxHeight().background(p.divider))
                        Spacer(Modifier.width(px(18)))
                    }
                    GrIcon(icon, 22f, p.textTertiary)
                    Spacer(Modifier.width(px(10)))
                    FitText(
                        buildAnnotatedString {
                            append(label)
                            withStyle(SpanStyle(color = p.textPrimary, fontWeight = FontWeight(500))) { append(value) }
                        },
                        grStyle(19f, 400, color = p.textTertiary, tnum = false), 19f, 15f, Modifier.weight(1f),
                        lineBox = true,
                    )
                }
            }
        }
    }
}
