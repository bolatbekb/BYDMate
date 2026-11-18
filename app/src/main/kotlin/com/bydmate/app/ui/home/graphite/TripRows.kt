package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripEntity

/** A trip for the chart and the card: recorded or estimated. */
data class TripRow(
    val startMs: Long,
    val endMs: Long?,
    val km: Double?,
    val kwh: Double?,
    val litres: Double?,
    val hevKm: Double?,
)

/** The only place the fork's Graphite code reads the author's [TripEntity]. */
fun TripEntity.toRow() = TripRow(startTs, endTs, distanceKm, kwhConsumed, fuelLiters, hevKm)

fun TripEstimate.toRow() = TripRow(startMs, endMs, km, kwh, litres, hevKm)

/** Tells which estimates the database already holds as recorded trips. */
object TripMatch {
    const val MIN_OVERLAP_MS = 30_000L

    /**
     * A recorded trip with km > 0 overlapping [e] by more than [MIN_OVERLAP_MS]; one still open
     * (no end) confirms when its start lies within [e].
     */
    fun confirmed(e: TripEstimate, recorded: List<TripRow>): Boolean = recorded.any { r ->
        if (r.km?.takeIf { it.isFinite() && it > 0.0 } == null) return@any false
        val end = r.endMs
        if (end == null) r.startMs in e.startMs..e.endMs
        else minOf(end, e.endMs) - maxOf(r.startMs, e.startMs) > MIN_OVERLAP_MS
    }

    /** The open session (its estimate up to [nowMs]) and the finished ones that moved and are not recorded yet. */
    fun pending(live: LiveTripState, recorded: List<TripRow>, nowMs: Long): List<TripEstimate> =
        (listOfNotNull(live.open?.estimate()?.copy(endMs = nowMs)) + live.finished)
            .filter { movedKm(it.km) && !confirmed(it, recorded) }

    /** Overlap in ms when it counts as a match (as in [confirmed]), else 0. */
    private fun overlapMs(r: TripRow, e: TripEstimate): Long {
        val end = r.endMs
        if (end == null) return if (r.startMs in e.startMs..e.endMs) (e.endMs - r.startMs).coerceAtLeast(1L) else 0L
        val o = minOf(end, e.endMs) - maxOf(r.startMs, e.startMs)
        return if (o > MIN_OVERLAP_MS) o else 0L
    }

    /**
     * Fills a recorded trip's gaps from the live estimate it overlaps (the one with the largest overlap
     * above [MIN_OVERLAP_MS]; one estimate may serve several fragments). The recorded km stays; kWh and
     * litres are taken from the estimate only when the record has none, and the engine km is the
     * estimate's ICE km, all times the row's share min(1, R.km / E.km). Rows with no estimate are unchanged.
     */
    fun merge(recorded: List<TripRow>, estimates: List<TripEstimate>): List<TripRow> =
        recorded.map { r ->
            if (r.km?.takeIf { it.isFinite() && it > 0.0 } == null) return@map r
            val e = estimates.maxByOrNull { overlapMs(r, it) }?.takeIf { overlapMs(r, it) > 0L } ?: return@map r
            fill(r, e)
        }

    /** The row's share of the estimate by distance; 0 when either km is unusable. */
    private fun fill(r: TripRow, e: TripEstimate): TripRow {
        val rk = r.km ?: 0.0
        val share = if (e.km > 0.0 && rk > 0.0) minOf(1.0, rk / e.km) else 0.0
        val ice = e.iceKm
        return r.copy(
            kwh = r.kwh ?: e.kwh?.times(share),
            litres = r.litres ?: e.litres?.times(share),
            hevKm = if (ice != null && share > 0.0) ice * share else r.hevKm,
        )
    }
}
