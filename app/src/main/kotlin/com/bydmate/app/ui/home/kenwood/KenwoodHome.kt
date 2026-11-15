@file:Suppress("TooManyFunctions") // Canvas drawing of one hardware panel: many small private painters by design

package com.bydmate.app.ui.home.kenwood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.CassetteDoor
import com.bydmate.app.ui.home.CassetteGlyph
import com.bydmate.app.ui.home.EngravedIcon
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.counterText
import com.bydmate.app.ui.home.labelLine
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.deg
import com.bydmate.app.ui.home.orDash
import java.util.Calendar
import java.util.Locale

private val DesignW = 1280.dp
private val DesignH = 604.dp

/**
 * Retro home screen styled as a three-tier black hi-fi stack: cassette deck with a fluorescent
 * display and range knob, graphic equalizer with spectrum analyzer and day log, and a power
 * amplifier with red range meters and the INPUT SELECTOR keys that switch the app's tabs.
 * Drawn at 1280 x 604 dp (the car's content area) and scaled uniformly to fit.
 *
 * @param data car readings; nulls show as dashes.
 * @param music the active media session, shown on the tape and display.
 * @param controls transport commands sent by the deck keys.
 * @param tabs the app's other screens, one selector key each; the lit TAPE key is this screen.
 * @param onTab opens a tab, as the bottom bar would.
 */
