package com.bydmate.app.fork.cells.collect

/**
 * R9: a stretch of discharge samples at or above 1C, no more than LOAD_GAP_MS apart. The live current
 * is negative while charging or regenerating (the author's FidMap hvCurrent), so load is discharge only.
 */
class LoadEpisode(private val oneCAmps: Double) {
    private var start: Long? = null
    private var lastHigh = 0L
    private var done = false

    fun clear() {
        start = null
        done = false
    }

    fun markDone() {
        done = true
    }

    /** The episode's first elapsed ms when the load window is complete and not yet measured. */
    fun update(s: LiveSample): Long? {
        val i = s.currentA ?: return null
        if (s.charging || i < oneCAmps) {
            start = null
            return null
        }
        val begun = start
        val first = if (begun == null || s.elapsedMs - lastHigh > CellSchedule.LOAD_GAP_MS) {
            done = false
            s.elapsedMs
        } else {
            begun
        }
        start = first
        lastHigh = s.elapsedMs
        return if (!done && s.elapsedMs - first >= CellSchedule.LOAD_HOLD_MS) first else null
    }
}

private data class PendingRelax(val id: Long, val elapsedMs: Long, val vmaxMv: Int)

class CellSchedule(oneCAmps: Double, private val intervalMs: () -> Long) {
    private val clock = RestClock()
    private val load = LoadEpisode(oneCAmps)
    private var wakePending = false
    private var anchorSoc: Int? = null
    private var lastStored: Long? = null
    private var lastContact = 0L
    private var pending: PendingRelax? = null

    /** Id = wall ms of the rest start; null while not at rest. */
    val restPeriod: Long? get() = clock.period

    /** null = unknown and less than 60 known minutes. */
    fun restMinutes(nowElapsedMs: Long): Int? = clock.minutes(nowElapsedMs)

    /**
     * [wake] false: the collector became active with the car already on (collection switched on), which is no
     * parking. A known rest under 15 min is none either (an app restart mid-session, a short stop): no wake measurement.
     */
    fun startSession(s: LiveSample, anchor: SessionAnchor?, wake: Boolean = true) {
        val rest = restSince(anchor, s.wallMs, s.elapsedMs)
        clock.begin(rest, anchor?.wallMs ?: s.wallMs, s.elapsedMs)
        load.clear()
        wakePending = wake && (rest == null || rest >= MIN_PARKING_MS)
        anchorSoc = anchor?.soc
        lastStored = null
        lastContact = s.elapsedMs
        pending = null
    }

    fun endSession(): List<RelaxUpdate> {
        val out = dropPending()
        clock.stop()
        load.clear()
        wakePending = false
        return out
    }

    fun onSample(s: LiveSample): Step {
        val relax = ArrayList<RelaxUpdate>()
        if (clock.advance(s)) {
            wakePending = false
            relax += dropPending()
        }
        val loadStart = load.update(s)
        val p = pending
        if (p != null && s.elapsedMs > p.elapsedMs + RELAX_UNTIL_MS) relax += dropPending()
        val check = pending?.let { s.elapsedMs >= it.elapsedMs + RELAX_FROM_MS } ?: false
        val last = lastStored
        val due = Due(
            scheduled = last == null || s.elapsedMs - last >= intervalMs(),
            afterParking = wakePending,
            rest60 = clock.rest60Due(s),
            load = loadStart != null,
            relaxCheck = check,
            keepalive = s.elapsedMs - lastContact >= KEEPALIVE_MS,
            windowStartMs = loadStart ?: s.elapsedMs,
        )
        return Step(due, relax)
    }

    /** R6: PENDING when a wake measurement needs the relaxation follow-up. */
    fun relaxFor(measure: MeasureKind?, vmaxMv: Int, soc: Int?): Relax {
        if (!wakePending || (measure != MeasureKind.AFTER_PARKING && measure != MeasureKind.AFTER_CHARGE)) {
            return Relax.NOT_NEEDED
        }
        val before = anchorSoc
        val top = vmaxMv > RELAX_TOP_MV || (soc ?: 0) > RELAX_TOP_SOC
        val rose = soc != null && before != null && soc - before >= RELAX_SOC_RISE
        return if (top || rose) Relax.PENDING else Relax.NOT_NEEDED
    }

    fun onStored(s: LiveSample, stored: StoredSnapshot): List<RelaxUpdate> {
        lastStored = stored.elapsedMs
        val out = answerRelax(stored)
        val kind = stored.measure
        if (kind == MeasureKind.LOAD) load.markDone()
        if (kind != null && !s.charging) clock.markMeasured()
        if (kind == MeasureKind.AFTER_PARKING || kind == MeasureKind.AFTER_CHARGE) {
            if (relaxFor(kind, stored.vmaxMv, stored.soc) == Relax.PENDING) {
                pending = PendingRelax(stored.id, stored.elapsedMs, stored.vmaxMv)
            }
            wakePending = false
        }
        return out
    }

    fun onHelperContact(nowElapsedMs: Long) {
        lastContact = nowElapsedMs
    }

    private fun answerRelax(stored: StoredSnapshot): List<RelaxUpdate> {
        val p = pending ?: return emptyList()
        if (stored.elapsedMs < p.elapsedMs + RELAX_FROM_MS) return emptyList()
        pending = null
        return listOf(RelaxUpdate(p.id, relaxOutcome(p.vmaxMv, stored.vmaxMv), p.vmaxMv - stored.vmaxMv))
    }

    private fun dropPending(): List<RelaxUpdate> {
        val p = pending ?: return emptyList()
        pending = null
        return listOf(RelaxUpdate(p.id, Relax.UNKNOWN, null))
    }

    companion object {
        const val REST_A = 5.0
        const val RESET_A = 10.0
        const val REST_FULL_MS = 3_600_000L
        const val LOAD_HOLD_MS = 7_000L
        const val LOAD_GAP_MS = 3_000L
        const val MAX_GAP_MS = 60_000L
        const val RELAX_FROM_MS = 300_000L
        const val RELAX_UNTIL_MS = 480_000L
        const val KEEPALIVE_MS = 60_000L
        const val MIN_PARKING_MS = 15 * 60_000L
        private const val RELAX_TOP_MV = 3400
        private const val RELAX_TOP_SOC = 85
        private const val RELAX_SOC_RISE = 2
    }
}
