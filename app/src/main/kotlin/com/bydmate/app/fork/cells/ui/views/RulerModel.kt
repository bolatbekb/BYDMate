package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.Reason

// The model of «Линейка»: the scale of the measurement's zone and the four checks. fork-local.

enum class RulerCheckKind { SPREAD, WEAK_CELLS, PROBES, CONDITIONS }

data class RulerCheck(val kind: RulerCheckKind, val level: Level)

/** «Линейка»: the measurement's own limits (cold doubles them), the top of the scale and the four checks. */
data class RulerModel(val normMv: Int, val serviceMv: Int?, val topMv: Int, val valueMv: Int, val checks: List<RulerCheck>)

private const val COLD_C = 10

/** topMv = max((svc ?: norm × 2) × 1.3, spread × 1.15), rounded up; integer maths, so 90 × 1.3 is 117, not 118. */
fun rulerModel(e: Evaluation): RulerModel {
    val norm = e.limits.normMv
    val svc = e.limits.serviceMv
    val top = maxOf(ceilDiv((svc ?: norm * 2) * 13, 10), ceilDiv(e.spreadMv * 115, 100), 1)
    val checks = listOf(
        RulerCheck(RulerCheckKind.SPREAD, spreadLevel(e)),
        RulerCheck(RulerCheckKind.WEAK_CELLS, weakLevel(e)),
        RulerCheck(RulerCheckKind.PROBES, probeLevel(e)),
        RulerCheck(RulerCheckKind.CONDITIONS, conditionsLevel(e)),
    )
    return RulerModel(norm, svc, top, e.spreadMv, checks)
}

private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

/**
 * The health checks never say more than the verdict: a reason stays in the list when a cap (no recent full charge, cold,
 * preliminary) lowers the level, so «в сервис» needs the verdict itself to be «в сервис».
 */
private fun capped(level: Level, e: Evaluation): Level = if (e.level == Level.NONE) Level.NONE else minOf(level, e.level)

/** «В сервис» only with the confirmed repeat; one measurement above the service number stays «следить». */
private fun spreadLevel(e: Evaluation): Level = capped(
    when {
        Reason.SERVICE_TWICE in e.reasons -> Level.SERVICE
        e.spreadMv > e.limits.normMv -> Level.WATCH
        else -> Level.OK
    },
    e,
)

/** Cells below the median: any off-normal one or a laggard is worth watching; red only with the low-zone laggard rule. */
private fun weakLevel(e: Evaluation): Level = capped(
    when {
        Reason.LOW_WITH_LAGGARD in e.reasons -> Level.SERVICE
        e.laggards.isNotEmpty() || e.classes.any { it == CellClass.NOTICEABLY_LOW || it == CellClass.STRONGLY_LOW } -> Level.WATCH
        else -> Level.OK
    },
    e,
)

/**
 * R17: no sourced norm for the probe spread, only the cold rule (thresholds × 2 below 10 °C). Like «Условия замера», a
 * condition of the measurement, not of the cells: orange at most, never above «следить».
 */
private fun probeLevel(e: Evaluation): Level {
    val min = e.input.probesC.filter { it != NO_TEMP }.minOrNull() ?: return Level.NONE
    return if (min < COLD_C) Level.WATCH else Level.OK
}

private fun conditionsLevel(e: Evaluation): Level = when {
    e.level == Level.NONE -> Level.NONE
    e.preliminary -> Level.WATCH
    else -> Level.OK
}
