package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.verdict.Evaluation

/**
 * Reads what «Выравнивание» draws, in bounded windows only: [CellRepository.summariesBetween] has no LIMIT, so it is asked
 * for at most 18 h at a time (three times at most: the measurement's window, the previous full charge's window and its
 * curve); parkings come from the LIMIT query of measurements. The repository is guarded: a failing read gives empty lists,
 * and the chart then says it has nothing to draw. Called on the IO dispatcher. fork-local.
 */
internal class EqualizationLoader(private val repo: CellRepository) {
    suspend fun load(e: Evaluation): EqualizationData = when (eqModeOf(e)) {
        EqMode.AFTER_CHARGE -> afterCharge(e.input.ts)
        EqMode.PARKINGS -> parkings(e.input.ts)
    }

    /** R22: the charge end is the latest charging snapshot of the 18 h before the measurement; without one, the wake. */
    private suspend fun afterCharge(ts: Long): EqualizationData {
        val window = repo.summariesBetween(ts - EQ_WINDOW_MS, ts)
        val anchor = window.lastOrNull { it.charging }?.ts
        val start = anchor ?: wakeStart(window, ts)
        val previous = previousFull(start)
        // only the curve is kept in the window's state: the model finds the same start in it
        return EqualizationData(EqMode.AFTER_CHARGE, window.filter { it.ts >= start }, anchor, previous?.second.orEmpty(), previous?.first)
    }

    /**
     * The last earlier period that reached SOC 100 (or a full charge that day): its after-charge measurement, the start of its
     * curve found the same way, and its snapshots from there for 18 h, cut where the current curve begins or where a later
     * charge starts (a partial charge after it is not part of its equalization).
     */
    private suspend fun previousFull(before: Long): Pair<Long, List<SnapshotSummary>>? {
        val m = repo.measurementsBetweenLimit(FULL_KINDS, 0L, before - 1, FULL_LOOKUP)
            .firstOrNull { (it.soc ?: 0) >= FULL_SOC || it.daysSinceFull == 0 } ?: return null
        val window = repo.summariesBetween(m.ts - EQ_WINDOW_MS, m.ts)
        val origin = window.lastOrNull { it.charging }?.ts ?: wakeStart(window, m.ts)
        val rows = repo.summariesBetween(origin, minOf(origin + EQ_WINDOW_MS, before - 1))
        return origin to rows.takeWhile { it.ts <= origin || !it.charging }
    }

    /** Rest measurements of the 14 days up to the measurement, oldest first; the model keeps one per rest period. */
    private suspend fun parkings(ts: Long): EqualizationData {
        val rows = repo.measurementsBetweenLimit(REST_KINDS, ts - PARKING_DAYS * DAY_MS, ts, PARKING_ROWS)
        return EqualizationData(EqMode.PARKINGS, rows.reversed(), null, emptyList(), null)
    }

    private companion object {
        const val FULL_SOC = 100
        const val FULL_LOOKUP = 30
        const val PARKING_ROWS = 200
        val FULL_KINDS = setOf(MeasureKind.AFTER_CHARGE)
        val REST_KINDS = setOf(MeasureKind.AFTER_PARKING, MeasureKind.REST_IN_SESSION, MeasureKind.AFTER_CHARGE, MeasureKind.MANUAL)
    }
}
