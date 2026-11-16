package com.bydmate.app.ui.home.graphite

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import com.bydmate.app.ui.home.cdrive.ReadyLamp
import com.bydmate.app.ui.home.cdrive.ReadyShown
import com.bydmate.app.ui.home.hybrid.MockupFrame
import com.bydmate.app.ui.home.hybrid.px
import com.bydmate.app.ui.theme.SkinState
import kotlinx.coroutines.delay
import java.time.LocalDateTime

private const val CLOCK_TICK_MS = 10_000L
private const val LAMP_TICK_MS = 250L

/**
 * The Graphite home (tools/graphite/design): top bar, six cards on the SPEC §2 grid, the menu.
 * The palette follows the skin; live values redraw at most once a second (RetroHome samples).
 */
@Composable
fun GraphiteHome(
    data: HomeData,
    music: NowPlaying,
    controls: MusicControls,
    tabs: List<RetroTab>,
    onTab: (RetroTab) -> Unit,
    vm: GraphiteViewModel = hiltViewModel(),
) {
    val period by vm.period.collectAsStateWithLifecycle()
    val energy by vm.energy.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val regenKwh by vm.regenKwh.collectAsStateWithLifecycle()
    val tankL by vm.tankCapacityL.collectAsStateWithLifecycle()
    val capacityKwh by vm.batteryCapacityKwh.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalGr provides homePalette(SkinState.current.id)) {
        MockupFrame {
            val p = LocalGr.current
            Column(
                Modifier.fillMaxSize().grBackground(p).padding(start = px(32), top = px(22), end = px(32), bottom = px(22)),
                verticalArrangement = Arrangement.spacedBy(px(20)),
            ) {
                TopBar(data, Modifier.fillMaxWidth().height(px(68)))
                // The six cards. Tasks 6–9 swap each *Slot call here for its card (same arguments).
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(px(20))) {
                    Column(Modifier.width(px(790)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        RangeCard(data, regenKwh, tankL, capacityKwh, Modifier.fillMaxWidth().weight(1f))
                        TempsCard(data, Modifier.fillMaxWidth().height(px(290)))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        GalleryCard(Modifier.fillMaxWidth().height(px(404)))
                        EnergySlot(energy, period, vm::select, Modifier.fillMaxWidth().weight(1f))
                    }
                    Column(Modifier.width(px(820)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        MediaSlot(music, controls, Modifier.fillMaxWidth().weight(1f))
                        TodaySlot(data.todayKm, today, Modifier.fillMaxWidth().height(px(252)))
                    }
                }
                BottomMenu(tabs, onTab, Modifier.fillMaxWidth().height(px(116)))
            }
        }
    }
}

/**
 * Bg, plus the BgGlow radial where the variant has one: CSS `radial-gradient(1600px 900px at
 * 50% 40%, BgGlow, Bg 70%)`, i.e. an ellipse with radii 1600 × 900 reaching Bg at 70 %.
 */
private fun Modifier.grBackground(p: GrPalette): Modifier = drawWithCache {
    val u = px(1).toPx()
    val c = Offset(size.width * 0.5f, size.height * 0.4f)
    val rx = 1600 * u
    val glow = if (p.bgGlow == p.bg) null else Brush.radialGradient(0f to p.bgGlow, 0.7f to p.bg, 1f to p.bg, center = c, radius = rx)
    onDrawBehind {
        drawRect(p.bg)
        // the ellipse is wider than the frame: clip so it never paints the letterbox
        if (glow != null) clipRect { scale(1f, 900f / 1600f, pivot = c) { drawCircle(glow, radius = rx, center = c) } }
    }
}

/* ---------------------------------------------------------------- card slots (placeholders) */

