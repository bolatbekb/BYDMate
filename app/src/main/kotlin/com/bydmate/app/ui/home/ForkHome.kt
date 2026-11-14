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

@Composable
private fun RetroHome(style: HomeStyle, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val live by TrackingService.lastData.collectAsStateWithLifecycle()
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
