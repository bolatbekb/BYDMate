package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData

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
     * True when the odometer went backwards from [latest] (the session so far); the tracker then
     * rebases the odometer. Only the odometer does: the BMS kWh counter is net and drops on regen.
     */
    fun isBelow(latest: TripCounters): Boolean =
        odometerKm != null && latest.odometerKm != null && odometerKm < latest.odometerKm

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

/** Driving (R/D) or standing (P/N). */
enum class Mode { DRIVE, PARK }

/** Gear 2 (R) and 4 (D) drive, 1 (P) and 3 (N) park; any other gear reading falls back to the speed. */
internal fun modeOf(gear: Int?, speed: Int?): Mode = when (gear) {
    2, 4 -> Mode.DRIVE
    1, 3 -> Mode.PARK
    else -> if ((speed ?: 0) > 0) Mode.DRIVE else Mode.PARK
}

/** Time, energy and fuel of a trip split into driving and standing. */
data class DrivePark(
    val driveMs: Long = 0,
    val parkMs: Long = 0,
    val driveKwh: Double = 0.0,
    val parkKwh: Double = 0.0,
    val driveL: Double = 0.0,
    val parkL: Double = 0.0,
) {
    /** Adds an interval and the counter deltas to [mode]. */
    fun add(mode: Mode, ms: Long, kwh: Double, litres: Double) = when (mode) {
        Mode.DRIVE -> copy(driveMs = driveMs + ms, driveKwh = driveKwh + kwh, driveL = driveL + litres)
        Mode.PARK -> copy(parkMs = parkMs + ms, parkKwh = parkKwh + kwh, parkL = parkL + litres)
    }
}

/**
 * An estimated drive: [endMs] = last tick of the session; deltas of the counters, null when a side is missing.
 * [split] = drive/park split, null for data of an old format.
 */
data class TripEstimate(
    val startMs: Long, val endMs: Long, val km: Double, val kwh: Double?, val litres: Double?, val hevKm: Double?,
    val split: DrivePark? = null,
)

/**
 * One ignition session's trip. [anchorMs] is the author's `TrackingService.sessionStartedAt` it
 * belongs to, [startMs] when this trip started, [lastSeenMs] the last tick while it was live.
 */
data class LiveSession(
    val anchorMs: Long,
    val startMs: Long,
    val lastSeenMs: Long,
    val base: TripCounters,
    val latest: TripCounters,
    val split: DrivePark = DrivePark(),
    /** Mode of the previous tick; the next interval and counter deltas go to it. */
    val lastMode: Mode? = null,
) {
    fun estimate() = TripEstimate(
        startMs = startMs,
        endMs = lastSeenMs,
        km = delta(base.odometerKm, latest.odometerKm)?.coerceAtLeast(0.0) ?: 0.0,
        kwh = delta(base.elecKwh, latest.elecKwh)?.takeIf { it in LiveTripTracker.KWH_RANGE },
        litres = delta(base.fuelL, latest.fuelL)?.takeIf { it in LiveTripTracker.LITRES_RANGE },
        hevKm = delta(base.hevKm, latest.hevKm),
        split = split,
    )

    private fun delta(from: Double?, to: Double?): Double? =
        if (from == null || to == null) null else to - from
}

/** A trip of at least [LiveTripTracker.MOVED_KM]; the small epsilon keeps 17000.1 - 17000.0 (0.0999..) a move. */
fun movedKm(km: Double): Boolean = km >= LiveTripTracker.MOVED_KM - 1e-6

/** [finished]: unconfirmed finished drives, newest last, at most [LiveTripTracker.MAX_FINISHED]. */
data class LiveTripState(val open: LiveSession? = null, val finished: List<TripEstimate> = emptyList())

/**
 * Builds the trip in progress from the car's cumulative counters. A session is one ignition
 * session of the author's `TrackingService.sessionStartedAt`: it lives from the car switched on to
 * switched off, however long it stands, and is fed by a steady ticker. It also ends on a gap in
 * ticks (the head unit slept). An odometer glitch rebases the odometer instead of ending it.
 */
class LiveTripTracker(initial: LiveTripState, nowMs: Long) {
    var state: LiveTripState = initial
        private set

    /** True once this instance has seen a non-null anchor: a later null or change is a real ignition change. */
    private var seenAnchor = false

    /**
     * Time the odometer last changed; the jump limit measures from it. The ticker re-feeds the same
     * reading during a DiPars outage, which must not move it.
     */
    private var lastOdoMs: Long = initial.open?.lastSeenMs ?: nowMs

    init {
        expireFinished(nowMs)
    }