@Suppress("UNUSED_PARAMETER")
@Composable
private fun EnergySlot(series: EnergySeries?, period: ChartPeriod, onPeriod: (ChartPeriod) -> Unit, modifier: Modifier) {
    CardBody(modifier) { CardHeader(R.drawable.gr_ic_leaf, "Расход энергии") }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun MediaSlot(music: NowPlaying, controls: MusicControls, modifier: Modifier) {
    CardBody(modifier) { CardHeader(R.drawable.gr_ic_note, "Медиа") }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun TodaySlot(todayKm: Double?, today: TodayStats?, modifier: Modifier) {
    CardBody(modifier) { CardHeader(R.drawable.gr_ic_route, "Пробег сегодня") }
}

/* ---------------------------------------------------------------- top bar */

@Composable
private fun TopBar(data: HomeData, modifier: Modifier) {
    val p = LocalGr.current
    val now = rememberNow()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(14))) {
        Text(grClock(now), maxLines = 1, softWrap = false, style = grStyle(46f, 300, -1f, p.textPrimary), modifier = Modifier.padding(end = px(6)))
        Text(grDate(now.toLocalDate()), maxLines = 1, softWrap = false, style = grStyle(23f, 400, color = p.textSecondary), modifier = Modifier.padding(end = px(20)))
        PowerChip(data.powerState)
        engineChip(data.engineRpm)?.let { text ->
            val (main, note) = splitNote(text)
            Chip(main, note = note, lead = { GrIcon(R.drawable.gr_ic_engine, 26f, p.fuel) })
        }
        modeChip(data.driveMode, data.driveProfile)?.let { text ->
            Chip(text, lead = { GrIcon(R.drawable.gr_ic_leaf, 24f, p.electric) })
        }
        Spacer(Modifier.weight(1f))
        data.outsideTemp?.let { t ->
            Chip(grDeg(t), note = "снаружи", lead = { GrIcon(R.drawable.gr_ic_tree, 24f, p.cold) })
        }
        data.todayKm?.let { km ->
            Chip("${grDec(km)} км", note = "сегодня", lead = { GrIcon(R.drawable.gr_ic_route, 24f, p.textSecondary) })
        }
        val links = rememberLinks()
        if (links.bt || links.wifi || links.cell) Links(links)
    }
}

/** "Двигатель заведён · 1 240 об/мин" → "Двигатель заведён" + "· 1 240 об/мин" (the quieter part). */
private fun splitNote(text: String): Pair<String, String?> {
    val i = text.indexOf(" · ")
    return if (i < 0) text to null else text.substring(0, i) to text.substring(i + 1)
}

/** Power chip: a glowing Electric dot while on, a TextTertiary dot when off; hidden when unknown. */
@Composable
private fun PowerChip(powerState: Int?) {
    val p = LocalGr.current
    val shown = rememberReadyLamp(powerState)
    if (powerState == null) return
    val text = powerChip(
        when (shown) {
            ReadyShown.READY -> 2
            ReadyShown.ON -> 1
            ReadyShown.OFF -> 0
        },
    ) ?: return
    val (main, note) = splitNote(text)
    val on = shown != ReadyShown.OFF
    Chip(main, note = note, lead = { StatusDot(if (on) p.electric else p.textTertiary, glow = on) })
}

/** A 10 × 10 status dot, with a 10 px glow of its own colour when [glow]. */
@Composable
private fun StatusDot(color: Color, glow: Boolean) {
    Box(
        Modifier.size(px(10)).drawWithCache {
            val u = px(1).toPx()
            val r = size.minDimension / 2
            val halo = if (glow) blurPaint(color, 10 * u) else null
            onDrawBehind {
                if (halo != null) drawIntoCanvas { it.nativeCanvas.drawCircle(center.x, center.y, r, halo) }
                drawCircle(color, r)
            }
        },
    )
}

/** Bluetooth, Wi-Fi and mobile icons 26 TextSecondary, then the mobile type; only what is on. */
@Composable
private fun Links(links: GraphiteLinks.Links) {
    val p = LocalGr.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(22))) {
        if (links.bt) GrIcon(R.drawable.gr_ic_bt, 26f, p.textSecondary)
        if (links.wifi) GrIcon(R.drawable.gr_ic_wifi, 26f, p.textSecondary)
        if (links.cell) GrIcon(R.drawable.gr_ic_signal, 26f, p.textSecondary)
        val type = links.cellType
        if (links.cell && type != null) Text(type, maxLines = 1, softWrap = false, style = grStyle(22f, 400, color = p.textSecondary))
    }
}

