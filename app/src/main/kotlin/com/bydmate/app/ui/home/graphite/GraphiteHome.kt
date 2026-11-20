package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.TrackingService
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.common.MockupFrame
import com.bydmate.app.ui.home.common.px
import kotlinx.coroutines.delay

private const val ONLINE_TICK_MS = 5_000L

/** Width of both side columns (mockup px); the centre keeps what 790 + 820 left it. */
internal const val SIDE_COLUMN = 805f

/** Temps and Trip share one height: 366 with the PM2.5 line, 290 without. */
internal fun bottomCardHeight(hasPmRow: Boolean): Float = if (hasPmRow) 366f else 290f

/** The cards that swap places in the top row of the side columns. */
internal enum class SideCard { RANGE, MEDIA }

/** (left, right) top cards; with [mediaLeft] the player and the range card swap. */
internal fun topCards(mediaLeft: Boolean): Pair<SideCard, SideCard> =
    if (mediaLeft) SideCard.MEDIA to SideCard.RANGE else SideCard.RANGE to SideCard.MEDIA

/**
 * The Graphite home's mockup height: the car's 1208 px content area less the app's 64 dp (128 px)
 * bottom bar, which stays under this home as on every tab (the home's own menu is gone, owner
 * 2026-11-20). The cards' row takes the room.
 */
internal const val GRAPHITE_DESIGN_H = 1080

/**
 * The Graphite home (tools/graphite/design): top bar and six cards on the SPEC §2 grid; the app's
 * bottom bar sits below it. The palette and the card look follow GraphiteLookState (Settings);
 * live values redraw at most once a second (RetroHome samples).
 */
@Composable
fun GraphiteHome(
    data: HomeData,
    music: State<NowPlaying>, // read only inside the media card, so a track change recomposes just that card
    controls: MusicControls,
    vm: GraphiteViewModel = hiltViewModel(),
) {
    val period by vm.period.collectAsStateWithLifecycle()
    val energy by vm.energy.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
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
        // The bands (only where the window is taller than the car's) take the scheme's background,
        // the same as the «Graphite» skin's bar below.
        MockupFrame(designH = GRAPHITE_DESIGN_H, letterbox = LocalGr.current.bg, fillHeight = true) {
            val p = LocalGr.current
            Column(
                Modifier.fillMaxSize().grBackground(p).padding(start = px(32), top = px(22), end = px(32), bottom = px(22)),
                verticalArrangement = Arrangement.spacedBy(px(20)),
            ) {
                TopBar(data, online, Modifier.fillMaxWidth().height(px(68)))
                // The six cards. Tasks 6–9 swap each *Slot call here for its card (same arguments).
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(px(20))) {
                    val hasFuelTank = data.fuelPercent != null || tankL != null
                    val (leftTop, rightTop) = topCards(GraphiteLookState.mediaLeft)
                    val bottomH = px(bottomCardHeight(pmSides != null))
                    // Both side columns: the top card takes the rest, Temps and Trip share one height
                    // (the PM2.5 line makes both taller; the top cards give the room).
                    @Composable
                    fun TopCard(card: SideCard, modifier: Modifier) = when (card) {
                        SideCard.RANGE -> RangeCard(data, soh, tankL, capacityKwh, compact = pmSides != null, modifier)
                        SideCard.MEDIA -> MediaCard(music, controls, compact = pmSides != null, modifier)
                    }
                    Column(Modifier.width(px(SIDE_COLUMN)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        TopCard(leftTop, Modifier.fillMaxWidth().weight(1f))
                        TempsCard(data, pmSides, Modifier.fillMaxWidth().height(bottomH))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        GalleryCard(Modifier.fillMaxWidth().height(px(404)))
                        EnergyCard(energy, period, vm::select, hasFuelTank, Modifier.fillMaxWidth().weight(1f))
                    }
                    Column(Modifier.width(px(SIDE_COLUMN)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(20))) {
                        TopCard(rightTop, Modifier.fillMaxWidth().weight(1f))
                        TripCard(trip, hasFuelTank, tall = pmSides != null, Modifier.fillMaxWidth().height(bottomH))
                    }
                }
            }
        }
    }
}

/**
 * Bg, plus the BgGlow radial where the variant has one: CSS `radial-gradient(1600px 900px at
 * 50% 40%, BgGlow, Bg 70%)`, i.e. an ellipse with radii 1600 × 900 reaching Bg at 70 %.
 * Also the screen background of the «Graphite» app skin (ui/skin/GraphiteSkinParts.kt).
 */
@Composable
internal fun Modifier.grBackground(p: GrPalette): Modifier = this then remember(p) { backgroundModifier(p) }

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
