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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.TrackingService
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.cdrive.ReadyLamp
import com.bydmate.app.ui.home.cdrive.ReadyShown
import com.bydmate.app.ui.home.common.MockupFrame
import com.bydmate.app.ui.home.common.px
import kotlinx.coroutines.delay
import java.time.LocalDate

private const val DATE_TICK_MS = 60_000L
private const val LAMP_TICK_MS = 250L
private const val ONLINE_TICK_MS = 5_000L

/**
 * The Graphite home (tools/graphite/design): top bar, six cards on the SPEC §2 grid, the menu.
 * The palette and the card look follow GraphiteLookState (Settings); live values redraw at most
 * once a second (RetroHome samples).
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
    val soh by vm.sohPercent.collectAsStateWithLifecycle()
    val pm25 by vm.pm25.collectAsStateWithLifecycle()
    val lang = rememberGrLang()
    // The week chart's day labels are built in the view model, in this language.
    LaunchedEffect(lang) { vm.setLang(lang) }
    val pmSides = remember(pm25, lang) { pm25Sides(pm25, lang) }
    val online = rememberOnline()
    val tankL by vm.tankCapacityL.collectAsStateWithLifecycle()
    val capacityKwh by vm.batteryCapacityKwh.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalGr provides GraphiteLookState.scheme.palette,
        LocalGrLook provides GraphiteLookState.look,
        LocalGrLang provides lang,
    ) {
        MockupFrame {
            val p = LocalGr.current
            Column(
                Modifier.fillMaxSize().grBackground(p).padding(start = px(32), top = px(22), end = px(32), bottom = px(22)),
                verticalArrangement = Arrangement.spacedBy(px(20)),
            ) {
                TopBar(data, online, Modifier.fillMaxWidth().height(px(68)))
                // The six cards. Tasks 6–9 swap each *Slot call here for its card (same arguments).
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(px(20))) {
                    val hasFuelTank = data.fuelPercent != null || tankL != null
                    Column(Modifier.width(px(790)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        // The PM2.5 line makes the temperatures 76 taller; the range card above gives the room.
                        RangeCard(data, soh, tankL, capacityKwh, compact = pmSides != null, Modifier.fillMaxWidth().weight(1f))
                        TempsCard(data, pmSides, Modifier.fillMaxWidth().height(px(if (pmSides != null) 290 + 76 else 290)))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        GalleryCard(Modifier.fillMaxWidth().height(px(404)))
                        EnergyCard(energy, period, vm::select, hasFuelTank, Modifier.fillMaxWidth().weight(1f))
                    }
                    Column(Modifier.width(px(820)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        MediaCard(music, controls, Modifier.fillMaxWidth().weight(1f))
                        TodayCard(data.todayKm, today, hasFuelTank, Modifier.fillMaxWidth().height(px(252)))
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
@Composable
private fun Modifier.grBackground(p: GrPalette): Modifier = this then remember(p) { backgroundModifier(p) }

private fun backgroundModifier(p: GrPalette): Modifier = Modifier.drawWithCache {
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

/* ---------------------------------------------------------------- top bar */

@Composable
private fun TopBar(data: HomeData, online: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val lang = LocalGrLang.current
    val today = rememberToday()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(14))) {
        // No clock: the car's own status bar shows the time.
        Text(grDate(today, lang), maxLines = 1, softWrap = false, style = grStyle(23f, 400, color = p.textSecondary), modifier = Modifier.padding(end = px(20)))
        engineChip(data.engineRpm, lang)?.let { text ->
            Chip(text, lead = { GrIcon(R.drawable.gr_ic_engine, 26f, p.fuel) })
        }
        modeChip(data.driveMode, data.driveProfile)?.let { text ->
            Chip(text, lead = { GrIcon(R.drawable.gr_ic_leaf, 24f, p.electric) })
        }
        Spacer(Modifier.weight(1f))
        // The car's power state is the link cue too: no link, no state to trust.
        if (online) PowerChip(data.powerState) else OfflineChip()
    }
}

/** «Включен · Ready» → «Включен» + «· Ready» (the quieter part). */
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
        LocalGrLang.current,
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

/** In place of the power chip while the car does not answer: a dim dot and «Нет связи». */
@Composable
private fun OfflineChip() {
    val p = LocalGr.current
    Chip(stringResource(R.string.home_gr_offline), lead = { StatusDot(p.textTertiary, glow = false) })
}

/**
 * The car link: the transport is up and a snapshot came in recently (TrackingService keeps the
 * last one after a loss). Re-checked every few seconds while started; demo is always online.
 */
@Composable
private fun rememberOnline(): Boolean {
    val ctx = LocalContext.current
    val demo = remember { DemoMode.isOn(ctx) }
    val connected by TrackingService.vehicleDataConnected.collectAsStateWithLifecycle()
    var fresh by remember { mutableStateOf(isOnline(true, TrackingService.lastDataAtMs, System.currentTimeMillis())) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                fresh = isOnline(true, TrackingService.lastDataAtMs, System.currentTimeMillis())
                delay(ONLINE_TICK_MS)
            }
        }
    }
    return demo || (connected && fresh)
}

/** Local date, re-read once a minute while the screen is started. */
@Composable
private fun rememberToday(): LocalDate {
    var now by remember { mutableStateOf(LocalDate.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = LocalDate.now()
                delay(DATE_TICK_MS)
            }
        }
    }
    return now
}

/**
 * The lamp for [powerState] (as CdHeader): a rise shows at once, a fall only after 2 s, so the
 * ~0.6 s dip of pressing start on a running car does not blink «Выключен».
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

/** Icon and label of a menu key by route, in [lang]; an unknown route keeps its own label with sliders. */
internal fun menuItem(route: String, label: String, lang: GrLang = GrLang.RU): Pair<Int, String> {
    val w = lang.words
    return when (route) {
        "tech_panel" -> R.drawable.gr_ic_car to w.menuCar
        "trips" -> R.drawable.gr_ic_route to w.menuTrips
        "charges" -> R.drawable.gr_ic_plug to w.menuCharges
        "automation" -> R.drawable.gr_ic_flow to w.menuAutomation
        "settings" -> R.drawable.gr_ic_sliders to w.menuSettings
        else -> R.drawable.gr_ic_sliders to label
    }
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
    val (icon, label) = menuItem(tab.route, tab.label, LocalGrLang.current)
    val color = if (active) p.textPrimary else p.textSecondary
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .drawBehind {
                if (divider) {
                    // A visible separator that fades out at both ends.
                    val u = px(1).toPx()
                    val line = Brush.verticalGradient(
                        0f to Color.Transparent, 0.5f to p.textTertiary.copy(alpha = 0.55f), 1f to Color.Transparent,
                        startY = 22 * u, endY = size.height - 22 * u,
                    )
                    drawRect(line, topLeft = Offset(-u / 2, 22 * u), size = Size(u, size.height - 44 * u))
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
