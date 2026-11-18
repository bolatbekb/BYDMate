package com.bydmate.app.ui.home.graphite

import android.content.Context
import androidx.room.withTransaction
import com.bydmate.app.data.local.database.AppDatabase
import com.bydmate.app.data.local.entity.TripEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** What to write into one recorded trip: only the non-null fields are to be filled. */
data class Fill(val tripId: Long, val fuelLiters: Double?, val hevKm: Double?)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TripBackfillEntryPoint {
    fun database(): AppDatabase
}

/**
 * Fills a recorded trip's missing litres and engine km from the live estimate it overlaps. Never overwrites a value
 * the author's code wrote; the row is re-read right before the write.
 */
object TripBackfill {
    const val WINDOW_MS = 31L * 24 * 3_600_000
    private const val DEBOUNCE_MS = 5_000L
    @Volatile private var started = false

    /** The plan: for each recent row with a gap, the share of its best-overlapping estimate (as in [TripMatch.overlapMs]). */
    fun plan(rows: List<TripEntity>, estimates: List<TripEstimate>, nowMs: Long): List<Fill> =
        rows.mapNotNull { r ->
            if (r.startTs < nowMs - WINDOW_MS) return@mapNotNull null
            if (r.fuelLiters != null && r.hevKm != null) return@mapNotNull null
            val rk = r.distanceKm?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            val row = r.toRow()
            val e = estimates.maxByOrNull { TripMatch.overlapMs(row, it) }
                ?.takeIf { TripMatch.overlapMs(row, it) > 0L } ?: return@mapNotNull null
            if (!(e.km > 0.0)) return@mapNotNull null
            val share = minOf(1.0, rk / e.km)
            val litres = if (r.fuelLiters == null) e.litres?.times(share) else null
            val hev = if (r.hevKm == null) e.iceKm?.times(share) else null
            if (litres == null && hev == null) null else Fill(r.id, litres, hev)
        }

    /**
     * Writes the fills; returns how many rows were changed. Each row is re-read and updated inside one Room
     * transaction, so the author's whole-row writes cannot land between the read and our write; fields that
     * became non-null meanwhile are skipped. A failure on one row does not stop the others.
     */
    suspend fun apply(
        db: AppDatabase, fills: List<Fill>, nowMs: Long,
        log: (Long, String, List<Pair<String, Any?>>) -> Unit = TripLog::event,
    ): Int {
        var written = 0
        for (f in fills) {
            val done = try {
                db.withTransaction {
                    val dao = db.tripDao()
                    val cur = dao.getById(f.tripId)
                    val litres = f.fuelLiters?.takeIf { cur != null && cur.fuelLiters == null }
                    val hev = f.hevKm?.takeIf { cur != null && cur.hevKm == null }
                    if (cur != null && (litres != null || hev != null)) {
                        dao.update(cur.copy(fuelLiters = litres ?: cur.fuelLiters, hevKm = hev ?: cur.hevKm))
                        Triple(f.tripId, litres, hev)
                    } else null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log(nowMs, "backfill_error", listOf("id" to f.tripId, "e" to e.javaClass.simpleName))
                null
            }
            if (done != null) {
                written++
                log(nowMs, "backfill", listOf("id" to done.first, "litres" to done.second, "hevKm" to done.third))
            }
        }
        return written
    }

    suspend fun pass(db: AppDatabase, rows: List<TripEntity>, estimates: List<TripEstimate>, nowMs: Long): Int =
        apply(db, plan(rows, estimates, nowMs), nowMs)

    /** Re-runs on a trips-table change or a changed finished list, not on every live-state publish. */
    internal fun trigger(rows: Flow<List<TripEntity>>, finished: Flow<List<TripEstimate>>): Flow<Pair<List<TripEntity>, List<TripEstimate>>> =
        combine(rows, finished.distinctUntilChanged()) { r, f -> r to f }

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = EntryPointAccessors.fromApplication(app, TripBackfillEntryPoint::class.java).database()
                // The lower bound is fixed at start and only grows the window; plan() applies the exact 31 days.
                val rows = db.tripDao().getByDateRange(System.currentTimeMillis() - WINDOW_MS, Long.MAX_VALUE)
                trigger(rows, LiveTrip.state.map { it.finished })
                    .debounce(DEBOUNCE_MS)
                    .collect { (r, finished) ->
                        try {
                            pass(db, r, finished, System.currentTimeMillis())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            TripLog.event(System.currentTimeMillis(), "backfill_error", listOf("e" to e.javaClass.simpleName))
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                started = false
                TripLog.event(System.currentTimeMillis(), "backfill_start_failed", listOf("e" to e.javaClass.simpleName))
            }
        }
    }
}
