package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.data.local.entity.TripPointEntity
import com.bydmate.app.ui.home.cdrive.ChartBuckets
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.roundToInt

/** [litres] == null means the car has no fuel data at all: the fuel line and share are hidden. */
data class EnergySeries(
    val labels: List<String>,
    val kwh: List<Double>,
    val litres: List<Double>?,
    val current: Int,
    val totalKwh: Double,
    val totalLitres: Double?,
    val totalKm: Double,
    val kwhPer100: Double?,
    val litresPer100: Double?,
    val electricShare: Int?,
    val empty: Boolean,
)

private fun TripEntity.km(): Double? = distanceKm?.takeIf { it.isFinite() && it > 0.0 }

/** Share of km driven on electricity, 0..100; null when no trip carries an engine-km figure. */
private fun electricShare(trips: List<TripEntity>): Int? {
    var dist = 0.0; var hev = 0.0; var any = false
    for (t in trips) {
        val km = t.km() ?: continue
        val h = t.hevKm?.takeIf { it.isFinite() } ?: continue
        dist += km; hev += h; any = true
    }
    if (!any) return null
    return (100.0 * (1.0 - hev / dist)).roundToInt().coerceIn(0, 100)
}

object EnergyBuckets {
    private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    fun build(trips: List<TripEntity>, period: ChartPeriod, now: ZonedDateTime): EnergySeries {
        val zone = now.zone
        val startDate = ChartBuckets.windowStart(period, now).toLocalDate()
        val n = when (period) {
            ChartPeriod.DAY -> 12
            ChartPeriod.WEEK -> 7
            ChartPeriod.MONTH -> 30
        }
        val kwh = DoubleArray(n)
        val litres = DoubleArray(n)
        var kwhSum = 0.0; var kwhKm = 0.0
        var litSum = 0.0; var litKm = 0.0; var anyFuel = false
        var totalKm = 0.0
        val used = ArrayList<TripEntity>()
        for (t in trips) {
            val km = t.km() ?: continue
            val local = Instant.ofEpochMilli(t.startTs).atZone(zone)
            val i = if (period == ChartPeriod.DAY) {
                if (local.toLocalDate() != startDate) continue
                local.hour / 2
            } else {
                val d = ChronoUnit.DAYS.between(startDate, local.toLocalDate())
                if (d < 0 || d >= n) continue
                d.toInt()
            }
            used += t
            totalKm += km
            t.kwhConsumed?.takeIf { it.isFinite() }?.let { kwh[i] += it; kwhSum += it; kwhKm += km }
            t.fuelLiters?.takeIf { it.isFinite() }?.let {
                litres[i] += it; litSum += it; litKm += km; anyFuel = true
            }
        }
        val labels = List(n) { i ->
            when (period) {
                ChartPeriod.DAY -> "%02d".format(i * 2)
                ChartPeriod.WEEK -> WEEKDAYS[startDate.plusDays(i.toLong()).dayOfWeek.value - 1]
                ChartPeriod.MONTH -> startDate.plusDays(i.toLong()).dayOfMonth.toString()
            }
        }
        val current = when (period) {
            ChartPeriod.DAY -> now.hour / 2
            ChartPeriod.WEEK -> 6
            ChartPeriod.MONTH -> 29
        }
        return EnergySeries(
            labels = labels,
            // A net-regen bucket plots as zero; the totals keep the raw sums.
            kwh = kwh.map { it.coerceAtLeast(0.0) },
            litres = if (anyFuel) litres.map { it.coerceAtLeast(0.0) } else null,
            current = current,
            totalKwh = kwhSum.coerceAtLeast(0.0),
            totalLitres = if (anyFuel) litSum else null,
            totalKm = totalKm,
            kwhPer100 = if (kwhKm > 0.0) (kwhSum / kwhKm * 100.0).coerceAtLeast(0.0) else null,
            litresPer100 = if (anyFuel && litKm > 0.0) litSum / litKm * 100.0 else null,
            electricShare = if (anyFuel) electricShare(used) else null,
            empty = used.isEmpty(),
        )
    }
}

data class TodayStats(
    val minutes: Int,
    val avgKmh: Int?,
    val electricShare: Int?,
    val kwh: Double?,
    val litres: Double?,
)

object TodayStatsCalc {
    fun from(trips: List<TripEntity>, todayKm: Double?): TodayStats {
        var ms = 0L; var kmDriven = 0.0
        var kwh = 0.0; var anyKwh = false
        var lit = 0.0; var anyFuel = false
        for (t in trips) {
            val end = t.endTs
            if (end != null && end > t.startTs) {
                ms += end - t.startTs
                t.km()?.let { kmDriven += it }
            }
            t.kwhConsumed?.takeIf { it.isFinite() }?.let { kwh += it; anyKwh = true }
            t.fuelLiters?.takeIf { it.isFinite() }?.let { lit += it; anyFuel = true }
        }
        val minutes = (ms / 60_000.0).roundToInt()
        // Finished trips only: km and hours must cover the same trips. todayKm is kept for callers.
        val avg = if (minutes > 0 && kmDriven > 0.0) (kmDriven / (ms / 3_600_000.0)).roundToInt() else null
        return TodayStats(
            minutes = minutes,
            avgKmh = avg,
            electricShare = if (anyFuel) electricShare(trips) else null,
            kwh = if (anyKwh) kwh.coerceAtLeast(0.0) else null,
            litres = if (anyFuel) lit else null,
        )
    }
}

object Regen {
    private const val GAP_CAP_MS = 10_000L

    /** Energy recovered while moving, kWh; null when no point carries battery power. */
    fun kwh(points: List<TripPointEntity>): Double? {
        if (points.none { it.batteryPowerW != null }) return null
        val sorted = points.sortedBy { it.timestamp }
        var wattMs = 0.0
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]; val b = sorted[i + 1]
            val w = a.batteryPowerW ?: continue
            if (w < 0 && (a.speedKmh ?: 0.0) > 0.0) {
                wattMs += -w * min(b.timestamp - a.timestamp, GAP_CAP_MS)
            }
        }
        return wattMs / 3_600_000_000.0
    }
}
