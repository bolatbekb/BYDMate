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
}
