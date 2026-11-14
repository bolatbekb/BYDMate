package com.bydmate.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import com.bydmate.app.ui.dashboard.DashboardViewModel
import com.bydmate.app.ui.home.kenwood.KenwoodHome
import com.bydmate.app.ui.home.onkyo.OnkyoHome

/**
 * The Dashboard tab: the author's screen, or one of the fork's retro home screens.
 * The only hook in the author's code is the call in AppNavigation.
 */
@Composable
fun ForkHome(standard: @Composable () -> Unit) {
    when (val style = HomeStyleState.current) {
        HomeStyle.STANDARD -> standard()
        else -> RetroHome(style)
    }
}

private const val LIVE_SAMPLE_MS = 1_000L

@OptIn(FlowPreview::class)
@Composable
private fun RetroHome(style: HomeStyle, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Push patches lastData up to every 200 ms; the stack redraws at most once a second.
    // The current snapshot first, then a sample a second, so the stack never opens blank.
    val liveFlow = remember {
        TrackingService.lastData.sample(LIVE_SAMPLE_MS).onStart { emit(TrackingService.lastData.value) }
    }
    val live by liveFlow.collectAsStateWithLifecycle(initialValue = null)
    // Same as the author's screen: period totals and the insight reload on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        NowPlayingSource.start(context, scope)
        onDispose { NowPlayingSource.stopPolling() }
    }
    val music by NowPlayingSource.state.collectAsStateWithLifecycle()
    val data = remember(state, live) { HomeData.from(state, live) }
    when (style) {
        HomeStyle.KENWOOD -> KenwoodHome(data = data, music = music, controls = NowPlayingSource)
        HomeStyle.ONKYO -> OnkyoHome(data = data, music = music, controls = NowPlayingSource)
        HomeStyle.STANDARD -> Unit
    }
}