    /**
     * Feeds one tick: the author's session anchor (null = no session), the latest reading and the
     * time. Returns true when a session opened or finished (the caller publishes at once).
     */
    fun onTick(anchorMs: Long?, data: DiParsData?, nowMs: Long): Boolean {
        var changed = false
        val stale = state.open
        if (stale != null && nowMs - stale.lastSeenMs > GAP_MS) {
            finishOpen()
            changed = true
        }
        if (anchorMs == null) {
            if (state.open != null && seenAnchor) {
                finishOpen()
                changed = true
            }
        } else {
            changed = follow(anchorMs, data, nowMs) || changed
        }
        expireFinished(nowMs)
        return changed
    }

    private fun follow(anchorMs: Long, data: DiParsData?, nowMs: Long): Boolean {
        val c = data?.let { TripCounters.of(it) } ?: TripCounters(null, null, null, null)
        var changed = false
        var session = state.open
        if (session != null && session.anchorMs != anchorMs) {
            if (seenAnchor) {
                finishOpen()
                session = null
                changed = true
            } else {
                session = session.copy(anchorMs = anchorMs)
            }
        }
        if (session == null) {
            session = LiveSession(anchorMs, nowMs, nowMs, c, c, lastMode = data?.let { modeOf(it.gear, it.speed) })
            lastOdoMs = nowMs
            changed = true
        }
        seenAnchor = true
        state = state.copy(open = if (data == null) session.ticked(nowMs) else session.advance(c, data, nowMs))
        return changed
    }

    /** An odometer step no car can drive in the time since the last reading: a scale change of the counter. */
    private fun isJump(s: LiveSession, c: TripCounters, nowMs: Long): Boolean {
        val now = c.odometerKm ?: return false
        val was = s.latest.odometerKm ?: return false
        val hours = (nowMs - lastOdoMs) / MS_PER_HOUR
        return now - was > maxOf(MIN_JUMP_KM, hours * MAX_SPEED_KMH)
    }

    /** A pure ticker beat: the time since the last tick goes to the previous mode, which stays. */
    private fun LiveSession.ticked(nowMs: Long): LiveSession =
        copy(lastSeenMs = nowMs, split = attributed(0.0, 0.0, nowMs))

    private fun LiveSession.advance(c: TripCounters, data: DiParsData, nowMs: Long): LiveSession {
        val rebased = rebaseOdometer(this, c, nowMs)
        if (c.odometerKm != null && c.odometerKm != latest.odometerKm) lastOdoMs = nowMs
        val kwh = counterDelta(latest.elecKwh, c.elecKwh)
        val litres = counterDelta(latest.fuelL, c.fuelL)
        return copy(
            lastSeenMs = nowMs, base = rebased.fillFrom(c), latest = latest.advanceBy(c),
            split = attributed(kwh, litres, nowMs), lastMode = modeOf(data.gear, data.speed),
        )
    }

    private fun counterDelta(from: Double?, to: Double?): Double = if (from == null || to == null) 0.0 else to - from

    /** The split with the interval since [LiveSession.lastSeenMs] and the deltas added to [LiveSession.lastMode]. */
    private fun LiveSession.attributed(kwh: Double, litres: Double, nowMs: Long): DrivePark {
        val mode = lastMode ?: return split
        return split.add(mode, (nowMs - lastSeenMs).coerceAtLeast(0), kwh, litres)
    }

    /** The base shifted by an odometer glitch (backwards or a jump), so the glitch step is not counted. */
    private fun rebaseOdometer(s: LiveSession, c: TripCounters, nowMs: Long): TripCounters {
        val now = c.odometerKm ?: return s.base
        val was = s.latest.odometerKm ?: return s.base
        if (!c.isBelow(s.latest) && !isJump(s, c, nowMs)) return s.base
        return s.base.copy(odometerKm = s.base.odometerKm?.plus(now - was))
    }

    private fun finishOpen() {
        val estimate = state.open?.estimate()?.takeIf { movedKm(it.km) && it.km <= MAX_TRIP_KM }
        state = LiveTripState(open = null, finished = (state.finished + listOfNotNull(estimate)).takeLast(MAX_FINISHED))
    }

    private fun expireFinished(nowMs: Long) {
        val kept = state.finished.filter { nowMs - it.endMs <= FINISHED_TTL_MS }
        if (kept.size != state.finished.size) state = state.copy(finished = kept)
    }

