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
data class TripFill(val tripId: Long, val fuelLiters: Double?, val hevKm: Double?)

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

    /** The estimate must have seen at least this share of the row's own duration. */
    const val MIN_COVERAGE = 0.8
    private const val MAX_LITRES = 60.0

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0

    /**
     * The plan: for each recent finished row with a gap, the estimate that covers at least [MIN_COVERAGE] of the row's
     * duration (the largest overlap wins). Values scale by the rate `row km / estimate km`, uncapped: litres to 0..60 L,
     * hev km (the engine km) to 0..row km, both rounded to 0.1.
     */
    fun plan(rows: List<TripEntity>, estimates: List<TripEstimate>, nowMs: Long): List<TripFill> =
        rows.mapNotNull { r -> if (r.startTs < nowMs - WINDOW_MS) null else planRow(r, estimates) }

    private fun planRow(r: TripEntity, estimates: List<TripEstimate>): TripFill? {
        if (r.fuelLiters != null && r.hevKm != null) return null
        val end = r.endTs ?: return null
        val dur = end - r.startTs
        val rk = r.distanceKm?.takeIf { it.isFinite() && it > 0.0 }
        val best = estimates.maxByOrNull { overlap(r.startTs, end, it) }
        if (dur <= 0L || rk == null || best == null) return null
        if (!(best.km > 0.0)) return null
        val o = overlap(r.startTs, end, best)
        if (o <= TripMatch.MIN_OVERLAP_MS || o < MIN_COVERAGE * dur) return null
        val ratio = rk / best.km
        val litres = scaled(r.fuelLiters == null, best.litres, ratio, MAX_LITRES)
        val hev = scaled(r.hevKm == null, best.hevKm, ratio, rk)
        return if (litres == null && hev == null) null else TripFill(r.id, litres, hev)
    }

    /** [v] × [ratio] clamped to 0..[max] and rounded to 0.1, or null when [wanted] is false or there is no value. */
    private fun scaled(wanted: Boolean, v: Double?, ratio: Double, max: Double): Double? =
        if (!wanted) null else v?.times(ratio)?.takeIf { it.isFinite() }?.coerceIn(0.0, max)?.let(::round1)

    private fun overlap(start: Long, end: Long, e: TripEstimate): Long = minOf(end, e.endMs) - maxOf(start, e.startMs)

    /**
     * Writes the fills; returns how many rows were changed. Each row is re-read and updated inside one Room
     * transaction, so the author's whole-row writes cannot land between the read and our write; fields that
     * became non-null meanwhile are skipped. A failure on one row does not stop the others.
     */
    suspend fun write(
        db: AppDatabase, fills: List<TripFill>, nowMs: Long,
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
        write(db, plan(rows, estimates, nowMs), nowMs)

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