@Composable
fun KenwoodHome(
    data: HomeData,
    music: NowPlaying,
    controls: MusicControls,
    tabs: List<RetroTab> = emptyList(),
    onTab: (RetroTab) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Kw.bg), contentAlignment = Alignment.Center) {
        val fitW = if (maxWidth.value.isFinite()) maxWidth / DesignW else 1f
        val fitH = if (maxHeight.value.isFinite()) maxHeight / DesignH else 1f
        val scale = minOf(fitW, fitH)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            Column(
                Modifier
                    .requiredSize(DesignW, DesignH)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .background(Kw.bg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DeckTier(data, music, controls, Modifier.fillMaxWidth().height(264.dp))
                EqualizerTier(data, music, Modifier.fillMaxWidth().height(138.dp))
                AmplifierTier(data, music, tabs, onTab, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- tier 1: cassette deck

@Composable
private fun DeckTier(data: HomeData, music: NowPlaying, controls: MusicControls, modifier: Modifier) {
    Row(
        modifier.brushedTier().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(150.dp).fillMaxHeight()) {
            Logo(19f)
            Spacer(Modifier.height(12.dp))
            DeckKey(Glyph.EJECT, "Извлечь", controls::stop, Modifier.size(72.dp, 30.dp), glyphW = 16.dp, glyphH = 12.dp)
            Spacer(Modifier.weight(1f))
            PowerPlate(11f, 76.dp, 38.dp, "▢ ON · ▢ OFF", Modifier.width(90.dp))
        }
        DeckWell(music, Modifier.width(380.dp).fillMaxHeight())
        DisplayColumn(data, music, controls, Modifier.weight(1f).fillMaxHeight())
        RangeColumn(data, Modifier.width(230.dp).fillMaxHeight())
    }
}

@Composable
private fun DeckWell(music: NowPlaying, modifier: Modifier) {
    Box(
        modifier
            .window(Color(0xFF0D0E10))
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent), endY = 13.dp.toPx()),
                )
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
            val spin = rememberRunningClock(music.isPlaying, if (music.winding) 900f else 360f / 2.2f)
            CassetteDoor(
                label = music.labelLine(),
                progress = music.progress,
                hubAngle = { spin.value },
                modifier = Modifier.padding(top = 2.dp).size(316.dp, 196.dp).align(Alignment.CenterHorizontally),
                frame = Kw.edge,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2E3034)))
            Row(Modifier.fillMaxWidth().padding(top = 3.dp, start = 14.dp, end = 14.dp), verticalAlignment = Alignment.Bottom) {
                KText("HYBRID", 18f, color = Color(0xFFD0D2D6), family = LogoFont, weight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                KText(
                    "STEREO DRIVE DECK KX-2026", 10f,
                    color = Kw.grey, family = LogoFont, align = TextAlign.End,
                )
            }
        }
        Screw(Modifier.align(Alignment.TopStart).padding(6.dp))
        Screw(Modifier.align(Alignment.TopEnd).padding(6.dp))
        Screw(Modifier.align(Alignment.BottomStart).padding(6.dp))
        Screw(Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

@Composable
private fun DisplayColumn(data: HomeData, music: NowPlaying, controls: MusicControls, modifier: Modifier) {
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .window()
                .drawBehind { drawRect(Color(0xFF96C8FF).copy(alpha = 0.04f)) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlyphIcon(Glyph.STOP, if (music.isPlaying) Kw.vfdOff else Kw.vfd, 14.dp, 12.dp)
                GlyphIcon(Glyph.PLAY, if (music.isPlaying) Kw.vfd else Kw.vfdOff, 14.dp, 12.dp)
                GlyphIcon(Glyph.PAUSE, Kw.vfdOff, 14.dp, 12.dp)
                KText("REC · LOOP · RAND", 11f, Modifier.weight(1f), color = Kw.vfdOff)
                KText(
                    music.counterText(), 32f, color = Kw.vfd, family = VfdFont,
                    weight = FontWeight.Bold, italic = true, glow = Color(0xB3AACDFF),
                )
            }
            VfdMeter("ЗАР", data.socPercent)
            VfdMeter("БАК", data.fuelPercent)
            Row(
                Modifier.fillMaxWidth().padding(start = 54.dp, end = 66.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf("0", "20", "40", "60", "80", "100").forEach {
                    KText(it, 11f, color = if (it == "80" || it == "100") Kw.vfdRed else Kw.vfdDim)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        StatusStrip(data)
        Spacer(Modifier.weight(1f))
        TransportKeys(music, controls)
    }
}

/** One labelled fluorescent meter row: chip, 30 cells, percent. */
@Composable
private fun VfdMeter(label: String, percent: Int?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        KText(
            label, 12f, Modifier.width(46.dp).background(Kw.vfd).padding(horizontal = 4.dp),
            color = Kw.vfdBg, weight = FontWeight.SemiBold, align = TextAlign.Center,
        )
        Spacer(Modifier.width(8.dp))
        VfdCells(percent, Modifier.weight(1f).height(8.dp))
        Spacer(Modifier.width(8.dp))
        KText(
            percent?.let { "$it%" } ?: "—", 16f, Modifier.width(58.dp),
            color = Kw.vfd, family = VfdFont, italic = true, align = TextAlign.End,
        )
    }
}

/** Takes the place of the mockup's navigation row: the period totals and the drive mode. */
@Composable
private fun StatusStrip(data: HomeData) {
    Row(
        Modifier.fillMaxWidth().height(24.dp).background(Kw.key).border(1.dp, Kw.edge).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KText("ПЕРИОД · ${data.periodKm} · ${data.periodCost}", 11f, Modifier.weight(1f), spacing = 1f)
        KText("РЕЖИМ ", 11f, spacing = 1f)
        // EV/HEV, then the drive profile when the car has that profile (ECO / NORMAL / SPORT).
        val profile = data.driveProfile?.takeIf { it in data.driveProfiles }
        val mode = listOfNotNull(data.driveMode, profile).joinToString(" · ").ifEmpty { "—" }
        KText(mode, 11f, color = Kw.vfd, weight = FontWeight.SemiBold, spacing = 1f)
    }
}

@Composable
private fun TransportKeys(music: NowPlaying, controls: MusicControls) {
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        DeckKey(Glyph.PREV, "Назад", controls::previous, Modifier.weight(1f).fillMaxHeight())
        DeckKey(
            if (music.isPlaying) Glyph.PAUSE else Glyph.PLAY, "Пуск/пауза", controls::playPause,
            Modifier.weight(1.6f).fillMaxHeight(), on = music.isPlaying,
        )
        DeckKey(Glyph.NEXT, "Вперёд", controls::next, Modifier.weight(1f).fillMaxHeight())
        DeckKey(Glyph.STOP, "Стоп", controls::stop, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun RangeColumn(data: HomeData, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        KText("ЗАПАС ХОДА", 11f, spacing = 2f)
        Spacer(Modifier.height(2.dp))
        KnurledKnob(data.totalRangeKm, 900, diameter = 112.dp)
        KText("${data.totalRangeKm.orDash()} км", 22f, color = Kw.vfd, family = VfdFont, italic = true)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundButton("САЛОН", data.insideTemp.deg(), Modifier.weight(1f))
            RoundButton("УЛИЦА", data.outsideTemp.deg(), Modifier.weight(1f))
            RoundButton("БАТАР.", data.batteryTemp.deg(), Modifier.weight(1f))
            RoundButton("КМ/Ч", data.speedKmh.orDash(), Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- tier 2: equalizer

@Composable
private fun EqualizerTier(data: HomeData, music: NowPlaying, modifier: Modifier) {
    Row(
        modifier.brushedTier().padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WeekSliders(data.weekPer100, Modifier.width(380.dp).fillMaxHeight())
        SpectrumPanel(music.isPlaying, Modifier.weight(1f).fillMaxHeight())
        EventsPanel(data, Modifier.width(400.dp).fillMaxHeight())
    }
}

private val dayNames = listOf("вс", "пн", "вт", "ср", "чт", "пт", "сб")

@Composable
private fun WeekSliders(week: List<Double?>, modifier: Modifier) {
    val days = remember {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        List(7) { i -> dayNames[(today - (6 - i) + 14) % 7] }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Logo(13f)
            Spacer(Modifier.weight(1f))
            KText("РАСХОД ПО ДНЯМ · кВт·ч/100", 10f, spacing = 1f)
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (i in 0 until 7) {
                val v = week.getOrNull(i)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    KText(
                        v?.let { String.format(Locale.US, "%.1f", it).replace('.', ',') } ?: "—",
                        10f, color = Kw.grey,
                    )
                    Spacer(Modifier.height(3.dp))
                    EqSlider(v?.let { ((it - 10.0) / 10.0).toFloat() }, Modifier.weight(1f))
                    Spacer(Modifier.height(3.dp))
                    KText(days[i], 10f)
                }
            }
        }
    }
}

/** Bar heights of the mockup's analyzer, percent of the window. */
private val spectrumHeights = intArrayOf(70, 88, 95, 80, 66, 74, 90, 62, 55, 68, 72, 58, 46, 52, 40, 34, 28, 22)

/** Fake analyzer: there is no audio FFT, the bars dance on keyframes while music plays. */
@Composable
private fun SpectrumPanel(playing: Boolean, modifier: Modifier) {
    val clock = rememberRunningClock(playing)
    Column(modifier.window().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).border(1.dp, Kw.vfdOff).padding(5.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val n = spectrumHeights.size
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                val t = clock.value
                val color = Kw.vfd.copy(alpha = 0.9f)
                for (i in 0 until n) {
                    val f = if (playing) {
                        val delay = (i * 37 % 9) / 10f
                        val dur = 0.55f + (i * 53 % 7) / 10f
                        spectrumHeights[i] / 100f * eqScale(t, delay, dur)
                    } else 0.05f
                    drawSegmentBar(i * (w + gap), w, size.height * f, color)
                }
            }
        }
        KText(
            "SPECTRUM ANALYZER", 10f, Modifier.fillMaxWidth().padding(top = 2.dp),
            color = Kw.vfdDim, spacing = 2f, align = TextAlign.Center,
        )
    }
}

@Composable
private fun EventsPanel(data: HomeData, modifier: Modifier) {
    val todayCost = data.events
        .filter { ':' in it.time }
        .mapNotNull { it.cost.toDoubleOrNull() }
        .takeIf { it.isNotEmpty() }
        ?.sum()
    Column(
        modifier.window().clipToBounds().padding(horizontal = 14.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        val head = "СОБЫТИЯ ДНЯ" + (todayCost?.let { String.format(Locale.getDefault(), " · %.0f %s", it, data.currency) } ?: "")
        KText(head, 10f, color = Kw.vfdDim, spacing = 2f)
        if (data.events.isEmpty()) {
            KText("Нет поездок", 13f, color = Kw.vfdDim)
        }
        data.events.forEach { e ->
            Row(Modifier.fillMaxWidth()) {
                KText(e.time, 13f, Modifier.width(46.dp), color = Kw.vfdDim, family = VfdFont, italic = true)
                Spacer(Modifier.width(8.dp))
                KText(e.title, 13f, Modifier.weight(1f), color = Kw.vfd)
                Spacer(Modifier.width(8.dp))
                KText(e.cost, 13f, color = Kw.vfd, family = VfdFont, italic = true)
            }
        }
    }
}

// ---------------------------------------------------------------- tier 3: power amplifier

@Composable
private fun AmplifierTier(
    data: HomeData,
    music: NowPlaying,
    tabs: List<RetroTab>,
    onTab: (RetroTab) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.brushedTier().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(200.dp).fillMaxHeight()) {
            Logo(13f)
            Spacer(Modifier.height(4.dp))
            KText("HYBRID POWER UNIT KM-2026", 10f, color = Kw.grey, spacing = 1f)
            Spacer(Modifier.weight(1f))
            KText("INPUT SELECTOR", 11f, spacing = 2f)
            Spacer(Modifier.height(2.dp))
            KText("◀ TAPE · SOURCE ▶", 10f, color = Kw.grey, spacing = 1f)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                Modifier.fillMaxWidth().weight(1f).window().padding(horizontal = 14.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                PowerMeter("ЭЛЕКТРО", data.evRangeKm)
                PowerMeter("БЕНЗИН", data.fuelRangeKm)
                Row(
                    Modifier.fillMaxWidth().padding(start = 100.dp, end = 90.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("0", "200", "400", "600", "800 КМ").forEach { KText(it, 10f, color = Kw.grey) }
                }
            }
            InputSelector(tabs, onTab, Modifier.fillMaxWidth().height(60.dp))
        }
        AmpLegend(music, Modifier.width(170.dp).fillMaxHeight())
    }
}

/** The amplifier's source keys: TAPE (this screen, lit) and one key per app tab. */
@Composable
private fun InputSelector(tabs: List<RetroTab>, onTab: (RetroTab) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SourceKey("TAPE", CassetteGlyph, current = true, onClick = {}, modifier = Modifier.weight(1f).fillMaxHeight())
        tabs.forEach { tab ->
            SourceKey(tab.label, tab.icon, current = false, onClick = { onTab(tab) }, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * Square source key: an engraved icon over the legend, and an LED slot in the corner that glows
 * on the current source.
 */
@Composable
private fun SourceKey(label: String, icon: ImageVector, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lit = current || pressed
    Box(
        modifier
            .keyFace(pressed)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
                .size(14.dp, 4.dp)
                .drawBehind {
                    if (current) {
                        val g = 3.dp.toPx()
                        drawRect(Kw.vfd.copy(alpha = 0.3f), Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g))
                    }
                    drawRect(if (current) Kw.vfd else Kw.vfdOff)
                },
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            EngravedIcon(icon, tone = if (lit) Kw.vfdDim else Color(0xFF70747B), size = 22.dp)
            KText(
                label.uppercase(), 12f, color = if (lit) Kw.vfd else Kw.text,
                weight = FontWeight.Medium, spacing = 0.3f, align = TextAlign.Center,
            )
        }
    }
}

private const val AMP_MAX_KM = 800

@Composable
private fun PowerMeter(label: String, km: Int?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KText(label, 12f, Modifier.width(90.dp), color = Kw.ampLabel, spacing = 1f)
        Spacer(Modifier.width(10.dp))
        AmpCells(km, AMP_MAX_KM, Modifier.weight(1f).height(16.dp))
        Spacer(Modifier.width(10.dp))
        KText(
            "${km.orDash()} км", 16f, Modifier.width(80.dp),
            color = Kw.ampValue, family = VfdFont, italic = true, align = TextAlign.End,
        )
    }
}

@Composable
private fun AmpLegend(music: NowPlaying, modifier: Modifier) {
    val status = if (music.isPlaying) {
        "▶ ${music.artist?.takeIf { it.isNotBlank() } ?: "—"} — ${music.title?.takeIf { it.isNotBlank() } ?: "Нет музыки"}"
    } else "■ STOP"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom), horizontalAlignment = Alignment.End) {
        LegendLine("WIDE DYNAMIC RANGE")
        LegendLine("HYBRID POWER AMPLIFIER")
        LegendLine(status, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LegendLine(text: String, modifier: Modifier = Modifier) =
    KText(text, 10f, modifier, color = Kw.grey, spacing = 1f, align = TextAlign.End)