/** Local time, re-read every 10 s while the screen is started. */
@Composable
private fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDateTime.now()
                delay(CLOCK_TICK_MS)
            }
        }
    }
    return now
}

/**
 * The lamp for [powerState] (as CdHeader): a rise shows at once, a fall only after 2 s, so the
 * ~0.6 s dip of pressing start on a running car does not blink "Выключен".
 */
@Composable
private fun rememberReadyLamp(powerState: Int?): ReadyShown {
    val lamp = remember { ReadyLamp(holdMs = 2_000) }
    val power by rememberUpdatedState(powerState)
    var shown by remember { mutableStateOf(lamp.update(powerState, SystemClock.elapsedRealtime())) }
    LaunchedEffect(powerState) { shown = lamp.update(powerState, SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shown = lamp.update(power, SystemClock.elapsedRealtime())
                delay(LAMP_TICK_MS)
            }
        }
    }
    return shown
}

/* ---------------------------------------------------------------- bottom menu */

/** Icon and label of a menu key by route; an unknown route keeps its own label with sliders. */
internal fun menuItem(route: String, label: String): Pair<Int, String> = when (route) {
    "tech_panel" -> R.drawable.gr_ic_car to "Машина"
    "trips" -> R.drawable.gr_ic_route to "Поездки"
    "charges" -> R.drawable.gr_ic_plug to "Зарядки"
    "automation" -> R.drawable.gr_ic_flow to "Автоматизации"
    "settings" -> R.drawable.gr_ic_sliders to "Настройки"
    else -> R.drawable.gr_ic_sliders to label
}

/**
 * The menu card (radius 40, padding 12): equal keys, 1 px Divider between them. No key is
 * active at rest; a pressed key lights (TabActive, Electric bar) and hides its neighbouring dividers.
 */
@Composable
private fun BottomMenu(tabs: List<RetroTab>, onTab: (RetroTab) -> Unit, modifier: Modifier) {
    val p = LocalGr.current
    val sources = remember(tabs.size) { List(tabs.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }
    Row(modifier.grCard(p, 40f).padding(px(12))) {
        tabs.forEachIndexed { i, tab ->
            val divider = i > 0 && !pressed[i] && !pressed[i - 1]
            MenuKey(tab, pressed[i], divider, sources[i], onTab)
        }
    }
}

@Composable
private fun RowScope.MenuKey(
    tab: RetroTab,
    active: Boolean,
    divider: Boolean,
    source: MutableInteractionSource,
    onTab: (RetroTab) -> Unit,
) {
    val p = LocalGr.current
    val (icon, label) = menuItem(tab.route, tab.label)
    val color = if (active) p.textPrimary else p.textSecondary
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .drawBehind {
                if (divider) {
                    val u = px(1).toPx()
                    drawRect(p.divider, topLeft = Offset(0f, 26 * u), size = Size(u, size.height - 52 * u))
                }
            }
            .then(if (active) Modifier.grPill(p.tabActive, p.tabActiveEdge, p.edgeStyle, 30f) else Modifier)
            .clickable(interactionSource = source, indication = null) { onTab(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(16))) {
            GrIcon(icon, if (icon == R.drawable.gr_ic_car) 34f else 32f, color)
            Text(label, maxLines = 1, softWrap = false, style = grStyle(26f, 400, color = color, tnum = false))
        }
        if (active) ActiveBar(p.electric, Modifier.align(Alignment.BottomCenter).padding(bottom = px(10)))
    }
}

/** The 36 × 4 Electric bar under a pressed key, with a 12 px glow. */
@Composable
private fun ActiveBar(color: Color, modifier: Modifier) {
    Box(
        modifier.size(px(36), px(4)).drawWithCache {
            val u = px(1).toPx()
            val r = 2 * u
            val halo = blurPaint(color, 12 * u)
            onDrawBehind {
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, halo) }
                drawRoundRect(color, cornerRadius = CornerRadius(r))
            }
        },
    )
}
