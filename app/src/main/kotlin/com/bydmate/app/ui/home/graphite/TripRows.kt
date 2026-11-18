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
    internal fun overlapMs(r: TripRow, e: TripEstimate): Long {
        val end = r.endMs
        if (end == null) return if (r.startMs in e.startMs..e.endMs) (e.endMs - r.startMs).coerceAtLeast(1L) else 0L
        val o = minOf(end, e.endMs) - maxOf(r.startMs, e.startMs)
        return if (o > MIN_OVERLAP_MS) o else 0L
    }

    /** The open session's estimate up to [nowMs], when it has moved. */
    private fun openEstimate(live: LiveTripState, nowMs: Long): TripEstimate? =
        live.open?.estimate()?.copy(endMs = nowMs)?.takeIf { movedKm(it.km) }

    /** Recorded trips the open estimate hides while the session is live (the author keeps recording them). */
    fun hiddenByLive(recorded: List<TripRow>, live: LiveTripState, nowMs: Long): List<Pair<TripRow, TripEstimate>> {
        val e = openEstimate(live, nowMs) ?: return emptyList()
        return recorded.filter { r -> r.km?.takeIf { it.isFinite() && it > 0.0 } != null && overlapMs(r, e) > 0L }.map { it to e }
    }

    /**
     * What the energy chart draws: our open estimate instead of the records it overlaps, the other
     * records as stored, and finished estimates no record confirms.
     */
    fun chartRows(recorded: List<TripRow>, live: LiveTripState, nowMs: Long): List<TripRow> {
        val hidden = hiddenByLive(recorded, live, nowMs).map { it.first }.toSet()
        val open = openEstimate(live, nowMs)
        val visible = recorded.filter { it !in hidden }
        val finished = live.finished.filter { movedKm(it.km) && !confirmed(it, recorded) }
        return visible + listOfNotNull(open).map { it.toRow() } + finished.map { it.toRow() }
    }
}
