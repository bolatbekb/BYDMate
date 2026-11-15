package com.bydmate.app.ui.home.onkyo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.ui.home.CassetteDoor
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.counterText
import com.bydmate.app.ui.home.labelLine
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.RetroTab
import kotlin.math.min

private val DesignW = 1280.dp
private val DesignH = 604.dp

/** What the gold centre panel of the amplifier shows; chosen with the small LED keys. */
internal enum class CentreView { LOG, TRACK, WEEK }

/**
 * Retro home screen styled as a two-tier hi-fi stack: a cassette deck over a power amplifier.
 * The deck plays the active media session (hubs turn, tape packs follow the track, the keys
 * drive [controls]); its meters, knobs and the amplifier's two VU meters read the car's
 * [data]. The amplifier's INPUT SELECTOR keys open the app's [tabs] through [onTab]; its lit
 * TAPE key is this screen. Drawn at a fixed 1280 x 604 dp (the car's content area) and scaled
 * uniformly to fit, letterboxed in black.
 */
@Composable
fun OnkyoHome(
    data: HomeData,
    music: NowPlaying,
    controls: MusicControls,
    tabs: List<RetroTab> = emptyList(),
    onTab: (RetroTab) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ok.Letterbox), contentAlignment = Alignment.Center) {
        val sw = if (constraints.hasBoundedWidth) maxWidth / DesignW else 1f
        val sh = if (constraints.hasBoundedHeight) maxHeight / DesignH else sw
        val scale = min(sw, sh)
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, 1f)) {
            Box(Modifier.size(DesignW, DesignH)) { Rack(data, music, controls, tabs, onTab) }
        }
    }
}

@Composable
private fun Rack(data: HomeData, music: NowPlaying, controls: MusicControls, tabs: List<RetroTab>, onTab: (RetroTab) -> Unit) {
    var centre by rememberSaveable { mutableStateOf(CentreView.LOG) }
    var percent by rememberSaveable { mutableStateOf(false) }
    var swayOn by rememberSaveable { mutableStateOf(true) }

    // Hubs: a frame clock so the speed can change (play / wind) without the reels jumping.
    val hubAngle = remember { mutableFloatStateOf(0f) }
    val spinning = music.isPlaying || music.winding
    LaunchedEffect(spinning, music.winding) {
        if (!spinning) return@LaunchedEffect
        val degPerNs = 360f / (if (music.winding) 0.4f else 2.4f) / 1e9f
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { t ->
                hubAngle.floatValue = (hubAngle.floatValue + (t - last) * degPerNs) % 360f
                last = t
            }
        }
    }

    // Needles sway ±1.5° while the music plays.
    val sway = remember { Animatable(0f) }
    LaunchedEffect(music.isPlaying && swayOn) {
        if (!(music.isPlaying && swayOn)) { sway.animateTo(0f, tween(300)); return@LaunchedEffect }
        while (true) {
            sway.animateTo(1.5f, tween(600, easing = FastOutSlowInEasing))
            sway.animateTo(-1.5f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DeckTier(
            data, music, controls, { hubAngle.floatValue },
            centre = centre, onCentre = { centre = it },
            percent = percent, onPercent = { percent = it },
            swayOn = swayOn, onSway = { swayOn = !swayOn },
        )
        AmpTier(data, music, centre, percent, { sway.value }, tabs, onTab, Modifier.weight(1f))
    }
}

/* ---------------------------------------------------------------- deck (upper tier) */

private val DeckH = 296.dp

/** The door fills the deck's height under the brand line (10 + 18 + 5 + door + 10). */
private val DoorW = 340.dp

@Composable
private fun DeckTier(
    data: HomeData,
    music: NowPlaying,
    controls: MusicControls,
    hubAngle: () -> Float,
    centre: CentreView,
    onCentre: (CentreView) -> Unit,
    percent: Boolean,
    onPercent: (Boolean) -> Unit,
    swayOn: Boolean,
    onSway: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(DeckH).clip(RoundedCornerShape(3.dp))
            .brushedAluminium().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DeckLeft(Modifier.width(84.dp).fillMaxHeight())
        Column(Modifier.width(DoorW), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Brand(15, "STEREO CASSETTE DRIVE DECK")
            CassetteDoor(
                label = music.labelLine(),
                progress = music.progress,
                hubAngle = hubAngle,
                modifier = Modifier.size(DoorW, DoorW * (290f / 392f)),
                frame = Ok.Edge,
                caption = "AMCS · DOLBY B NR · AUTO REVERSE",
            )
        }
        PeakPanel(data.socPercent, data.fuelPercent, Modifier.width(132.dp).fillMaxHeight())
        Column(Modifier.width(136.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationPanel(music.isPlaying)
            ModePanel(data.driveProfile, data.driveProfiles)
            CounterPanel(music)
        }
        TransportKeys(music.isPlaying, controls, Modifier.width(232.dp))
        DeckRight(data, centre, onCentre, percent, onPercent, swayOn, onSway, Modifier.weight(1f).fillMaxHeight())
    }
}

/** "BYDMATE Hybrid" with the model line, baseline-aligned. */
@Composable
internal fun Brand(size: Int, line: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("BYDMATE", Modifier.alignByBaseline(), size = size.sp, color = Ok.White, weight = FontWeight.ExtraBold)
        Label("Hybrid", Modifier.alignByBaseline(), size = (size - 1).sp, color = Ok.White, family = FontFamily.Serif, style = FontStyle.Italic, spacing = 0.sp)
        Label(line, Modifier.alignByBaseline())
        Box(Modifier.weight(1f))
        Box(Modifier.alignByBaseline()) { trailing() }
    }
}

