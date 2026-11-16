package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.data.local.dao.CounterSnapshotDao
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.RefuelEntity
import javax.inject.Inject
import kotlin.math.abs

enum class PrefillSource { LIVE, DRAFT, SNAPSHOT, NONE }

data class Prefill(val odometerKm: Double?, val carFuelTotalL: Double?, val source: PrefillSource)

/**
 * Picks odometer / fuel-counter values for a new entry. A draft close to the entry wins over live
 * data (the station values, not the ones after the drive home); live car data only for a fresh entry.
 */
class LedgerPrefill @Inject constructor(private val counterSnapshots: CounterSnapshotDao) {

    suspend fun forEntry(entryTs: Long, nowTs: Long, live: DiParsData?, draft: RefuelEntity?): Prefill {
        if (draft != null && abs(draft.date - entryTs) <= DRAFT_FIRST_WINDOW_MS) {
            return Prefill(draft.odometerKm, draft.carFuelTotalL, PrefillSource.DRAFT)
        }
        if (abs(entryTs - nowTs) <= LIVE_WINDOW_MS && live?.mileage != null) {
            return Prefill(live.mileage, live.fuelTotalL, PrefillSource.LIVE)
        }
        if (draft != null && abs(draft.date - entryTs) <= DRAFT_WINDOW_MS) {
            return Prefill(draft.odometerKm, draft.carFuelTotalL, PrefillSource.DRAFT)
        }
        val snap = counterSnapshots.nearest(entryTs, SNAPSHOT_WINDOW_MS)
        if (snap != null) return Prefill(snap.odometer, snap.fuelTotal, PrefillSource.SNAPSHOT)
        return Prefill(null, null, PrefillSource.NONE)
    }

    private companion object {
        const val LIVE_WINDOW_MS = 2 * 3_600_000L
        const val DRAFT_FIRST_WINDOW_MS = 2 * 3_600_000L
        const val DRAFT_WINDOW_MS = 12 * 3_600_000L
        /** Snapshots are written every 10 min while the car is on. */
        const val SNAPSHOT_WINDOW_MS = 3_600_000L
    }
}
