@file:Suppress("TooManyFunctions") // Canvas drawing of one hardware panel: many small private painters by design

package com.bydmate.app.ui.home.onkyo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.ui.home.CassetteGlyph
import com.bydmate.app.ui.home.EngravedIcon
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.spunCap
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.deg
import com.bydmate.app.ui.home.orDash
import com.bydmate.app.ui.home.tapeTime
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val KeyText = Color(0xFFD5D7DB)

/* ---------------------------------------------------------------- transport */

/** PREV/NEXT, wide PLAY, STOP/PAUSE, REC/MENU (the last two are decorative). */
@Composable
internal fun TransportKeys(playing: Boolean, controls: MusicControls, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransportKey("PREV", Glyph.PREV, Modifier.weight(1f), onClick = controls::previous)
            TransportKey("NEXT", Glyph.NEXT, Modifier.weight(1f), onClick = controls::next)
        }
        TransportKey("PLAY", Glyph.PLAY, Modifier.fillMaxWidth().height(58.dp), on = playing, onClick = controls::play)
        Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransportKey("STOP", Glyph.STOP, Modifier.weight(1f), onClick = controls::stop)
            TransportKey("PAUSE", Glyph.PAUSE, Modifier.weight(1f), on = !playing, onClick = controls::pause)
        }
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TransportKey("REC", Glyph.REC, Modifier.weight(1f), red = true)
            TransportKey("MENU", Glyph.MENU, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TransportKey(
    label: String,
    glyph: Glyph,
    modifier: Modifier,
    on: Boolean = false,
    red: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val c = when { red -> Ok.KeyRed; on -> Ok.Green; else -> KeyText }
    Column(
        modifier.fillMaxHeight().keyFace(on || pressed)
            .then(if (onClick != null) Modifier.clickable(src, null, onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        GlyphIcon(glyph, c)
        Label(label, color = c, weight = FontWeight.SemiBold)
    }
}

/* ---------------------------------------------------------------- right of the deck */

/**
 * Six small LED keys (they pick what the amplifier shows: log / track / week in the centre,
 * range or level on the meters, needle sway), the traction balance, three temperature knobs
 * and the big knurled knob with the total range.
 */
@Composable
internal fun DeckRight(
    data: HomeData,
    centre: CentreView,
    onCentre: (CentreView) -> Unit,
    percent: Boolean,
    onPercent: (Boolean) -> Unit,
    swayOn: Boolean,
    onSway: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LedKey("ЖУРНАЛ", centre == CentreView.LOG, Modifier.weight(1f)) { onCentre(CentreView.LOG) }
            LedKey("ТРЕК", centre == CentreView.TRACK, Modifier.weight(1f)) { onCentre(CentreView.TRACK) }
            LedKey("НЕДЕЛЯ", centre == CentreView.WEEK, Modifier.weight(1f)) { onCentre(CentreView.WEEK) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LedKey("ЗАПАС КМ", !percent, Modifier.weight(1f)) { onPercent(false) }
            LedKey("УРОВЕНЬ %", percent, Modifier.weight(1f)) { onPercent(true) }
            LedKey("КАЧАНИЕ", swayOn, Modifier.weight(1f), onSway)
        }
        Label("ПРИВОД ПЕРЕД · ЗАД", Modifier.fillMaxWidth(), size = 10.sp, spacing = 0.5.sp, align = TextAlign.Center)
        BalanceLeds(data.frontShare)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TempKnob("САЛОН", data.insideTemp.deg(), Modifier.weight(1f))
            TempKnob("УЛИЦА", data.outsideTemp.deg(), Modifier.weight(1f))
            TempKnob("БАТАР.", data.batteryTemp.deg(), Modifier.weight(1f))
        }
        Box(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RangeKnob(data.totalRangeKm)
            Column(horizontalAlignment = Alignment.End) {
                Label("ЗАПАС", spacing = 0.5.sp)
                Label(data.totalRangeKm?.let { "$it км" } ?: "—", size = 18.sp, color = Ok.White, weight = FontWeight.ExtraBold, spacing = 0.sp)
                Box(Modifier.size(1.dp, 6.dp))
                Label("TA-2026", size = 13.sp, color = Ok.White, weight = FontWeight.ExtraBold, spacing = 0.sp)
            }
        }
    }
}

/** Seven lamps, front on the left, rear on the right: centre to the traction split, the split in green. */
@Composable
private fun BalanceLeds(frontShare: Float?) {
    val mark = frontShare?.let { ((1f - it) * 6f).roundToInt().coerceIn(0, 6) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)) {
        for (i in 0..6) {
            val color = when {
                mark == null -> Ok.Edge2
                i == mark -> Ok.Green
                i in min(3, mark)..max(3, mark) -> Ok.Amber
                else -> Ok.Edge2
            }
            LedStrip(color != Ok.Edge2, Modifier.size(12.dp, 5.dp), color = color, off = Ok.Edge2, halo = 4.dp)
        }
    }
}

