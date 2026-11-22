package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.stream.NO_TEMP

enum class CellClass { NORMAL, NOTICEABLY_LOW, NOTICEABLY_HIGH, STRONGLY_LOW, STRONGLY_HIGH }

enum class Reason {
    REST_SHORT, CHARGING, REST_PRELIM, REST_UNKNOWN, RELAXING, RELAX_UNKNOWN, COLD, SPREAD_ABOVE_NORM,
    SERVICE_ONCE, SERVICE_TWICE, TOP_NO_RECENT_FULL, KNEE_REMEASURE, LAGGARD, BASE_GROWTH, LOW_WITH_LAGGARD,
}

@Suppress("ArrayInDataClass", "LongParameterList") // why: plain value holder of a stored measurement, arrays are never compared
data class MeasurementInput(
    val id: Long,
    val ts: Long,
    val kind: MeasureKind,
    val cellsMv: IntArray,
    val probesC: IntArray,
    val soc: Int?,
    val currentA: Double?,
    val packV: Int?,
    val restMin: Int?,
    val charging: Boolean,
    val relax: Relax,
    val daysSinceFull: Int?,
    val restPeriod: Long?,
) {
    val atRest: Boolean get() = kind != MeasureKind.LOAD && restPeriod != null
}

private const val COLD_C = 10
private const val PRELIM_REST_MIN = 60

/** Honest = at rest, rest of an hour or more, relaxation not pending (same definition as [SpreadPoint.preliminary]). */
internal val MeasurementInput.honest: Boolean
    get() = atRest && restMin != null && restMin >= PRELIM_REST_MIN &&
        relax != Relax.RELAXING && relax != Relax.PENDING && relax != Relax.UNKNOWN

/** Probes below 10 °C double the thresholds; no probes means not cold. */
internal fun isCold(m: MeasurementInput): Boolean = m.probesC.filter { it != NO_TEMP }.minOrNull()?.let { it < COLD_C } ?: false

private fun parseRelax(s: String): Relax = runCatching { Relax.valueOf(s) }.getOrDefault(Relax.UNKNOWN)

private fun parseKind(s: String?): MeasureKind? = s?.let { runCatching { MeasureKind.valueOf(it) }.getOrNull() }

/** Null for a plain snapshot (no measure), an unknown measure or a reading without cells. */
fun CellSnapshotEntity.toInput(): MeasurementInput? {
    val kind = parseKind(measure) ?: return null
    val cellsMv = CellBlob.decodeCells(cells)
    if (cellsMv.isEmpty()) return null
    return MeasurementInput(
        id, ts, kind, cellsMv, CellBlob.decodeProbes(probes), soc, currentA, packV, restMin, charging,
        parseRelax(relax), daysSinceFull, restPeriod,
    )
}

data class SpreadPoint(
    val id: Long,
    val ts: Long,
    val restPeriod: Long?,
    val row: ZoneRow,
    val spreadMv: Int,
    val cold: Boolean,
    val preliminary: Boolean,
)

fun SnapshotSummary.toSpreadPoint(): SpreadPoint {
    val rx = parseRelax(relax)
    val prelim = restMin == null || restMin < PRELIM_REST_MIN ||
        rx == Relax.RELAXING || rx == Relax.PENDING || rx == Relax.UNKNOWN
    return SpreadPoint(id, ts, restPeriod, zoneOf(vmaxMv, soc), spreadMv, (probeMinC ?: Int.MAX_VALUE) < COLD_C, prelim)
}

/** All lists exclude the current measurement; rest lists are newest first. */
data class History(val rest: List<MeasurementInput>, val restSpreads: List<SpreadPoint>, val loads: List<MeasurementInput>)

/** [cell] is 1-based. */
data class Laggard(val cell: Int, val times: Int, val of: Int)

data class ServiceConditions(val twiceOnDifferentDays: Boolean, val laggardRepeats: Boolean, val cellBelowHalfService: Boolean)

data class Limits(val normMv: Int, val serviceMv: Int?, val cold: Boolean)

@Suppress("ArrayInDataClass", "LongParameterList") // why: result holder read by the UI
data class Evaluation(
    val input: MeasurementInput,
    val level: Level,
    val preliminary: Boolean,
    val reasons: List<Reason>,
    val row: ZoneRow,
    val limits: Limits,
    val spreadMv: Int,
    val medianMv: Double,
    val vminMv: Int,
    val vmaxMv: Int,
    val lowestCell: Int,
    val highestCell: Int,
    val deviationsMv: DoubleArray,
    val classes: List<CellClass>,
    val laggards: List<Laggard>,
    val service: ServiceConditions,
    val baseMv: Int?,
    val growthMv: Int?,
    val previousSpreadMv: Int?,
)
