package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.trips.HybridIgnitionEdge

/**
 * One reading's lifetime counters; null = not reported. An odometer below 1 km is the startup
 * zero and reads as null; the other counters are lifetime totals, so a non-positive value is
 * likewise "not reported yet".
 */
data class TripCounters(val odometerKm: Double?, val elecKwh: Double?, val fuelL: Double?, val hevKm: Double?) {
    /** Fills the gaps of this one from [o]. */
    fun fillFrom(o: TripCounters) = TripCounters(
        odometerKm ?: o.odometerKm, elecKwh ?: o.elecKwh, fuelL ?: o.fuelL, hevKm ?: o.hevKm,
    )

    /** Takes the reported fields of [o], keeps the rest. */
    fun advanceBy(o: TripCounters) = o.fillFrom(this)

    /** True when any counter reported by both went backwards from [base] (the session so far). */
    fun isBelow(base: TripCounters): Boolean =
        less(odometerKm, base.odometerKm) || less(elecKwh, base.elecKwh) ||
            less(fuelL, base.fuelL) || less(hevKm, base.hevKm)

    companion object {
        private const val MIN_ODOMETER_KM = 1.0

        fun of(d: DiParsData) = TripCounters(
            odometerKm = d.mileage?.takeIf { it >= MIN_ODOMETER_KM },
            elecKwh = d.totalElecConsumption?.takeIf { it > 0.0 },
            fuelL = d.fuelTotalL?.takeIf { it > 0.0 },
            hevKm = d.mileageHevKm?.toDouble()?.takeIf { it > 0.0 },
        )

        private fun less(a: Double?, b: Double?) = a != null && b != null && a < b
    }
}

/** An estimated drive: [endMs] = last "on" reading; deltas of the counters, null when a side is missing. */
data class TripEstimate(
    val startMs: Long, val endMs: Long, val km: Double, val kwh: Double?, val litres: Double?, val hevKm: Double?,
)

data class LiveSession(
    val startMs: Long,
    val lastOnMs: Long,
    val lastReadingMs: Long,
    val base: TripCounters,
    val latest: TripCounters,
) {
    fun estimate() = TripEstimate(
        startMs = startMs,
        endMs = lastOnMs,
        km = delta(base.odometerKm, latest.odometerKm) ?: 0.0,
        kwh = delta(base.elecKwh, latest.elecKwh),
        litres = delta(base.fuelL, latest.fuelL),
        hevKm = delta(base.hevKm, latest.hevKm),
    )

    private fun delta(from: Double?, to: Double?): Double? =
        if (from == null || to == null) null else (to - from).coerceAtLeast(0.0)
}

data class LiveTripState(val open: LiveSession? = null, val finished: TripEstimate? = null)

/**
 * Builds the trip in progress from the car's cumulative counters. "On" comes from the author's
 * [HybridIgnitionEdge]; a session lives from the first on reading to the first off one, to a gap
 * in readings, or to a counter running backwards.
 */
class LiveTripTracker(initial: LiveTripState, nowMs: Long) {
    private val edge = HybridIgnitionEdge()

    var state: LiveTripState = initial
        private set

    init {
        val open = initial.open
        if (open != null && nowMs - open.lastReadingMs <= GAP_MS) edge.seedOn(nowMs) else finishOpen()
        expireFinished(nowMs)
    }

    /** Feeds one reading; returns true when a session opened or finished (the caller publishes at once). */
    fun onReading(data: DiParsData, nowMs: Long): Boolean {
        var changed = false
        val stale = state.open
        if (stale != null && nowMs - stale.lastReadingMs > GAP_MS) {
            finishOpen()
            changed = true
        }
        val on = edge.update(data, nowMs)
        val counters = TripCounters.of(data)
        val session = state.open
        when {
            on && session == null -> changed = open(counters, nowMs) || changed
            on && session != null && counters.isBelow(session.latest) -> {
                finishOpen()
                changed = open(counters, nowMs) || changed
            }
            on && session != null -> state = state.copy(open = session.advance(counters, nowMs))
            session != null -> {
                state = state.copy(open = session.copy(lastReadingMs = nowMs))
                finishOpen()
                changed = true
            }
        }
        expireFinished(nowMs)
        return changed
    }

    private fun open(counters: TripCounters, nowMs: Long): Boolean {
        state = state.copy(open = LiveSession(nowMs, nowMs, nowMs, counters, counters))
        return true
    }

    private fun LiveSession.advance(c: TripCounters, nowMs: Long) = copy(
        lastOnMs = nowMs, lastReadingMs = nowMs, base = base.fillFrom(c), latest = latest.advanceBy(c),
    )

    private fun finishOpen() {
        val estimate = state.open?.estimate()?.takeIf { it.km >= MOVED_KM }
        state = LiveTripState(open = null, finished = estimate ?: state.finished)
    }

    private fun expireFinished(nowMs: Long) {
        val f = state.finished
        if (f != null && nowMs - f.endMs > FINISHED_TTL_MS) state = state.copy(finished = null)
    }

    companion object {
        const val GAP_MS = 5 * 60_000L
        const val MOVED_KM = 0.1
        const val FINISHED_TTL_MS = 48 * 3_600_000L
    }
}

/**
 * Pure-Kotlin text form of [LiveTripState] for the prefs: `L1|open|finished|E`, fields joined by
 * commas, null = empty field. The closing `E` makes a truncated string fail; any failure decodes
 * to an empty state.
 */
object LiveTripCodec {
    private const val HEAD = "L1"
    private const val TAIL = "E"
    private const val NONE = "-"
    private const val SESSION_FIELDS = 11
    private const val ESTIMATE_FIELDS = 6

    fun encode(s: LiveTripState): String {
        val open = s.open?.let { o ->
            listOf(o.startMs, o.lastOnMs, o.lastReadingMs).joinToString(",") +
                "," + counters(o.base) + "," + counters(o.latest)
        } ?: NONE
        val fin = s.finished?.let { listOf(it.startMs, it.endMs, it.km, it.kwh ?: "", it.litres ?: "", it.hevKm ?: "") }
            ?.joinToString(",") ?: NONE
        return listOf(HEAD, open, fin, TAIL).joinToString("|")
    }

    fun decode(raw: String?): LiveTripState = try {
        val parts = raw.orEmpty().split("|")
        require(parts.size == 4 && parts[0] == HEAD && parts[3] == TAIL)
        LiveTripState(open = session(parts[1]), finished = estimate(parts[2]))
    } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
        LiveTripState()
    }

    private fun counters(c: TripCounters) =
        listOf(c.odometerKm, c.elecKwh, c.fuelL, c.hevKm).joinToString(",") { it?.toString() ?: "" }

    private fun session(s: String): LiveSession? {
        if (s == NONE) return null
        val f = s.split(",")
        require(f.size == SESSION_FIELDS)
        return LiveSession(
            f[0].toLong(), f[1].toLong(), f[2].toLong(), counters(f, 3), counters(f, 7),
        )
    }

    private fun counters(f: List<String>, at: Int) =
        TripCounters(num(f[at]), num(f[at + 1]), num(f[at + 2]), num(f[at + 3]))

    private fun estimate(s: String): TripEstimate? {
        if (s == NONE) return null
        val f = s.split(",")
        require(f.size == ESTIMATE_FIELDS)
        return TripEstimate(f[0].toLong(), f[1].toLong(), f[2].toDouble(), num(f[3]), num(f[4]), num(f[5]))
    }

    private fun num(s: String): Double? = if (s.isEmpty()) null else s.toDouble()
}
