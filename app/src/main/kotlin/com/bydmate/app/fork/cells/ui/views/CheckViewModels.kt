package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.Reason
import com.bydmate.app.fork.cells.verdict.ServiceConditions
import com.bydmate.app.fork.cells.verdict.ZoneRow
import kotlin.math.log10

// The pure models of the window's four views: what each one shows for a measurement. fork-local.

enum class Advice { NOTHING_TO_DO, WATCH, SERVICE, NO_VERDICT, WAIT_RELAX, PRELIMINARY }

enum class Recheck { AFTER_CHARGE_HOUR, AFTER_PARKING_HOUR, ON_PLATEAU, AFTER_FULL_AC, WARM, MONTHLY }

/**
 * One row of «Что считается нормой»: [labelKey] names the charge band; the numbers are the research thresholds, the
 * measurement's own limits on its [current] row (doubled in the cold).
 */
data class NormRow(val labelKey: String, val row: ZoneRow, val normMv: Int, val serviceMv: Int?, val current: Boolean)

/**
 * How a service condition reads: [UNKNOWN] without a verdict (neutral), [NO], [MET] while the verdict is below «в сервис»
 * (orange: a condition alone is not a service), [MET_SERVICE] when the verdict is «в сервис» (red).
 */
enum class ConditionMark { UNKNOWN, NO, MET, MET_SERVICE }

/**
 * «Что делать»: the advice, the six recheck rows (true = lit), the norm table, the spread and the three service conditions
 * with their [marks] (twice, laggard, half the service number); [cold]: the current row's numbers are doubled.
 */
data class WhatToDoModel(
    val advice: Advice,
    val recheck: List<Pair<Recheck, Boolean>>,
    val norms: List<NormRow>,
    val yoursMv: Int?,
    val service: ServiceConditions,
    val marks: List<ConditionMark> = List(3) { ConditionMark.UNKNOWN },
    val cold: Boolean = false,
)

private val NORM_ROWS = listOf(
    "low_under_10" to ZoneRow.LOW_UNDER_10,
    "low_10_15" to ZoneRow.LOW_10_15,
    "plateau" to ZoneRow.PLATEAU,
    "top_knee" to ZoneRow.TOP_KNEE,
    "top" to ZoneRow.TOP,
)

private val NO_CONDITIONS = ServiceConditions(twiceOnDifferentDays = false, laggardRepeats = false, cellBelowHalfService = false)

fun whatToDoModel(e: Evaluation?): WhatToDoModel {
    val norms = NORM_ROWS.map { (key, row) ->
        if (e?.row == row) NormRow(key, row, e.limits.normMv, e.limits.serviceMv, true) else NormRow(key, row, row.normMv, row.serviceMv, false)
    }
    if (e == null) return WhatToDoModel(Advice.NO_VERDICT, Recheck.entries.map { it to false }, norms, null, NO_CONDITIONS)
    val met = listOf(e.service.twiceOnDifferentDays, e.service.laggardRepeats, e.service.cellBelowHalfService)
    val marks = met.map { m ->
        when {
            e.level == Level.NONE -> ConditionMark.UNKNOWN
            !m -> ConditionMark.NO
            e.level == Level.SERVICE -> ConditionMark.MET_SERVICE
            else -> ConditionMark.MET
        }
    }
    return WhatToDoModel(adviceOf(e), Recheck.entries.map { it to lit(it, e) }, norms, e.spreadMv, e.service, marks, e.limits.cold)
}

/** Order matters: no verdict first, then a pack still settling after a charge, then the level. */
fun adviceOf(e: Evaluation): Advice = when {
    e.level == Level.NONE -> Advice.NO_VERDICT
    Reason.RELAXING in e.reasons -> Advice.WAIT_RELAX
    e.preliminary && e.level == Level.OK -> Advice.PRELIMINARY
    e.level == Level.OK -> Advice.NOTHING_TO_DO
    e.level == Level.WATCH -> Advice.WATCH
    else -> Advice.SERVICE
}

