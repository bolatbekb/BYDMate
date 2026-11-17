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

    /**
     * True when the odometer went backwards from [base] (the session so far). Only the odometer
     * does: the BMS kWh counter is net and drops on regen, so it never splits a session.
     */
    fun isBelow(base: TripCounters): Boolean =
        odometerKm != null && base.odometerKm != null && odometerKm < base.odometerKm

    companion object {
        private const val MIN_ODOMETER_KM = 1.0

        fun of(d: DiParsData) = TripCounters(
            odometerKm = d.mileage?.takeIf { it >= MIN_ODOMETER_KM },
            elecKwh = d.totalElecConsumption?.takeIf { it > 0.0 },
            fuelL = d.fuelTotalL?.takeIf { it > 0.0 },
            hevKm = d.mileageHevKm?.toDouble()?.takeIf { it > 0.0 },
        )
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
        km = delta(base.odometerKm, latest.odometerKm)?.coerceAtLeast(0.0) ?: 0.0,
        kwh = delta(base.elecKwh, latest.elecKwh)?.takeIf { it in LiveTripTracker.KWH_RANGE },
        litres = delta(base.fuelL, latest.fuelL)?.takeIf { it in LiveTripTracker.LITRES_RANGE },
        hevKm = delta(base.hevKm, latest.hevKm),
    )

    private fun delta(from: Double?, to: Double?): Double? =
        if (from == null || to == null) null else to - from
}

/** A trip of at least [LiveTripTracker.MOVED_KM]; the small epsilon keeps 17000.1 - 17000.0 (0.0999..) a move. */
fun movedKm(km: Double): Boolean = km >= LiveTripTracker.MOVED_KM - 1e-6

/** [finished]: unconfirmed finished drives, newest last, at most [LiveTripTracker.MAX_FINISHED]. */
data class LiveTripState(val open: LiveSession? = null, val finished: List<TripEstimate> = emptyList())

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
        if (open != null && nowMs - open.lastReadingMs <= GAP_MS) {
            edge.seedOn(nowMs)
            state = state.copy(open = open.copy(lastReadingMs = nowMs))
        } else {
            finishOpen()
        }
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
            on && session != null && (counters.isBelow(session.latest) || isJump(session, counters, nowMs)) -> {
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

    /** An odometer step no car can drive in the time since the last reading: a scale change of the counter. */
    private fun isJump(s: LiveSession, c: TripCounters, nowMs: Long): Boolean {
        val now = c.odometerKm ?: return false
        val was = s.latest.odometerKm ?: return false
        val hours = (nowMs - s.lastReadingMs) / MS_PER_HOUR
        return now - was > maxOf(MIN_JUMP_KM, hours * MAX_SPEED_KMH)
    }

    private fun LiveSession.advance(c: TripCounters, nowMs: Long) = copy(
        lastOnMs = if (edge.lastOnMs == nowMs) nowMs else lastOnMs, lastReadingMs = nowMs, base = base.fillFrom(c), latest = latest.advanceBy(c),
    )

    private fun finishOpen() {
        val estimate = state.open?.estimate()?.takeIf { movedKm(it.km) && it.km <= MAX_TRIP_KM }
        state = LiveTripState(open = null, finished = (state.finished + listOfNotNull(estimate)).takeLast(MAX_FINISHED))
    }

    private fun expireFinished(nowMs: Long) {
        val kept = state.finished.filter { nowMs - it.endMs <= FINISHED_TTL_MS }
        if (kept.size != state.finished.size) state = state.copy(finished = kept)
    }

    companion object {
        const val GAP_MS = 5 * 60_000L
        const val MOVED_KM = 0.1
        const val FINISHED_TTL_MS = 48 * 3_600_000L
        const val MAX_FINISHED = 8

        /** Faster than any car: a bigger odometer step is a counter scale change (cf. TripRecorder). */
        const val MAX_SPEED_KMH = 300.0
        const val MIN_JUMP_KM = 2.0
        const val MAX_TRIP_KM = 1500.0
        val KWH_RANGE = -5.0..500.0
        val LITRES_RANGE = -1.0..100.0
        private const val MS_PER_HOUR = 3_600_000.0
    }
}

/**
 * Pure-Kotlin text form of [LiveTripState] for the prefs: `L2|open|finished|E`, fields joined by
 * commas, null = empty field, finished drives joined by `;`. `L1` (one finished drive) still reads. The closing `E` makes a truncated string fail; any failure decodes
 * to an empty state.
 */
object LiveTripCodec {
    private const val HEAD = "L2"
    private const val HEAD_V1 = "L1"
    private const val TAIL = "E"
    private const val NONE = "-"
    private const val SESSION_FIELDS = 11
    private const val ESTIMATE_FIELDS = 6

    fun encode(s: LiveTripState): String {
        val open = s.open?.let { o ->
            listOf(o.startMs, o.lastOnMs, o.lastReadingMs).joinToString(",") +
                "," + counters(o.base) + "," + counters(o.latest)
        } ?: NONE
        val fin = s.finished.takeIf { it.isNotEmpty() }?.joinToString(";") {
            listOf(it.startMs, it.endMs, it.km, it.kwh ?: "", it.litres ?: "", it.hevKm ?: "").joinToString(",")
        } ?: NONE
        return listOf(HEAD, open, fin, TAIL).joinToString("|")
    }

    fun decode(raw: String?): LiveTripState = try {
        val parts = raw.orEmpty().split("|")
        require(parts.size == 4 && (parts[0] == HEAD || parts[0] == HEAD_V1) && parts[3] == TAIL)
        LiveTripState(open = session(parts[1]), finished = estimates(parts[2]))
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

    private fun estimates(s: String): List<TripEstimate> =
        if (s == NONE) emptyList() else s.split(";").map { estimate(it) }.takeLast(LiveTripTracker.MAX_FINISHED)

    private fun estimate(s: String): TripEstimate {
        val f = s.split(",")
        require(f.size == ESTIMATE_FIELDS)
        return TripEstimate(f[0].toLong(), f[1].toLong(), f[2].toDouble(), num(f[3]), num(f[4]), num(f[5]))
    }

    private fun num(s: String): Double? = if (s.isEmpty()) null else s.toDouble()
}
