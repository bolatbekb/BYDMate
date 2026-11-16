package com.bydmate.app.ui.home.cdrive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.local.entity.TripEntity
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
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil

enum class ChartPeriod { DAY, WEEK, MONTH }

/** [fuel] == null means the fuel line is hidden; a null value inside a list is a gap. */
data class ChartSeries(
    val labels: List<String>,
    val energy: List<Double?>,
    val fuel: List<Double?>?,
    val energyMax: Double,
    val fuelMax: Double,
)

object ChartBuckets {
    private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    private val DM = DateTimeFormatter.ofPattern("dd.MM")

    /** First instant of the window for [period], in [now]'s zone. */
    fun windowStart(period: ChartPeriod, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atStartOfDay(now.zone)
        return when (period) {
            ChartPeriod.DAY -> today
            ChartPeriod.WEEK -> now.toLocalDate().minusDays(6).atStartOfDay(now.zone)
            ChartPeriod.MONTH -> now.toLocalDate().minusDays(29).atStartOfDay(now.zone)
        }
    }

    /** Exclusive end of the window: the start of tomorrow. */
    fun windowEnd(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone)

    fun build(trips: List<TripEntity>, period: ChartPeriod, now: ZonedDateTime): ChartSeries {
        val zone = now.zone
        val start = windowStart(period, now)
        val n = when (period) {
            ChartPeriod.DAY -> 24
            ChartPeriod.WEEK -> 7
            ChartPeriod.MONTH -> 30
        }
        val startDate = start.toLocalDate()
        val kmE = DoubleArray(n); val kwh = DoubleArray(n); val hasE = BooleanArray(n)
        val kmF = DoubleArray(n); val liters = DoubleArray(n); val hasF = BooleanArray(n)
        var anyFuel = false
        for (t in trips) {
            val km = t.distanceKm ?: continue
            if (!(km > 0.0) || !km.isFinite()) continue
            val local = Instant.ofEpochMilli(t.startTs).atZone(zone)
            // Keyed by the local clock, so a DST day never shifts labels.
            val i = if (period == ChartPeriod.DAY) {
                if (local.toLocalDate() != startDate) continue
                local.hour
            } else {
                val d = ChronoUnit.DAYS.between(startDate, local.toLocalDate())
                if (d < 0 || d >= n) continue
                d.toInt()
            }
            t.kwhConsumed?.takeIf { it.isFinite() }?.let { kmE[i] += km; kwh[i] += it; hasE[i] = true }
            t.fuelLiters?.takeIf { it.isFinite() }?.let { kmF[i] += km; liters[i] += it; hasF[i] = true; anyFuel = true }
        }
        // Fuel per 100 km uses only the km of trips that have a litres figure.
        // Negative kWh (regen) stays in the sums; the plotted value never drops below zero.
        val energy = List(n) { if (hasE[it]) (kwh[it] / kmE[it] * 100.0).coerceAtLeast(0.0) else null }
        val fuel = if (!anyFuel) null
        else List(n) { if (hasF[it]) (liters[it] / kmF[it] * 100.0).coerceAtLeast(0.0) else null }

        val labels = List(n) { i ->
            when (period) {
                ChartPeriod.DAY -> if (i % 3 == 0) String.format(Locale.US, "%02d:00", i) else ""
                ChartPeriod.WEEK -> WEEKDAYS[startDate.plusDays(i.toLong()).dayOfWeek.value - 1]
                // Counted back from today, so today always carries a label.
                ChartPeriod.MONTH -> if ((n - 1 - i) % 5 == 0) DM.format(startDate.plusDays(i.toLong())) else ""
            }
        }
        return ChartSeries(
            labels = labels,
            energy = energy,
            fuel = fuel,
            energyMax = niceMax(energy.filterNotNull().maxOrNull(), 5.0, 30.0),
            fuelMax = niceMax(fuel?.filterNotNull()?.maxOrNull(), 2.0, 12.0),
        )
    }

    private fun niceMax(max: Double?, step: Double, floor: Double): Double =
        maxOf(floor, if (max == null) 0.0 else ceil(max / step) * step)
}

@HiltViewModel
class CdChartViewModel @Inject constructor(
    tripDao: TripDao,
    @ApplicationContext ctx: Context,
) : ViewModel() {
    private val prefs = ctx.getSharedPreferences("bydmate_home", Context.MODE_PRIVATE)

    private val _period = MutableStateFlow(
        runCatching { ChartPeriod.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ChartPeriod.DAY)
    )
    val period: StateFlow<ChartPeriod> = _period.asStateFlow()

    fun select(p: ChartPeriod) {
        _period.value = p
        prefs.edit().putString(KEY, p.name).apply()
    }

    /** Emits the local date at once and again whenever it rolls over (checked once a minute). */
    private val today = flow {
        while (true) {
            emit(LocalDate.now(ZoneId.systemDefault()))
            delay(60_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val series: StateFlow<ChartSeries?> = combine(_period, today) { p, _ -> p }
        .flatMapLatest { p ->
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val from = ChartBuckets.windowStart(p, now).toInstant().toEpochMilli()
            // getByDateRange is inclusive on both ends: pass end - 1 for a half-open window.
            val to = ChartBuckets.windowEnd(now).toInstant().toEpochMilli() - 1
            tripDao.getByDateRange(from, to).map { ChartBuckets.build(it, p, now) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object { const val KEY = "cdrive_chart_period" }
}
