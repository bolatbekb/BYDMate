package com.bydmate.app.fork.cells.collect

import android.util.Log
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.stream.CellReading
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

private const val TAG = "CellCollector"
private const val DAY_MS = 86_400_000L
private const val FULL_WRITE_GAP_MS = 60_000L
private const val FULL_SOC = 100
private const val COLD_C = 10
private const val REST_FULL_MIN = 60
private const val CHARGING_STATUS = 2
private const val GEAR_D = 4
private val CELL_MV = 2000..4000
private val PROBE_C = -40..125

/** Same ignition rule as the author's LoopFsm.classify: powerState when known, else D or moving. */
fun liveSampleOf(d: DiParsData, wallMs: Long, elapsedMs: Long): LiveSample = LiveSample(
    wallMs = wallMs,
    elapsedMs = elapsedMs,
    powerOn = d.powerState?.let { it in 1..2 } ?: (d.gear == GEAR_D || (d.speed ?: 0) > 0),
    soc = d.soc,
    currentA = d.hvCurrent,
    packV = d.hvVoltage,
    charging = d.chargingStatus == CHARGING_STATUS,
)

/** R24: every cell within 2000..4000 mV and every probe within −40..125 °C, else the reading is not stored. */
internal fun saneReading(r: CellReading.Complete): Boolean =
    r.cellsMv.isNotEmpty() && r.cellsMv.all { it in CELL_MV } && r.probesC.all { it in PROBE_C }

/** Whole days since the last 100 %; null when unknown or when the wall clock went back more than a day. */
internal fun daysSinceFull(wallMs: Long, lastFullMs: Long): Int? {
    if (lastFullMs <= 0L || wallMs < lastFullMs - DAY_MS) return null
    return ((wallMs - lastFullMs) / DAY_MS).toInt().coerceAtLeast(0)
}

/** SOC 100 marks the last full charge; written at most once a minute while it stays at 100. */
internal fun CellsSettings.noteFull(s: LiveSample) {
    if (s.soc != FULL_SOC) return
    val last = lastFull100WallMs
    if (s.wallMs < last || s.wallMs - last >= FULL_WRITE_GAP_MS) lastFull100WallMs = s.wallMs
}

/** The manual snapshot counts as a rest measurement only at rest current and not charging. */
internal fun restConditions(s: LiveSample): Boolean =
    s.currentA?.let { abs(it) <= CellSchedule.REST_A } == true && !s.charging

/** What was wrong when the driver took the snapshot; empty = good conditions. Unknown current is not «at rest». */
internal fun manualProblems(s: LiveSample, restMin: Int?, probeMinC: Int?): List<ManualProblem> = buildList {
    if (s.currentA?.let { abs(it) <= CellSchedule.REST_A } != true) add(ManualProblem.CURRENT)
    when {
        restMin == null -> add(ManualProblem.UNKNOWN_REST)
        restMin < REST_FULL_MIN -> add(ManualProblem.SHORT_REST)
    }
    if (s.charging) add(ManualProblem.CHARGING)
    if (probeMinC != null && probeMinC < COLD_C) add(ManualProblem.COLD)
}

/** The schedule's side of one stored row. */
internal data class RowConditions(
    val measure: MeasureKind?,
    val restMin: Int?,
    val restPeriod: Long?,
    val relax: Relax,
    val daysSinceFull: Int?,
    val flags: BmsFlags?,
)

/** One complete reading with its passport (the car state of [s] and the conditions [c]). */
internal fun snapshotRow(profile: CarProfile, s: LiveSample, r: CellReading.Complete, c: RowConditions): CellSnapshotEntity {
    val vmin = r.cellsMv.min()
    val vmax = r.cellsMv.max()
    return CellSnapshotEntity(
        ts = s.wallMs, elapsedMs = s.elapsedMs, profile = profile.id, measure = c.measure?.name,
        restPeriod = c.restPeriod, relax = c.relax.name, relaxDropMv = null,
        soc = s.soc, currentA = s.currentA, packV = s.packV, charging = s.charging,
        restMin = c.restMin, daysSinceFull = c.daysSinceFull,
        vminMv = vmin, vmaxMv = vmax, spreadMv = vmax - vmin,
        probeMinC = r.probesC.minOrNull(), probeMaxC = r.probesC.maxOrNull(),
        flagConsistency = c.flags?.consistency, flagTempDiff = c.flags?.tempDiff, flagUnbalanced = c.flags?.unbalanced,
        cells = CellBlob.encodeCells(r.cellsMv), probes = CellBlob.encodeProbes(r.probesC),
    )
}

/** The flags of the passport; a failed read only leaves them empty. */
internal suspend fun readFlags(flags: suspend () -> BmsFlags?): BmsFlags? {
    var out: BmsFlags? = null
    cellsStep("flags") { out = flags() }
    return out
}

/** One collector step: any failure is logged and the next step retries; only cancellation passes. */
@Suppress("TooGenericExceptionCaught") // why: the battery check must never crash the app; a failed step is logged, the next sample retries
internal inline fun cellsStep(op: String, block: () -> Unit) {
    try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        runCatching { Log.w(TAG, "cells $op failed: ${e.javaClass.simpleName}: ${e.message}") }
    }
}
