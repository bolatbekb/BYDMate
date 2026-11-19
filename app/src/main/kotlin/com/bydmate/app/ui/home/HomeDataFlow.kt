package com.bydmate.app.ui.home

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.DriveMode
import com.bydmate.app.ui.dashboard.DashboardUiState
import com.bydmate.app.ui.home.common.throttleLatest
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal const val HOME_SAMPLE_MS = 1_000L

/** fork (P3): the inputs of [HomeData.from], so one combine feeds one sampled build. */
internal data class HomeInputs(
    val state: DashboardUiState,
    val live: DiParsData?,
    val profiles: List<DriveMode>?,
    val todayKm: Double?,
)

/** One sample a [periodMs], built by [build], only when it differs, all on [context] (never Main). */
internal fun <T, R> Flow<T>.sampleBuild(
    periodMs: Long,
    context: CoroutineContext,
    build: (T) -> R,
): Flow<R> =
    // The first value of every collection at once (a return to the app is fresh in the first frame),
    // then at most one a period, the latest; no timer runs while the sources are quiet.
    throttleLatest(periodMs).map(build).distinctUntilChanged().flowOn(context)

/** HomeData from the four sources, at most once a second, off Main, emitted only when it changed. */
internal fun homeDataFlow(
    state: Flow<DashboardUiState>,
    live: Flow<DiParsData?>,
    profiles: Flow<List<DriveMode>?>,
    todayKm: Flow<Double?>,
    context: CoroutineContext = Dispatchers.Default,
): Flow<HomeData> =
    combine(state, live, profiles, todayKm, ::HomeInputs)
        .sampleBuild(HOME_SAMPLE_MS, context) { HomeData.from(it.state, it.live, it.profiles, it.todayKm) }
