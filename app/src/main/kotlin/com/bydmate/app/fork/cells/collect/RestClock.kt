package com.bydmate.app.fork.cells.collect

import kotlin.math.abs

private const val MAX_REST_SINCE_MS = 30L * 24 * 3_600_000L

/** R4: rest since the previous session; elapsed time when it did not go back, else a sane wall-clock delta. */
fun restSince(prev: SessionAnchor?, nowWallMs: Long, nowElapsedMs: Long): Long? {
    if (prev == null) return null
    if (nowElapsedMs >= prev.elapsedMs) return nowElapsedMs - prev.elapsedMs
    val wall = nowWallMs - prev.wallMs
    return if (wall in 0..MAX_REST_SINCE_MS) wall else null
}

/** R7: rest accumulator. */
class RestClock {
    var accumMs: Long = 0L
        private set
    var period: Long? = null
        private set
    private var unknown = false
    private var rest60Done = false
    private var resting = false
    private var lastElapsed: Long = 0L

    fun begin(restMs: Long?, periodId: Long, nowElapsedMs: Long) {
        accumMs = restMs ?: 0L
        unknown = restMs == null
        period = periodId
        rest60Done = false
        resting = true
        lastElapsed = nowElapsedMs
    }

    fun stop() {
        accumMs = 0L
        unknown = false
        period = null
        resting = false
    }

    /** Returns true when this sample broke the rest (current above 10 A or charging). */
    fun advance(s: LiveSample): Boolean {
        val dt = (s.elapsedMs - lastElapsed).coerceIn(0L, CellSchedule.MAX_GAP_MS)
        lastElapsed = s.elapsedMs
        val i = s.currentA
        if (s.charging || (i != null && abs(i) > CellSchedule.RESET_A)) {
            stop()
            return true
        }
        if (i == null) return false
        resting = abs(i) <= CellSchedule.REST_A
        if (resting) {
            if (period == null) {
                period = s.wallMs
                rest60Done = false
            }
            accumMs += dt
        }
        return false
    }

    fun rest60Due(s: LiveSample): Boolean =
        resting && !s.charging && period != null && !rest60Done && accumMs >= CellSchedule.REST_FULL_MS

    /** A measurement taken with 60+ minutes of rest closes the "60 minutes" slot of this period. */
    fun markMeasured() {
        if (period != null && accumMs >= CellSchedule.REST_FULL_MS) rest60Done = true
    }

    fun minutes(nowElapsedMs: Long): Int? {
        val tail = if (resting) (nowElapsedMs - lastElapsed).coerceIn(0L, CellSchedule.MAX_GAP_MS) else 0L
        val total = accumMs + tail
        if (unknown && total < CellSchedule.REST_FULL_MS) return null
        return (total / 60_000L).toInt()
    }
}