@Composable
private fun TempKnob(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SmallKnob()
        Label(value, size = 13.sp, color = Ok.White, weight = FontWeight.Bold, spacing = 0.sp)
        Label(label, size = 10.sp, spacing = 0.5.sp)
    }
}

/** Knurled knob; the white pip points at the total range on 0..900 km (no pip when unknown). */
@Composable
private fun RangeKnob(totalKm: Int?) {
    Canvas(Modifier.size(74.dp)) {
        val c = center
        val r = 29.dp.toPx()
        drawCircle(Color(0xFF121315), r, c)
        for (i in 0 until 45) {
            drawArc(Color(0xFF1E2023), i * 8f, 4f, useCenter = true, topLeft = c - Offset(r, r), size = Size(2 * r, 2 * r))
        }
        drawCircle(Ok.Edge, r, c, style = Stroke(1.dp.toPx()))
        spunCap(c, r * 0.62f, Color(0xFF17181B), Color(0xFF3E4045))
        if (totalKm != null) {
            val a = (225f - totalKm.coerceIn(0, 900) / 900f * 270f) * PI.toFloat() / 180f
            val d = 20.dp.toPx()
            drawCircle(Ok.White, 2.5.dp.toPx(), c + Offset(d * cos(a), -d * sin(a)))
        }
    }
}

/* ---------------------------------------------------------------- amplifier (lower tier) */

/**
 * Power amplifier: the INPUT SELECTOR keys (the app's tabs) under the brand line, then two VU
 * meters (EV and fuel) around the gold-lettered centre panel.
 */
