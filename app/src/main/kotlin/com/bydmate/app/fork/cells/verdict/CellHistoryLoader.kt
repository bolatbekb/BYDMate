package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.SnapshotSummary

private const val WINDOW_DAYS = 120L
private const val DAY_MS = 86_400_000L
private const val NEWEST = 6
private val REST_KINDS = setOf(MeasureKind.AFTER_PARKING, MeasureKind.REST_IN_SESSION, MeasureKind.AFTER_CHARGE, MeasureKind.MANUAL)

/** Loads the history window of one measurement from the cells database and evaluates it. */
class CellHistoryLoader(private val repo: CellRepository) {
    suspend fun evaluate(id: Long, nowWallMs: Long): Evaluation? {
        val m = repo.entity(id)?.toInput() ?: return null
        val to = minOf(m.ts, nowWallMs)
        val from = m.ts - WINDOW_DAYS * DAY_MS
        // newest first; the latest measurement of each rest period wins, the current one and its period are left out
        val rest = repo.measurementsBetween(REST_KINDS, from, to).filter { it.restPeriod != null }
            .distinctBy { it.restPeriod }.filter { it.id != m.id && (m.restPeriod == null || it.restPeriod != m.restPeriod) }
        val restInputs = inputs(rest.take(NEWEST))
        val loads = inputs(repo.measurementsBetween(setOf(MeasureKind.LOAD), from, to).filter { it.id != m.id }.take(NEWEST))
        return CellVerdict.evaluate(m, History(restInputs, rest.map { it.toSpreadPoint() }, loads))
    }

    private suspend fun inputs(list: List<SnapshotSummary>): List<MeasurementInput> =
        repo.entities(list.map { it.id }).mapNotNull { it.toInput() }.sortedByDescending { it.ts }

    suspend fun latestRestId(): Long? = repo.latestRestId()

    suspend fun latestLoadId(): Long? = repo.latestLoadId()
}