/** Which recheck row the measurement's reasons point at; a charge in progress also waits for the hour after it. */
private fun lit(r: Recheck, e: Evaluation): Boolean {
    val why = e.reasons
    return when (r) {
        Recheck.AFTER_CHARGE_HOUR -> Reason.RELAXING in why || Reason.RELAX_UNKNOWN in why || Reason.CHARGING in why
        Recheck.AFTER_PARKING_HOUR -> Reason.REST_SHORT in why || Reason.REST_PRELIM in why || Reason.REST_UNKNOWN in why
        Recheck.ON_PLATEAU -> Reason.KNEE_REMEASURE in why
        Recheck.AFTER_FULL_AC -> Reason.TOP_NO_RECENT_FULL in why
        Recheck.WARM -> Reason.COLD in why
        Recheck.MONTHLY -> e.level == Level.OK && !e.preliminary
    }
}

/** A charge band of the «Норма по заряду» chart: SOC from–to, the norm and the service number. */
data class NormZone(val socFrom: Int, val socTo: Int, val normMv: Int, val serviceMv: Int?)

/**
 * [here] and [previous] are (SOC, spread); [previous] is the earlier measurement of the same zone (its SOC is not stored).
 * [lit] is the index of the measurement's zone, which carries the measurement's own limits ([cold]: doubled); -1 for none.
 */
data class NormChartModel(
    val zones: List<NormZone>,
    val here: Pair<Int, Int>?,
    val level: Level?,
    val previous: Pair<Int, Int>?,
    val lit: Int = -1,
    val cold: Boolean = false,
)

val NORM_ZONES: List<NormZone> = listOf(NormZone(0, 10, 150, null), NormZone(10, 15, 50, null), NormZone(15, 85, 20, 90), NormZone(85, 100, 150, 300))

/** The SOC drawn for a measurement without one (R10 zones by Vmax only): the middle of its band. */
private fun socOf(e: Evaluation): Int = e.input.soc ?: when (e.row) {
    ZoneRow.TOP, ZoneRow.TOP_KNEE -> 95
    ZoneRow.LOW_10_15 -> 12
    ZoneRow.LOW_UNDER_10 -> 5
    ZoneRow.PLATEAU, ZoneRow.LOAD -> 50
}

/** The band of [NORM_ZONES] a zone row belongs to; -1 for a load. */
private fun zoneIndex(z: ZoneRow): Int = when (z) {
    ZoneRow.LOW_UNDER_10 -> 0
    ZoneRow.LOW_10_15 -> 1
    ZoneRow.PLATEAU -> 2
    ZoneRow.TOP, ZoneRow.TOP_KNEE -> 3
    ZoneRow.LOAD -> -1
}

/** The warm bands of [NORM_ZONES], the measurement's band with its own limits; a load measurement has no point (rest norms). */
fun normChartModel(e: Evaluation?): NormChartModel {
    if (e == null || e.row == ZoneRow.LOAD) return NormChartModel(NORM_ZONES, null, e?.level, null)
    val soc = socOf(e).coerceIn(0, 100)
    val lit = zoneIndex(e.row)
    val zones = NORM_ZONES.mapIndexed { i, z -> if (i == lit) z.copy(normMv = e.limits.normMv, serviceMv = e.limits.serviceMv) else z }
    return NormChartModel(zones, soc to e.spreadMv, e.level, e.previousSpreadMv?.let { soc to it }, lit, e.limits.cold)
}

private const val LOG_SHIFT = 6.0
private const val LOG_TOP = 326.0

/** The chart's compressed scale (mockup V1): 0 → 0, 320 mV → 1, clamped. */
fun normChartY(mv: Int): Float {
    val y = (log10(mv.coerceAtLeast(0) + LOG_SHIFT) - log10(LOG_SHIFT)) / (log10(LOG_TOP) - log10(LOG_SHIFT))
    return y.toFloat().coerceIn(0f, 1f)
}