/** OPEN key, A·B·C selector, phones jack and the POWER key with its amber lamp. */
@Composable
private fun DeckLeft(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(1.dp, 10.dp))
        Label("OPEN")
        Box(Modifier.size(1.dp, 4.dp))
        FlatKey(Modifier.size(66.dp, 22.dp))
        Box(Modifier.size(1.dp, 10.dp))
        Label("A · B · C")
        Box(Modifier.size(1.dp, 4.dp))
        SmallKnob()
        Box(Modifier.size(1.dp, 10.dp))
        Label("PHONES")
        Box(Modifier.size(1.dp, 4.dp))
        Jack()
        Box(Modifier.weight(1f))
        LedStrip(true, Modifier.size(66.dp, 4.dp))
        Box(Modifier.size(1.dp, 4.dp))
        FlatKey(Modifier.size(66.dp, 32.dp))
        Box(Modifier.size(1.dp, 4.dp))
        Label("POWER")
    }
}

/** Two vertical LED columns: battery charge and fuel level. All dark when unknown. */
@Composable
private fun PeakPanel(soc: Int?, fuel: Int?, modifier: Modifier) {
    Column(
        modifier.insetPanel().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TitleBar("ЭНЕРГИЯ")
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.width(22.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                for (i in 0..10) Label("${100 - i * 10}", color = Ok.Dim, spacing = 0.sp)
            }
            PeakColumn(soc, Modifier.width(30.dp).fillMaxHeight())
            PeakColumn(fuel, Modifier.width(30.dp).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(22.dp))
            Label("ЗАР", Modifier.width(30.dp), color = Ok.Dim, align = TextAlign.Center, spacing = 0.5.sp)
            Label("БАК", Modifier.width(30.dp), color = Ok.Dim, align = TextAlign.Center, spacing = 0.5.sp)
        }
    }
}

@Composable
private fun PeakColumn(pct: Int?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0..10) {
            val level = 100 - i * 10
            val c = when { level >= 90 -> Ok.Red; level >= 80 -> Ok.Yellow; else -> Ok.Green }
            LedStrip(pct != null && pct >= level, Modifier.weight(1f).fillMaxWidth(), color = c, off = Color(0xFF1A2A1E), halo = 4.dp)
        }
    }
}

/** PLAY / PAUSE lamps with the dark REC indicator between. */
@Composable
private fun OperationPanel(playing: Boolean) {
    TitledPanel("OPERATION", Modifier.fillMaxWidth()) {
        Box(Modifier.size(1.dp, 6.dp))
        val playC = if (playing) Ok.Green else Ok.Off
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("PLAY", color = playC, weight = FontWeight.SemiBold)
            GlyphIcon(Glyph.PLAY, playC, 8.dp)
        }
        Box(Modifier.size(1.dp, 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).border(1.dp, Color(0xFFC8322B)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).background(Color(0xFF5A1A16), CircleShape))
            }
            val pauseC = if (playing) Ok.Dim else Ok.Yellow
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("PAUSE", color = pauseC, weight = FontWeight.SemiBold)
                GlyphIcon(Glyph.PAUSE, pauseC, 8.dp)
            }
        }
    }
}

/**
 * ECO / NORMAL / SPORT selector: only the modes this car reports support for, the current one
 * lit. A dash while the car has not answered (or has none of them).
 */
@Composable
private fun ModePanel(mode: String?, modes: List<String>) {
    TitledPanel("РЕЖИМ", Modifier.fillMaxWidth()) {
        if (modes.isEmpty()) {
            Box(Modifier.padding(top = 4.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Label("—", color = Ok.Dim, weight = FontWeight.SemiBold)
            }
        }
        modes.forEach { m ->
            val on = m == mode
            Box(
                Modifier.padding(top = 4.dp).fillMaxWidth().background(if (on) Ok.Amber else Color.Transparent)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Label(m, color = if (on) Color(0xFF141416) else Ok.Dim, weight = FontWeight.SemiBold)
            }
        }
    }
}

/** Green seven-segment style counter: the play position. */
@Composable
private fun CounterPanel(music: NowPlaying) {
    TitledPanel("TAPE COUNTER", Modifier.fillMaxWidth()) {
        Box(
            Modifier.padding(top = 4.dp).fillMaxWidth().background(Color(0xFF050606)).border(1.dp, Ok.Edge),
            contentAlignment = Alignment.Center,
        ) {
            Label(
                music.counterText(),
                size = 26.sp, color = Ok.Green, weight = FontWeight.Bold, spacing = 0.sp,
                family = FontFamily.Monospace, style = FontStyle.Italic,
                shadow = Shadow(Ok.Green.copy(alpha = 0.7f), blurRadius = 8f * LocalDensity.current.density),
            )
        }
    }
}
