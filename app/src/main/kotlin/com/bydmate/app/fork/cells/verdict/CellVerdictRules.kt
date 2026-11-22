package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.Relax
import java.time.Instant
import java.time.ZoneId

private const val REST_MIN_VERDICT = 15
private const val REST_MIN_HONEST = 60
private const val TOP_FULL_DAYS = 7
private const val GROWTH_WATCH_MV = 30

/** Shape of one cell array: spread, median, extremes, 1-based indices of the lowest and highest cell. */
internal class Shape(val cellsMv: IntArray) {
    val vminMv = cellsMv.min()
    val vmaxMv = cellsMv.max()
    val spreadMv = vmaxMv - vminMv
    val medianMv = CellVerdict.median(cellsMv)
    val lowestCell = cellsMv.indexOf(vminMv) + 1
    val highestCell = cellsMv.indexOf(vmaxMv) + 1
    val deviationsMv = DoubleArray(cellsMv.size) { cellsMv[it] - medianMv }
}

/** What both verdict paths share: the measurement, its zone row, cold state and shape. */
internal class Base(val m: MeasurementInput, val row: ZoneRow, val cold: Boolean, val shape: Shape) {
    val k: Int get() = if (cold) 2 else 1
    val norm: Int get() = row.normMv * k
    val svc: Int? get() = row.serviceMv?.times(k)

    @Suppress("LongParameterList") // why: one assembly point for the result of both paths
    fun evaluation(
        level: Level,
        preliminary: Boolean,
        reasons: List<Reason>,
        laggards: List<Laggard> = emptyList(),
        twice: Boolean = false,
        baseMv: Int? = null,
        previousSpreadMv: Int? = null,
    ): Evaluation {
        val classes = CellVerdict.classify(shape.deviationsMv, row, cold)
        return Evaluation(
            m, level, preliminary, reasons, row, Limits(norm, svc, cold), shape.spreadMv, shape.medianMv, shape.vminMv, shape.vmaxMv,
            shape.lowestCell, shape.highestCell, shape.deviationsMv, classes, laggards,
            ServiceConditions(twice, laggards.isNotEmpty(), classes.contains(CellClass.STRONGLY_LOW)),
            baseMv, baseMv?.let { shape.spreadMv - it }, previousSpreadMv,
        )
    }
}

/** Mutable level and reasons of the rest path; [raise] and [cap] only move the level one way. */
internal class Draft {
    var level = Level.OK
    val reasons = mutableListOf<Reason>()
    fun raise(l: Level) { level = maxOf(level, l) }
    fun cap(l: Level) { level = minOf(level, l) }
}

/** The reason a rest measurement gets no verdict, or null. */
internal fun noVerdictReason(m: MeasurementInput): Reason? = when {
    m.charging -> Reason.CHARGING
    m.restMin != null && m.restMin < REST_MIN_VERDICT -> Reason.REST_SHORT
    !m.atRest -> Reason.REST_UNKNOWN
    else -> null
}

internal fun prelimReasons(m: MeasurementInput): List<Reason> = buildList {
    if (m.restMin == null) add(Reason.REST_UNKNOWN) else if (m.restMin < REST_MIN_HONEST) add(Reason.REST_PRELIM)
    if (m.relax == Relax.RELAXING) add(Reason.RELAXING)
    if (m.relax == Relax.PENDING || m.relax == Relax.UNKNOWN) add(Reason.RELAX_UNKNOWN)
}

private fun dateOf(ts: Long, zone: ZoneId) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

/** R13: another honest measurement of the same row at the row's service number on a different local date. */
internal fun serviceTwice(b: Base, spreads: List<SpreadPoint>, zone: ZoneId): Boolean {
    val limit = b.row.serviceMv ?: return false
    return spreads.any {
        it.id != b.m.id && it.row == b.row && !it.cold && !it.preliminary && it.spreadMv >= limit &&
            dateOf(it.ts, zone) != dateOf(b.m.ts, zone)
    }
}

/** Spread against the norm and the service number; returns whether the service condition was met twice. */
internal fun spreadRules(b: Base, d: Draft, twice: Boolean): Boolean {
    if (b.shape.spreadMv > b.norm) {
        d.level = Level.WATCH
        d.reasons.add(Reason.SPREAD_ABOVE_NORM)
    }
    val svc = b.svc ?: return false
    if (b.shape.spreadMv < svc) return false
    if (twice) {
        d.level = Level.SERVICE
        d.reasons.add(Reason.SERVICE_TWICE)
    } else {
        d.reasons.add(Reason.SERVICE_ONCE)
    }
    return twice
}

/** Zone-specific adjustments: top needs a recent full charge, the knee never serves, low serves with a laggard. */
internal fun zoneRules(b: Base, d: Draft, lag: List<Laggard>) {
    val spread = b.shape.spreadMv
    val noRecentFull = b.m.daysSinceFull.let { it == null || it > TOP_FULL_DAYS }
    if (b.row == ZoneRow.TOP && d.level == Level.SERVICE && noRecentFull) {
        d.level = Level.WATCH
        d.reasons.add(Reason.TOP_NO_RECENT_FULL)
    }
    if (b.row == ZoneRow.TOP_KNEE && spread > b.norm) d.reasons.add(Reason.KNEE_REMEASURE)
    val low = b.row == ZoneRow.LOW_10_15 || b.row == ZoneRow.LOW_UNDER_10
    if (low && spread > b.norm && lag.any { it.cell == b.shape.lowestCell }) {
        d.level = Level.SERVICE
        d.reasons.add(Reason.LOW_WITH_LAGGARD)
    }
}

/** Laggards, personal-base growth, cold and preliminary caps (R12, R14, R15). */
internal fun capRules(b: Base, d: Draft, lag: List<Laggard>, growth: Int?) {
    if (lag.isNotEmpty()) {
        d.raise(Level.WATCH)
        d.reasons.add(Reason.LAGGARD)
    }
    if ((growth ?: 0) > GROWTH_WATCH_MV) {
        d.raise(Level.WATCH)
        d.reasons.add(Reason.BASE_GROWTH)
    }
    if (b.cold) {
        d.cap(Level.WATCH)
        d.reasons.add(Reason.COLD)
    }
    val prelim = prelimReasons(b.m)
    if (prelim.isNotEmpty()) {
        d.cap(Level.WATCH)
        d.reasons.addAll(prelim)
    }
}
