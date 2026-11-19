package com.bydmate.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import android.os.SystemClock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bydmate.app.ui.navigation.Screen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.delay
import com.bydmate.app.ui.dashboard.DashboardViewModel
import com.bydmate.app.ui.home.kenwood.KenwoodHome
import com.bydmate.app.ui.home.onkyo.OnkyoHome
import com.bydmate.app.ui.home.cdrive.CdHome
import com.bydmate.app.ui.home.graphite.GraphiteHome

/** True on the Dashboard tab while a retro home is on: it draws the tabs itself (AppNavigation hook). */
fun hidesBottomBar(route: String?): Boolean =
    route == Screen.Dashboard.route && HomeStyleState.current != HomeStyle.STANDARD

/**
 * The Dashboard tab: the author's screen, or one of the fork's retro home screens.
 * The hooks in the author's code are this call and [hidesBottomBar], both in AppNavigation.
 * [onOpenTab] switches tabs exactly as the bottom bar does.
 */
@Composable
fun ForkHome(onOpenTab: (String) -> Unit, standard: @Composable () -> Unit) {
    when (val style = HomeStyleState.current) {
        HomeStyle.STANDARD -> standard()
        else -> {
            val tabs = retroTabScreens(style).map { RetroTab(it.route, stringResource(it.labelRes), it.icon) }
            RetroHome(style, tabs, onTab = { onOpenTab(it.route) })
        }
    }
}

/**
 * The tabs a retro home draws itself: every tab but its own, except the Graphite home, whose menu
 * starts with its own «Главная», lit (owner, 2026-11-20).
 */
internal fun retroTabScreens(style: HomeStyle): List<Screen> =
    Screen.entries.filter { it != Screen.Dashboard || style == HomeStyle.GRAPHITE }

/** Retro counters (tape time, progress) run on this local tick; the source publishes only on change. */
private const val COUNTER_TICK_MS = 1_000L

@Composable
private fun RetroHome(
    style: HomeStyle,
    tabs: List<RetroTab>,
    onTab: (RetroTab) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    // One flow builds HomeData off Main: the inputs are combined, sampled once a second, built and
    // compared there; the home recomposes only when a value on screen changed. The first value is
    // built from the current snapshot, so the home never opens blank.
    val dataFlow = remember(viewModel) {
        homeDataFlow(
            viewModel.uiState, TrackingService.lastData,
            com.bydmate.app.fork.DriveModeSupport.supported, DayMileage.todayKm,
        )
    }
    val initial = remember(viewModel) {
        HomeData.from(
            viewModel.uiState.value, TrackingService.lastData.value,
            com.bydmate.app.fork.DriveModeSupport.supported.value, DayMileage.todayKm.value,
        )
    }
    val data by dataFlow.collectAsStateWithLifecycle(initialValue = initial)
    // Same as the author's screen: period totals and the insight reload on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The media poll runs only while the app is STARTED (not behind the navigator); resumes at once on return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> NowPlayingSource.start(context, scope)
                Lifecycle.Event.ON_STOP -> NowPlayingSource.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            NowPlayingSource.stopPolling()
        }
    }
    // A State, not a value: only the readers below recompose when the music changes.
    val music = NowPlayingSource.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { com.bydmate.app.fork.DriveModeSupport.ensureProbed(context) }
    when (style) {
        HomeStyle.KENWOOD -> LiveMusic(music) { KenwoodHome(data = data, music = it, controls = NowPlayingSource, tabs = tabs, onTab = onTab) }
        HomeStyle.ONKYO -> LiveMusic(music) { OnkyoHome(data = data, music = it, controls = NowPlayingSource, tabs = tabs, onTab = onTab) }
        HomeStyle.CDRIVE -> LiveMusic(music) { CdHome(data = data, music = it, controls = NowPlayingSource, tabs = tabs, onTab = onTab) }
        HomeStyle.GRAPHITE -> GraphiteHome(data, music, NowPlayingSource, tabs, onTab)
        HomeStyle.STANDARD -> Unit
    }
}

/**
 * The retro homes read the music as a value: this gives them the published state with its position
 * run to now, ticking once a second while a track plays (the source itself publishes only on change).
 */
@Composable
private fun LiveMusic(music: State<NowPlaying>, content: @Composable (NowPlaying) -> Unit) {
    val published = music.value
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, published.isPlaying) {
        if (!published.isPlaying) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(COUNTER_TICK_MS)
            }
        }
    }
    content(published.advancedTo(now))
}
