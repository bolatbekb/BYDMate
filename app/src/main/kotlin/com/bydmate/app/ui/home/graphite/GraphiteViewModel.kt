package com.bydmate.app.ui.home.graphite

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.autoservice.AdbOnDeviceClient
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList") // Hilt constructor injection
class GraphiteViewModel @Inject constructor(
    private val tripDao: TripDao,
    ledger: LedgerRepository,
    settings: SettingsRepository,
    private val battery: BatteryStateRepository,
    private val pm25Reader: Pm25Reader,
    private val adb: AdbOnDeviceClient,
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

    // MainActivity sets the default locale to the app language; the screen confirms it (setLang).
    private val lang = MutableStateFlow(grLang(Locale.getDefault().language))

    /** The app language, for the week chart's day labels. */
    fun setLang(value: GrLang) {
        lang.value = value
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
    val energy: StateFlow<EnergySeries?> = combine(_period, hour, lang) { p, _, l -> p to l }
        .flatMapLatest { (p, l) ->
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            combine(window(p, now), LiveTrip.state) { trips, live ->
                val rows = trips.map { it.toRow() }
                val nowMs = System.currentTimeMillis()
                TripLog.hides(nowMs, TripLogEvents.hideDecisions(rows, live, nowMs)) // fork log
                EnergyBuckets.build(TripMatch.chartRows(rows, live, nowMs), p, now, l).also { GraphiteWarm.energy[EnergyKey(p, l)] = it }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphiteWarm.energy[EnergyKey(_period.value, lang.value)])

    /** The trip card: today's recorded trips + the live session; the day rolls over with [date]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trip: StateFlow<TripCardModel> = date
        .flatMapLatest {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val dayStart = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            combine(window(ChartPeriod.DAY, now), LiveTrip.state) { trips, live ->
                TripCardCalc.from(live, trips.map { it.toRow() }, dayStart)
            }
        }
        .onEach { GraphiteWarm.trip = it }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphiteWarm.trip ?: TripCardModel.EMPTY)

    // The last good SOH and when it was read: kept across WhileSubscribed restarts, so returning to
    // the screen neither re-reads before 10 min nor loses the value to a failed read.
    @Volatile private var sohLast: Float? = GraphiteWarm.sohPercent
    @Volatile private var sohReadAt = 0L

    /** Battery SOH, %: read at once, then every 10 min; a failed read keeps the last good value. */
    val sohPercent: StateFlow<Float?> = flow {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (sohReadAt == 0L || now - sohReadAt >= SOH_POLL_MS) {
                runCatching { battery.refresh().sohPercent }.getOrNull()?.takeIf { it.isFinite() }?.let { sohLast = it }
                sohReadAt = now
            }
            emit(sohLast)
            delay((SOH_POLL_MS - (SystemClock.elapsedRealtime() - sohReadAt)).coerceAtLeast(1_000L))
        }
    }
        .onEach { v -> v?.let { GraphiteWarm.sohPercent = it } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphiteWarm.sohPercent)

    /**
     * PM2.5, read at once and every minute; null = no sensor. Demo shows a fixed reading. Starts from the last
     * known reading; a failed read keeps it.
     */
    val pm25: StateFlow<Pm25?> = flow {
        while (true) {
            val read = if (demo) Result.success(DEMO_PM25) else runCatching { pm25Reader.read() }
            val linkUp = demo || runCatching { adb.isConnected() }.getOrDefault(false)
            read.onSuccess { GraphiteWarm.onPmRead(it, linkUp) }
            emit(warmPm())
            delay(PM25_POLL_MS)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), warmPm())

    private fun warmPm(): Pm25? = (GraphiteWarm.pm25 as? Pm25Memo.Reading)?.pm

    val tankCapacityL: StateFlow<Double?> = flow { emit(runCatching { ledger.tankCapacityL() }) }
        .onEach { r -> r.onSuccess { GraphiteWarm.tankL = it } } // a cleared capacity (null) is forgotten too
        .map { it.getOrNull() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphiteWarm.tankL)

    val batteryCapacityKwh: StateFlow<Double?> = flow { emit(runCatching { settings.getBatteryCapacity() }.getOrNull()) }
        .onEach { v -> v?.let { GraphiteWarm.capacityKwh = it } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphiteWarm.capacityKwh)

    private companion object {
        const val KEY = "graphite_chart_period"
        const val SOH_POLL_MS = 10 * 60_000L
        const val PM25_POLL_MS = 60_000L
        val DEMO_PM25 = Pm25(inside = 12, outside = 35, levelIn = 1, levelOut = 2)
    }
}