@Composable
internal fun AmpTier(
    data: HomeData,
    music: NowPlaying,
    centre: CentreView,
    percent: Boolean,
    sway: () -> Float,
    tabs: List<RetroTab>,
    onTab: (RetroTab) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).brushedAluminium()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Brand(14, "STEREO POWER AMPLIFIER")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.border(1.dp, Ok.Edge).padding(horizontal = 8.dp)) {
                        Label("M-2026", size = 11.sp, color = Ok.White, weight = FontWeight.Bold, spacing = 0.sp)
                    }
                    Label("INPUT SELECTOR ▸", size = 11.sp, color = Ok.Text, weight = FontWeight.SemiBold)
                }
            }
            InputSelector(tabs, onTab, Modifier.weight(1f).fillMaxHeight())
        }
        val shape = RoundedCornerShape(4.dp)
        Row(Modifier.weight(1f).fillMaxWidth().clip(shape).border(2.dp, Ok.Shadow, shape)) {
            if (percent) {
                VuMeter("ЭЛЕКТРО", "УРОВЕНЬ · %", PctMarks, data.socPercent, data.socPercent.unit("%"), sway, Modifier.weight(1f).fillMaxHeight())
            } else {
                VuMeter("ЭЛЕКТРО", "ЗАПАС · КМ", EvMarks, data.evRangeKm, data.evRangeKm.unit("км"), sway, Modifier.weight(1f).fillMaxHeight())
            }
            CentrePanel(data, music, centre, Modifier.width(230.dp).fillMaxHeight())
            if (percent) {
                VuMeter("БЕНЗИН", "УРОВЕНЬ · %", PctMarks, data.fuelPercent, data.fuelPercent.unit("%"), sway, Modifier.weight(1f).fillMaxHeight())
            } else {
                VuMeter("БЕНЗИН", "ЗАПАС · КМ", FuelMarks, data.fuelRangeKm, data.fuelRangeKm.unit("км"), sway, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** TAPE (this screen, its lamp lit) and one key per app tab. */
@Composable
private fun InputSelector(tabs: List<RetroTab>, onTab: (RetroTab) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SourceKey("TAPE", CassetteGlyph, current = true, onClick = {}, modifier = Modifier.weight(1f))
        tabs.forEach { tab ->
            SourceKey(tab.label, tab.icon, current = false, onClick = { onTab(tab) }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Source key: an engraved icon over the legend, an amber lamp in the corner lit on the current
 * source, pushed in while held.
 */
@Composable
private fun SourceKey(label: String, icon: ImageVector, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier.fillMaxHeight().keyFace(pressed)
            .clickable(src, null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        LedStrip(current, Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp).size(14.dp, 4.dp), halo = 5.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            EngravedIcon(icon, tone = if (current) Color(0xFF9A9DA3) else Color(0xFF74777D), size = 22.dp)
            Label(
                label.uppercase(), size = 12.sp, color = if (current) Ok.White else KeyText,
                weight = FontWeight.SemiBold, spacing = 0.3.sp, align = TextAlign.Center,
            )
        }
    }
}

private val EvMarks = listOf(0, 25, 50, 75, 100, 125, 150)
private val FuelMarks = listOf(0, 100, 200, 400, 600, 700, 800)
private val PctMarks = listOf(0, 10, 20, 40, 60, 80, 100)

private fun Int?.unit(u: String) = this?.let { "$it $u" } ?: "—"

@Composable
private fun CentrePanel(data: HomeData, music: NowPlaying, view: CentreView, modifier: Modifier) {
    Column(
        modifier.background(Ok.CentreBg)
            .drawBehind {
                val w = 2.dp.toPx()
                drawRect(Ok.Shadow, Offset.Zero, Size(w, size.height))
                drawRect(Ok.Shadow, Offset(size.width - w, 0f), Size(w, size.height))
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().clipToBounds(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (view) {
                CentreView.LOG -> LogLines(data)
                CentreView.TRACK -> TrackLines(music)
                CentreView.WEEK -> WeekBars(data.weekPer100, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Box(Modifier.size(1.dp, 2.dp))
        val ev = data.driveMode == "EV" || data.driveMode == "HEV"
        val fuel = data.driveMode == "HEV"
        DriveLine("ЭЛЕКТРО", ev)
        Box(Modifier.size(1.dp, 2.dp))
        DriveLine("БЕНЗИН", fuel)
    }
}

@Composable
private fun Gold(text: String, modifier: Modifier = Modifier, size: Int = 10, maxLines: Int = 1) {
    Label(text, modifier, size = size.sp, color = Ok.Gold, maxLines = maxLines)
}

private const val LOG_ROWS = 5

@Composable
private fun LogLines(data: HomeData) {
    Gold("ЗА ПЕРИОД ${data.periodKm} · ${data.periodCost}".uppercase())
    // Five fit above the speed line at 604 dp; a sixth would be cut in half.
    data.events.take(LOG_ROWS).forEach { e ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Gold("${e.time} ${e.title}".uppercase(), Modifier.weight(1f))
            Gold(if (e.cost == "—") "—" else "${e.cost} ${data.currency}")
        }
    }
    val power = data.powerKw?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
    Gold("${data.speedKmh.orDash()} КМ/Ч · $power КВТ · ${data.driveMode ?: "—"}")
}

@Composable
private fun TrackLines(music: NowPlaying) {
    val src = music.source?.substringAfterLast('.')?.uppercase()
    Gold("ДЕКА · ${src ?: "НЕТ ПЛЕЕРА"}")
    if (!music.hasTrack) {
        Gold("КАССЕТА НЕ ВСТАВЛЕНА")
        return
    }
    Label(music.artist?.uppercase() ?: "—", size = 13.sp, color = Color(0xFFE2C46A), weight = FontWeight.SemiBold, spacing = 0.5.sp, maxLines = 2)
    Label(music.title.orEmpty(), size = 13.sp, color = Color(0xFFE2C46A), spacing = 0.5.sp, maxLines = 2)
    Gold("${music.positionMs.tapeTime()} / ${music.durationMs.tapeTime()}")
    Gold(
        when {
            music.winding -> "ПЕРЕМОТКА"
            music.isPlaying -> "ВОСПРОИЗВЕДЕНИЕ"
            else -> "ПАУЗА"
        }
    )
}

private val DayNames = arrayOf("ВС", "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ")

/** kWh/100 km for the last seven days as gold bars; days without trips show a dash. */
@Composable
private fun WeekBars(week: List<Double?>, modifier: Modifier) {
    Gold("НЕДЕЛЯ · КВТ·Ч/100 КМ")
    val top = week.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val days = remember {
        val cal = Calendar.getInstance()
        (6 downTo 0).map { back ->
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -back)
            DayNames[c.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.take(7).forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Label(v?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—", size = 10.sp, color = Ok.Gold, spacing = 0.sp)
                    Box(Modifier.size(1.dp, 2.dp))
                    if (v != null) {
                        LedStrip(true, Modifier.fillMaxWidth().fillMaxHeight((v / top).toFloat().coerceIn(0.04f, 1f)), color = Ok.Gold, halo = 3.dp)
                    }
                }
                Box(Modifier.size(1.dp, 3.dp))
                Label(days.getOrElse(i) { "" }, size = 10.sp, color = Ok.Light, spacing = 0.sp)
            }
        }
    }
}

/** "ЭЛЕКТРО ────" lamp line, lit by the drive mode. */
@Composable
private fun DriveLine(label: String, lit: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(label, Modifier.width(54.dp), size = 10.sp)
        LedStrip(lit, Modifier.weight(1f).height(3.dp), color = Ok.Green, off = Ok.Edge2)
    }
}
