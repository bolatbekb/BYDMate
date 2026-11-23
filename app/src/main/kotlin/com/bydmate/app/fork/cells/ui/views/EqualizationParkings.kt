package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.ui.pointLevel
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.fork.cells.verdict.toSpreadPoint

// «Выравнивание» outside the top zone: the rest spread of two weeks against the plateau lines. fork-local.

internal const val PARKING_DAYS = 14
internal const val DAY_MS = 86_400_000L
private const val LIST_CELLS = 5

/**
 * One dot per rest period (the latest measurement of it, R8) of the 14 days up to the measurement, x = 14 at the measurement.
 * Only plateau measurements: the lines are the plateau's (the measurement's own limits when it is a plateau one), a top or
 * low spread would read wrong against them. A selected measurement outside the plateau is not drawn; the view says its value.
 */
internal fun parkingsModel(e: Evaluation, d: EqualizationData, levels: Map<Long, Level>): EqualizationModel {
    val me = e.input
    val from = me.ts - PARKING_DAYS * DAY_MS
    val rows = d.points.asSequence()
        .filter { it.ts in from..me.ts && it.restPeriod != null }
        .sortedByDescending { it.ts }
        .distinctBy { it.restPeriod }
        .filter { it.id != me.id && it.toSpreadPoint().row == ZoneRow.PLATEAU }
        .toList()
    val points = rows.map { parking(it, me.ts, levels[it.id]) }.toMutableList()
    if (me.atRest && e.row == ZoneRow.PLATEAU) points += mine(e)
    val (norm, svc) = eqLines(e, EqMode.PARKINGS)
    return EqualizationModel(
        EqMode.PARKINGS, false, points.sortedBy { it.x }, emptyList(), norm, svc, PARKING_DAYS.toDouble(), extremeCells(e, highest = false),
    )
}

private fun mine(e: Evaluation) = EqPoint(PARKING_DAYS.toDouble(), e.spreadMv, false, e.level, e.preliminary, current = true)

/** Without a known verdict the dot takes only the norm band of its own zone: never red (as the history tab). */
private fun parking(s: SnapshotSummary, ts: Long, known: Level?): EqPoint {
    val p = s.toSpreadPoint()
    return EqPoint(PARKING_DAYS - (ts - s.ts) / DAY_MS.toDouble(), s.spreadMv, false, pointLevel(p, known), p.preliminary)
}

/** The five cells farthest above ([highest]) or below the median, farthest first; equal ones by number. */
internal fun extremeCells(e: Evaluation, highest: Boolean): List<Pair<Int, Double>> =
    e.deviationsMv.withIndex()
        .filter { if (highest) it.value > 0 else it.value < 0 }
        .sortedWith(compareBy<IndexedValue<Double>>({ if (highest) -it.value else it.value }, { it.index }))
        .take(LIST_CELLS)
        .map { it.index + 1 to it.value }
