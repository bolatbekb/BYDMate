package com.bydmate.app.fork.cells.collect

/** What one live sample does to the collector's session. */
internal enum class SessionMove {
    /** Inactive, and it stays so. */
    STAY_OFF,

    /** Active, an «off» reading not confirmed yet: nothing changes and the sample is not fed. */
    HOLD,

    /** Active → inactive. */
    END,

    /** Active: feed the sample. */
    CONTINUE,

    /** Start a session with the wake measurement: process start, car off → on, or the head unit slept. */
    WAKE,

    /** Active, and the head unit slept since the last sample: end the session, then start one with the wake measurement. */
    RESTART,

    /** Start a session without the wake measurement: collection switched on (or the car chosen) with the car already on. */
    JOIN,
}

/**
 * R5 (amended) on every sample. The head unit quickboots: it suspends with the car and resumes, often before any
 * `powerState == 0` reaches the app, so a growing sleep time (elapsedRealtime − uptime, the signal of the fork's
 * LiveTripModel) is a wake too. powerState dips to 0 for ~0.6 s when the start button is pressed on a car already
 * on (the author's OffReport): «off» counts only when off readings span [OFF_CONFIRM_MS]. Not thread-safe: the
 * collector calls it under its lock.
 */
internal class SessionGate {
    private var lastSleepMs: Long? = null
    private var prevOn: Boolean? = null
    private var offSinceMs: Long? = null

    /** [uptimeMs] is uptimeMillis read with the sample; [enabled] = a car profile chosen and collection on. */
    fun move(s: LiveSample, uptimeMs: Long, enabled: Boolean, active: Boolean): SessionMove {
        val sleep = s.elapsedMs - uptimeMs
        val slept = lastSleepMs?.let { sleep - it > SLEEP_SLACK_MS } == true
        lastSleepMs = sleep
        // After a sleep the car was not watched: whatever it was before says nothing now.
        val wasOn = prevOn.takeUnless { slept }
        prevOn = s.powerOn
        if (s.powerOn) offSinceMs = null
        return when {
            !enabled -> if (active) SessionMove.END else SessionMove.STAY_OFF
            !s.powerOn -> off(s.elapsedMs, active, slept)
            !active -> if (wasOn == true) SessionMove.JOIN else SessionMove.WAKE
            slept -> SessionMove.RESTART
            else -> SessionMove.CONTINUE
        }
    }

    private fun off(nowMs: Long, active: Boolean, slept: Boolean): SessionMove {
        val since = offSinceMs ?: nowMs.also { offSinceMs = it }
        return when {
            !active -> SessionMove.STAY_OFF
            slept || nowMs - since >= OFF_CONFIRM_MS -> SessionMove.END
            else -> SessionMove.HOLD
        }
    }

    companion object {
        const val SLEEP_SLACK_MS = 5_000L
        const val OFF_CONFIRM_MS = 5_000L
    }
}
