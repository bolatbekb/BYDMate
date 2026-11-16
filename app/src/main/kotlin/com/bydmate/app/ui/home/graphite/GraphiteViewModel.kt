package com.bydmate.app.ui.home.graphite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.autoservice.Pm25
import com.bydmate.app.data.autoservice.Pm25Reader
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.domain.battery.BatteryStateRepository
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
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class GraphiteViewModel @Inject constructor(
    private val tripDao: TripDao,
    ledger: LedgerRepository,
    settings: SettingsRepository,
    private val battery: BatteryStateRepository,
    private val pm25Reader: Pm25Reader,
    @ApplicationContext ctx: Context,
) : ViewModel() {
    private val prefs = ctx.getSharedPreferences("bydmate_home", Context.MODE_PRIVATE)
    private val demo = DemoMode.isOn(ctx)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<TodayStats?> = date
        .flatMapLatest { window(ChartPeriod.DAY, ZonedDateTime.now(ZoneId.systemDefault())) }
        .map { trips -> TodayStatsCalc.from(trips, trips.sumOf { it.distanceKm ?: 0.0 }.takeIf { it > 0.0 }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Battery SOH, %: read at once, then every 10 min; a failed read keeps the last value. */
    val sohPercent: StateFlow<Float?> = flow {
        var last: Float? = null
        while (true) {
            last = runCatching { battery.refresh().sohPercent }.getOrNull() ?: last
            emit(last)
            delay(SOH_POLL_MS)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** PM2.5, read at once and every minute; null = no sensor. Demo shows a fixed reading. */
    val pm25: StateFlow<Pm25?> = flow {
        while (true) {
            emit(if (demo) DEMO_PM25 else runCatching { pm25Reader.read() }.getOrNull())
            delay(PM25_POLL_MS)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tankCapacityL: StateFlow<Double?> = flow { emit(runCatching { ledger.tankCapacityL() }.getOrNull()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val batteryCapacityKwh: StateFlow<Double?> = flow { emit(runCatching { settings.getBatteryCapacity() }.getOrNull()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        const val KEY = "graphite_chart_period"
        const val SOH_POLL_MS = 10 * 60_000L
        const val PM25_POLL_MS = 60_000L
        val DEMO_PM25 = Pm25(inside = 12, outside = 35, levelIn = 1, levelOut = 2)
    }
}
