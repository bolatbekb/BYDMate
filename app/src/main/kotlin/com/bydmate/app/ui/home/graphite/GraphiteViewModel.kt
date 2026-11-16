package com.bydmate.app.ui.home.graphite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.local.dao.TripPointDao
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.ui.home.cdrive.ChartBuckets
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class GraphiteViewModel @Inject constructor(
    private val tripDao: TripDao,
    private val tripPointDao: TripPointDao,
    ledger: LedgerRepository,
    settings: SettingsRepository,
    @ApplicationContext ctx: Context,
) : ViewModel() {
    private val prefs = ctx.getSharedPreferences("bydmate_home", Context.MODE_PRIVATE)

    private val _period = MutableStateFlow(
        runCatching { ChartPeriod.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ChartPeriod.WEEK)
    )
    val period: StateFlow<ChartPeriod> = _period.asStateFlow()

    fun select(p: ChartPeriod) {
        _period.value = p
        prefs.edit().putString(KEY, p.name).apply()
    }

    /** Emits the local date at once and again whenever it rolls over (checked once a minute). */
    private val date = flow {
        while (true) {
            emit(LocalDate.now(ZoneId.systemDefault()))
            delay(60_000)
        }
    }.distinctUntilChanged()

    /** Trips of [p]'s window. getByDateRange is inclusive on both ends, hence end - 1. */
    private fun window(p: ChartPeriod, now: ZonedDateTime) = tripDao.getByDateRange(
        ChartBuckets.windowStart(p, now).toInstant().toEpochMilli(),
        ChartBuckets.windowEnd(now).toInstant().toEpochMilli() - 1,
    )

    /** Local hour start; changes when the hour (or date) rolls over, so the DAY `current` bucket advances. */
    private val hour = flow {
        while (true) {
            emit(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS))
            delay(60_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val energy: StateFlow<EnergySeries?> = combine(_period, hour) { p, _ -> p }
        .flatMapLatest { p ->
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            window(p, now).map { EnergyBuckets.build(it, p, now) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // One collector of today's trips, shared by `today` and `regenKwh`.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayTrips = date.flatMapLatest {
        window(ChartPeriod.DAY, ZonedDateTime.now(ZoneId.systemDefault()))
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val today: StateFlow<TodayStats?> = todayTrips
        .map { trips -> TodayStatsCalc.from(trips, trips.sumOf { it.distanceKm ?: 0.0 }.takeIf { it > 0.0 }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val regenCache = RegenCache()

    /** Regen of today's finished trips; recomputed when the trip list changes, each trip's points read once. */
    val regenKwh: StateFlow<Double?> = todayTrips
        .map { trips -> withContext(Dispatchers.IO) { regenCache.total(trips, tripPointDao::getByTripId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tankCapacityL: StateFlow<Double?> = flow { emit(runCatching { ledger.tankCapacityL() }.getOrNull()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val batteryCapacityKwh: StateFlow<Double?> = flow { emit(runCatching { settings.getBatteryCapacity() }.getOrNull()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object { const val KEY = "graphite_chart_period" }
}