    companion object {
        const val GAP_MS = 2 * 60_000L
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
 * Pure-Kotlin text form of [LiveTripState] for the prefs: `L4|open|finished|E`, fields joined by
 * commas, null = empty field, finished drives joined by `;`. `L3`, `L2` and `L1` still read (no split). The closing `E` makes a truncated string fail; any failure decodes
 * to an empty state.
 */
object LiveTripCodec {
    private const val HEAD = "L4"
    private const val HEAD_V3 = "L3"
    private const val HEAD_V2 = "L2"
    private const val HEAD_V1 = "L1"
    private const val TAIL = "E"
    private const val NONE = "-"
    private const val SESSION_FIELDS = 11
    private const val SPLIT_FIELDS = 6
    private const val ESTIMATE_FIELDS = 6

    fun encode(s: LiveTripState): String {
        val open = s.open?.let { o ->
            listOf(o.anchorMs, o.startMs, o.lastSeenMs).joinToString(",") +
                "," + counters(o.base) + "," + counters(o.latest) + "," + split(o.split) + "," + (o.lastMode?.name ?: "")
        } ?: NONE
        val fin = s.finished.takeIf { it.isNotEmpty() }?.joinToString(";") {
            listOf(it.startMs, it.endMs, it.km, it.kwh ?: "", it.litres ?: "", it.hevKm ?: "").joinToString(",") +
                "," + (it.split?.let { sp -> split(sp) } ?: ",".repeat(SPLIT_FIELDS - 1))
        } ?: NONE
        return listOf(HEAD, open, fin, TAIL).joinToString("|")
    }

    fun decode(raw: String?): LiveTripState = try {
        val parts = raw.orEmpty().split("|")
        require(parts.size == 4 && parts[0] in listOf(HEAD, HEAD_V3, HEAD_V2, HEAD_V1) && parts[3] == TAIL)
        LiveTripState(open = session(parts[1], parts[0]), finished = estimates(parts[2], parts[0] == HEAD))
    } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
        LiveTripState()
    }

    private fun split(d: DrivePark) =
        listOf(d.driveMs, d.parkMs, d.driveKwh, d.parkKwh, d.driveL, d.parkL).joinToString(",")

    private fun counters(c: TripCounters) =
        listOf(c.odometerKm, c.elecKwh, c.fuelL, c.hevKm).joinToString(",") { it?.toString() ?: "" }

    /** L3+: anchor,start,lastSeen,...; L2/L1: start,lastOn,lastReading,... (anchor = start, lastSeen = lastReading). L4 adds split and mode. */
    private fun session(s: String, head: String): LiveSession? {
        if (s == NONE) return null
        val f = s.split(",")
        val v4 = head == HEAD
        require(f.size == SESSION_FIELDS + if (v4) SPLIT_FIELDS + 1 else 0)
        val first = f[0].toLong()
        val base = counters(f, 3)
        val latest = counters(f, 7)
        return when {
            v4 -> LiveSession(
                first, f[1].toLong(), f[2].toLong(), base, latest,
                split(f, SESSION_FIELDS) ?: DrivePark(), f[SESSION_FIELDS + SPLIT_FIELDS].takeIf { it.isNotEmpty() }?.let { Mode.valueOf(it) },
            )
            head == HEAD_V3 -> LiveSession(first, f[1].toLong(), f[2].toLong(), base, latest)
            else -> LiveSession(first, first, f[2].toLong(), base, latest)
        }
    }

    /** Six split fields from [at]; null when all are empty. */
    private fun split(f: List<String>, at: Int): DrivePark? {
        if ((at until at + SPLIT_FIELDS).all { f[it].isEmpty() }) return null
        return DrivePark(
            f[at].toLong(), f[at + 1].toLong(), f[at + 2].toDouble(), f[at + 3].toDouble(), f[at + 4].toDouble(), f[at + 5].toDouble(),
        )
    }

    private fun counters(f: List<String>, at: Int) =
        TripCounters(num(f[at]), num(f[at + 1]), num(f[at + 2]), num(f[at + 3]))

    private fun estimates(s: String, v4: Boolean): List<TripEstimate> =
        if (s == NONE) emptyList() else s.split(";").map { estimate(it, v4) }.takeLast(LiveTripTracker.MAX_FINISHED)

    private fun estimate(s: String, v4: Boolean): TripEstimate {
        val f = s.split(",")
        require(f.size == ESTIMATE_FIELDS + if (v4) SPLIT_FIELDS else 0)
        return TripEstimate(
            f[0].toLong(), f[1].toLong(), f[2].toDouble(), num(f[3]), num(f[4]), num(f[5]),
            if (v4) split(f, ESTIMATE_FIELDS) else null,
        )
    }

    private fun num(s: String): Double? = if (s.isEmpty()) null else s.toDouble()
}
