package com.bydmate.app.ui.home.graphite

import kotlin.math.abs

/** An event of the trip log: a name and `key=value` fields. */
class TripEvent(val name: String, val fields: List<Pair<String, Any?>>)

/** A recorded row the chart hides while our live estimate is drawn instead. */
class HideDecision(val rowStartMs: Long, val estimateStartMs: Long) {
    fun fields(): List<Pair<String, Any?>> = listOf("row" to rowStartMs, "est" to estimateStartMs)
}

/** Pure event detection from the tracker's state before and after one call: the tracker itself is not touched. */
object TripLogEvents {
    private const val EPS = 1e-9

    fun diff(before: LiveTripState, after: LiveTripState, anchorMs: Long?, nowMs: Long): List<TripEvent> {
        val was = before.open
        val now = after.open
        val sameSession = was != null && now != null && was.startMs == now.startMs
        val out = ArrayList<TripEvent>()
        if (was != null && !sameSession) out += finish(was, before, after, anchorMs, nowMs)
        if (now != null && !sameSession) {
            out += TripEvent("open", listOf("anchor" to now.anchorMs, "start" to now.startMs, "odo" to now.latest.odometerKm))
        }
        if (was != null && now != null && sameSession) out += within(was, now)
        return out
    }

    private fun finish(was: LiveSession, before: LiveTripState, after: LiveTripState, anchorMs: Long?, nowMs: Long): TripEvent {
        val reason = when {
            nowMs - was.lastSeenMs > LiveTripTracker.GAP_MS -> "gap"
            anchorMs == null -> "anchor_null"
            else -> "anchor_change"
        }
        val est = was.estimate()
        val kept = after.finished.lastOrNull().let { it != null && it !in before.finished }
        return TripEvent(
            "finish",
            listOf(
                "reason" to reason, "start" to was.startMs, "lastSeen" to was.lastSeenMs, "km" to est.km, "kwh" to est.kwh,
                "l" to est.litres, "ice" to est.iceKm, "ev" to est.evKm, "kept" to kept,
            ),
        )
    }

    /** Adopt, rebase and dropped-energy events of one session between two calls. */
    private fun within(was: LiveSession, now: LiveSession): List<TripEvent> {
        val out = ArrayList<TripEvent>()
        if (was.anchorMs != now.anchorMs) out += TripEvent("adopt", listOf("from" to was.anchorMs, "to" to now.anchorMs))
        val b0 = was.base.odometerKm
        val b1 = now.base.odometerKm
        if (b0 != null && b1 != null && abs(b1 - b0) > EPS) {
            out += TripEvent("rebase", listOf("shiftKm" to (b1 - b0), "odoWas" to was.latest.odometerKm, "odoNow" to now.latest.odometerKm))
        }
        if (was.lastMode != null) {
            dropped("kwh", was.latest.elecKwh, now.latest.elecKwh, was.split.driveKwh + was.split.parkKwh, now.split.driveKwh + now.split.parkKwh)
                ?.let { out += it }
            dropped("l", was.latest.fuelL, now.latest.fuelL, was.split.driveL + was.split.parkL, now.split.driveL + now.split.parkL)
                ?.let { out += it }
        }
        return out
    }

    /** A counter step the split did not take (an implausible energy step adds only time). */
    private fun dropped(what: String, c0: Double?, c1: Double?, s0: Double, s1: Double): TripEvent? {
        if (c0 == null || c1 == null) return null
        val step = c1 - c0
        if (abs(step) < EPS || abs(step - (s1 - s0)) < EPS) return null
        return TripEvent("energy_dropped", listOf("what" to what, "step" to step, "counterWas" to c0, "counterNow" to c1))
    }

    /** The recorded rows the open estimate hides right now. */
    fun hideDecisions(recorded: List<TripRow>, live: LiveTripState, nowMs: Long): List<HideDecision> =
        TripMatch.hiddenByLive(recorded, live, nowMs).map { (r, e) -> HideDecision(r.startMs, e.startMs) }
}
