package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.ui.home.cdrive.ChartBuckets
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import java.time.Instant
import java.time.ZonedDateTime
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
    /** The period these buckets are for; the chart grows in again when it changes. */
    val period: ChartPeriod,
)

private fun TripRow.drivenKm(): Double? = km?.takeIf { it.isFinite() && it > 0.0 }

/** Share of km driven on electricity, 0..100; null when no trip carries an engine-km figure. */
private fun electricShare(trips: List<TripRow>): Int? {
    // km and engine km of the trips that have both
    val pairs = trips.mapNotNull { t -> t.drivenKm()?.let { km -> t.hevKm?.takeIf { it.isFinite() }?.let { km to it } } }
    if (pairs.isEmpty()) return null
    val dist = pairs.sumOf { it.first }
    val hev = pairs.sumOf { it.second }
    return (100.0 * (1.0 - hev / dist)).roundToInt().coerceIn(0, 100)
}

object EnergyBuckets {
    /** [lang] names the week's days; the other periods label with numbers. */
    fun build(trips: List<TripEntity>, period: ChartPeriod, now: ZonedDateTime, lang: GrLang = GrLang.RU): EnergySeries =
        build(trips.map { it.toRow() }, period, now, lang)
}

/** The main build: recorded trips and estimates alike. A member overload wins for TripEntity lists. */
fun EnergyBuckets.build(rows: List<TripRow>, period: ChartPeriod, now: ZonedDateTime, lang: GrLang = GrLang.RU): EnergySeries {
    val startDate = ChartBuckets.windowStart(period, now).toLocalDate()
    val n = when (period) {
        ChartPeriod.DAY -> 12
        ChartPeriod.WEEK -> 7
        ChartPeriod.MONTH -> 30
    }
    val tally = EnergyTally(n)
    for (t in rows) {
        val km = t.drivenKm() ?: continue
        val local = Instant.ofEpochMilli(t.startMs).atZone(now.zone)
        ChartBuckets.bucketOf(local, period, startDate, n, hoursPerBucket = 2)?.let { tally.add(t, km, it) }
    }
    val labels = List(n) { i ->
        when (period) {
            ChartPeriod.DAY -> "%02d".format(i * 2)
            ChartPeriod.WEEK -> lang.words.weekdays[startDate.plusDays(i.toLong()).dayOfWeek.value - 1]
            ChartPeriod.MONTH -> startDate.plusDays(i.toLong()).dayOfMonth.toString()
        }
    }
    val current = when (period) {
        ChartPeriod.DAY -> now.hour / 2
        ChartPeriod.WEEK -> 6
        ChartPeriod.MONTH -> 29
    }
    return tally.series(labels, current, period)
}

/** The sums of one [EnergyBuckets.build]: per bucket and over the window. */
private class EnergyTally(n: Int) {
    private val kwh = DoubleArray(n)
    private val litres = DoubleArray(n)
    private var kwhSum = 0.0
    private var kwhKm = 0.0
    private var litSum = 0.0
    private var litKm = 0.0
    private var anyFuel = false
    private var totalKm = 0.0
    private val used = ArrayList<TripRow>()

    fun add(t: TripRow, km: Double, i: Int) {
        used += t
        totalKm += km
        t.kwh?.takeIf { it.isFinite() }?.let { kwh[i] += it; kwhSum += it; kwhKm += km }
        t.litres?.takeIf { it.isFinite() }?.let {
            litres[i] += it; litSum += it; litKm += km; anyFuel = true
        }
    }

    fun series(labels: List<String>, current: Int, period: ChartPeriod) = EnergySeries(
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
        period = period,
    )
}

data class TodayStats(
    val minutes: Int,
    val avgKmh: Int?,
    val electricShare: Int?,
    val kwh: Double?,
    val litres: Double?,
)

object TodayStatsCalc {
    fun from(trips: List<TripEntity>): TodayStats {
        var ms = 0L; var kmDriven = 0.0
        var kwh = 0.0; var anyKwh = false
        var lit = 0.0; var anyFuel = false
        val rows = trips.map { it.toRow() }
        for (t in rows) {
            val end = t.endMs
            if (end != null && end > t.startMs) {
                ms += end - t.startMs
                t.drivenKm()?.let { kmDriven += it }
            }
            t.kwh?.takeIf { it.isFinite() }?.let { kwh += it; anyKwh = true }
            t.litres?.takeIf { it.isFinite() }?.let { lit += it; anyFuel = true }
        }
        val minutes = (ms / 60_000.0).roundToInt()
        // Finished trips only: km and hours must cover the same trips.
        val avg = if (minutes > 0 && kmDriven > 0.0) (kmDriven / (ms / 3_600_000.0)).roundToInt() else null
        return TodayStats(
            minutes = minutes,
            avgKmh = avg,
            electricShare = if (anyFuel) electricShare(rows) else null,
            kwh = if (anyKwh) kwh.coerceAtLeast(0.0) else null,
            litres = if (anyFuel) lit else null,
        )
    }
}
