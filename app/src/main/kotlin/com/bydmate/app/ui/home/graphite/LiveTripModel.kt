package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import kotlin.math.abs

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

/**
 * Gear 2 (R) and 4 (D) drive, 1 (P) and 3 (N) park; any other gear reading falls back to the speed.
 * With neither gear nor speed, [odoDeltaKm] (odometer movement since the previous tick) decides.
 */
internal fun modeOf(gear: Int?, speed: Int?, odoDeltaKm: Double? = null): Mode = when (gear) {
    2, 4 -> Mode.DRIVE
    1, 3 -> Mode.PARK
    else -> when {
        speed != null -> if (speed > 0) Mode.DRIVE else Mode.PARK
        else -> if ((odoDeltaKm ?: 0.0) >= ODO_MOVED_KM) Mode.DRIVE else Mode.PARK
    }
}

private const val ODO_MOVED_KM = 0.05

private fun odoDelta(from: TripCounters, to: TripCounters): Double? =
    if (from.odometerKm == null || to.odometerKm == null) null else to.odometerKm - from.odometerKm

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
 * [split] = drive/park split, null for data of an old format. [iceKm]/[evKm] = odometer km driven with the engine
 * running / not running (by engine rpm), null for data of an old format.
 */
data class TripEstimate(
    val startMs: Long, val endMs: Long, val km: Double, val kwh: Double?, val litres: Double?, val hevKm: Double?,
    val split: DrivePark? = null,
    val iceKm: Double? = null,
    val evKm: Double? = null,
)

/**
 * The head unit's boot: [bootCount] = `Settings.Global.BOOT_COUNT` (null when unreadable), [bootMs] = wall time of the
 * boot (now - elapsedRealtime), [sleepMs] = time the head unit spent asleep since the boot (elapsedRealtime - uptime).
 * The head unit reboots at every ignition on, so one boot is one ignition cycle.
 */
data class BootInfo(val bootCount: Int?, val bootMs: Long, val sleepMs: Long) {
    /** Same boot: equal counts when both are known, else the boot times within [LiveTripTracker.BOOT_SLACK_MS]. */
    fun sameBoot(o: BootInfo): Boolean =
        if (bootCount != null && o.bootCount != null) bootCount == o.bootCount
        else abs(bootMs - o.bootMs) <= LiveTripTracker.BOOT_SLACK_MS
}

/**
 * One ignition cycle's trip. [anchorMs] is the author's `TrackingService.sessionStartedAt` at its opening (only for
 * analysis and the migration), [startMs] when this trip started, [lastSeenMs] the last on-tick while it was live.
 * [boot] = the boot it belongs to (null = saved before 4.4.1.16), [sleepAtLastMs] = the head unit's sleep time at the
 * last tick, [offSinceMs] = when the first `powerState == 0` reading came (null = the car is on).
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
    /** Km with the engine running / not running; null = the session began before this was tracked. */
    val iceKm: Double? = 0.0,
    val evKm: Double? = 0.0,
    /** Engine rpm of the previous tick. */
    val lastRpm: Int? = null,
    /** True once a non-null rpm was read in this session; the rpm rule covers only the intervals from then on. */
    val rpmSeen: Boolean = false,
    val boot: BootInfo? = null,
    val sleepAtLastMs: Long = 0,
    val offSinceMs: Long? = null,
) {
    /**
     * The rpm rule's ice/ev km count only when the session saw an rpm and the rule covered at least
     * [LiveTripTracker.MIN_RPM_COVERAGE] of the trip km; else both are null and hevKm is the counter delta.
     */
    fun estimate(): TripEstimate {
        val km = delta(base.odometerKm, latest.odometerKm)?.coerceAtLeast(0.0) ?: 0.0
        val covered = rpmSeen && iceKm != null && evKm != null && iceKm + evKm >= LiveTripTracker.MIN_RPM_COVERAGE * km
        return TripEstimate(
            startMs = startMs,
            endMs = lastSeenMs,
            km = km,
            kwh = delta(base.elecKwh, latest.elecKwh)?.takeIf { it in LiveTripTracker.KWH_RANGE },
            litres = delta(base.fuelL, latest.fuelL)?.takeIf { it in LiveTripTracker.LITRES_RANGE },
            hevKm = (if (covered) iceKm else null) ?: delta(base.hevKm, latest.hevKm),
            split = split,
            iceKm = if (covered) iceKm else null,
            evKm = if (covered) evKm else null,
        )
    }

    private fun delta(from: Double?, to: Double?): Double? =
        if (from == null || to == null) null else to - from

    /** Takes the interval's odometer step [km] (null = none or a glitch) by the rpm at its ends; [rpm] is the new reading. */
    internal fun counting(km: Double?, rpm: Int?): LiveSession {
        val seen = rpmSeen || rpm != null
        val ice = (lastRpm ?: 0) > 0 || (rpm ?: 0) > 0
        val k = km.takeIf { seen }
        return copy(
            iceKm = if (k != null && ice) iceKm?.plus(k) else iceKm,
            evKm = if (k != null && !ice) evKm?.plus(k) else evKm,
            lastRpm = rpm, rpmSeen = seen,
        )
    }
}

/** A trip of at least [LiveTripTracker.MOVED_KM]; the small epsilon keeps 17000.1 - 17000.0 (0.0999..) a move. */
fun movedKm(km: Double): Boolean = km >= LiveTripTracker.MOVED_KM - 1e-6

/** [finished]: unconfirmed finished drives, newest last, at most [LiveTripTracker.MAX_FINISHED]. */
data class LiveTripState(val open: LiveSession? = null, val finished: List<TripEstimate> = emptyList())

/**
 * Builds the trip in progress from the car's cumulative counters. A session is one ignition cycle, identified by the
 * head unit's boot ([BootInfo]): it lives from the car switched on to switched off, however long it stands or however
 * long the app was away, and is fed by a steady ticker. It ends on a new boot, on the head unit having slept, or on
 * the car seen off (`powerState == 0` for [OFF_CONFIRM_MS]). An odometer glitch rebases the odometer instead.
 */
@Suppress("TooManyFunctions") // one state machine: the cycle rules, the opening, the counters and the attribution
class LiveTripTracker(initial: LiveTripState, nowMs: Long) {
    var state: LiveTripState = initial
        private set

    private val events = ArrayList<TripEvent>()

    /** Why the next session opens: first, or after a finish by boot / sleep / off / migrate. */
    private var openReason = REASON_FIRST

    /** True after the car was seen off: only a real `powerState >= 1` reading opens the next session. */
    private var closedByOff = false

    /**
     * Time the odometer last changed; the jump limit measures from it. The ticker re-feeds the same
     * reading during a DiPars outage, which must not move it.
     */
    private var lastOdoMs: Long = initial.open?.lastSeenMs ?: nowMs

    init {
        expireFinished(nowMs)
    }

    /** The cycle events since the last call (for the trip log). */
    fun drainEvents(): List<TripEvent> = events.toList().also { events.clear() }

    /**
     * Feeds one tick: the head unit's boot, the author's session anchor (identity no more, only for the log and the
     * migration of an old session), the latest reading and the time. Returns true when a session opened or finished
     * (the caller publishes at once).
     */
    fun onTick(boot: BootInfo, anchorMs: Long?, data: DiParsData?, nowMs: Long): Boolean {
        var changed = endOnCycleChange(boot, anchorMs, nowMs)
        val ps = data?.powerState
        state.open?.let { s ->
            if (ps == 0) {
                changed = offStep(s, boot, nowMs) || changed
            } else {
                state = state.copy(open = follow(s, boot, data, nowMs))
            }
        }
        if (state.open == null && data != null && mayOpen(ps)) {
            state = state.copy(open = openSession(boot, anchorMs, data, nowMs))
            changed = true
        }
        expireFinished(nowMs)
        return changed
    }

    /** A session opens unless the car reads off, or was seen off and shows no `powerState` yet. */
    private fun mayOpen(ps: Int?): Boolean = ps != 0 && !(closedByOff && ps == null)

    /** A boot change, a sleep, or a session of an old format that is not the one running now ends the open session. */
    private fun endOnCycleChange(boot: BootInfo, anchorMs: Long?, nowMs: Long): Boolean {
        val s = state.open ?: return false
        val was = s.boot
        val reason = when {
            was == null -> migrate(s, boot, anchorMs, nowMs)
            !was.sameBoot(boot) -> REASON_BOOT
            boot.sleepMs - s.sleepAtLastMs > SLEEP_SLACK_MS -> REASON_SLEEP
            else -> null
        } ?: return false
        finishOpen()
        closedByOff = false
        openReason = reason
        return true
    }

    /** An L6 session without a boot: adopt it into the current boot when the author's session is its own or it is fresh; null = adopted. */
    private fun migrate(s: LiveSession, boot: BootInfo, anchorMs: Long?, nowMs: Long): String? {
        val adopt = anchorMs == s.anchorMs || nowMs - s.lastSeenMs <= MIGRATE_FRESH_MS
        events += TripEvent("migrate", listOf("result" to if (adopt) "adopt" else "finish", "anchor" to anchorMs, "ageS" to (nowMs - s.lastSeenMs) / MS_PER_SECOND))
        if (adopt) state = state.copy(open = s.copy(boot = boot, sleepAtLastMs = boot.sleepMs))
        return if (adopt) null else REASON_MIGRATE
    }

    /** A `powerState == 0` reading: the first only marks the time, one that spans [OFF_CONFIRM_MS] ends the cycle at its last on-tick. */
    private fun offStep(s: LiveSession, boot: BootInfo, nowMs: Long): Boolean {
        val since = s.offSinceMs
        if (since == null) {
            state = state.copy(open = s.copy(offSinceMs = nowMs, boot = s.boot ?: boot))
            return false
        }
        if (nowMs - since < OFF_CONFIRM_MS) return false
        events += TripEvent("off", listOf("since" to since, "lastOn" to s.lastSeenMs))
        finishOpen()
        closedByOff = true
        openReason = REASON_OFF
        return true
    }

    /**
     * Opens a session at the first reading. One place for what Task 2 adds: the start time and a catch-up base from
     * the persisted counters go here.
     */
    private fun openSession(boot: BootInfo, anchorMs: Long?, data: DiParsData, nowMs: Long): LiveSession {
        val c = TripCounters.of(data)
        events += TripEvent(
            "cycle_new",
            listOf("reason" to openReason, "boot" to boot.bootCount, "bootMs" to boot.bootMs, "anchor" to anchorMs, "odo" to c.odometerKm),
        )
        openReason = REASON_FIRST
        closedByOff = false
        lastOdoMs = nowMs
        return LiveSession(
            anchorMs ?: nowMs, nowMs, nowMs, c, c, lastMode = modeOf(data.gear, data.speed), lastRpm = data.engineRpm,
            rpmSeen = data.engineRpm != null, boot = boot, sleepAtLastMs = boot.sleepMs,
        )
    }

    /** Continues the session with a tick; a gap in ticks inside the cycle is noted for the log. */
    private fun follow(s: LiveSession, boot: BootInfo, data: DiParsData?, nowMs: Long): LiveSession {
        val gapMs = nowMs - s.lastSeenMs
        val next = if (data == null) s.ticked(nowMs) else s.advance(TripCounters.of(data), data, nowMs)
        if (gapMs > GAP_NOTE_MS) {
            val moved = if (data == null) null else odoDelta(s.latest, TripCounters.of(data))
            events += TripEvent("cycle_continue", listOf("gapS" to gapMs / MS_PER_SECOND, "moved" to moved))
        }
        return next.copy(sleepAtLastMs = boot.sleepMs, offSinceMs = null)
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
        val glitch = c.isBelow(latest) || isJump(this, c, nowMs)
        val rebased = rebaseOdometer(this, c, nowMs)
        if (c.odometerKm != null && c.odometerKm != latest.odometerKm) lastOdoMs = nowMs
        val kwh = counterDelta(latest.elecKwh, c.elecKwh)
        val litres = counterDelta(latest.fuelL, c.fuelL)
        // A gap in ticks (the app was away inside the cycle) goes to driving only if the odometer moved in it.
        val gapMode = if (nowMs - lastSeenMs > GAP_NOTE_MS) {
            if ((odoDelta(latest, c) ?: 0.0) >= ODO_MOVED_KM) Mode.DRIVE else Mode.PARK
        } else null
        return copy(
            lastSeenMs = nowMs, base = rebased.fillFrom(c), latest = latest.advanceBy(c),
            split = attributed(kwh, litres, nowMs, gapMode), lastMode = modeOf(data.gear, data.speed, odoDelta(latest, c)),
        ).counting(if (glitch) null else odoDelta(latest, c), data.engineRpm)
    }

    private fun counterDelta(from: Double?, to: Double?): Double = if (from == null || to == null) 0.0 else to - from

    /** The split with the interval since [LiveSession.lastSeenMs] and the deltas added to [LiveSession.lastMode] (or the gap's [gapMode]). */
    private fun LiveSession.attributed(kwh: Double, litres: Double, nowMs: Long, gapMode: Mode? = null): DrivePark {
        val mode = gapMode ?: lastMode ?: return split
        val ms = (nowMs - lastSeenMs).coerceAtLeast(0)
        val h = ms / MS_PER_HOUR
        // An implausible step (counter reset or scale change) adds no energy, only the time.
        val k = kwh.takeIf { it in -(1.0 + MAX_KW * h)..(1.0 + MAX_KW * h) } ?: 0.0
        val l = litres.takeIf { it in -0.5..(0.5 + MAX_LITRES_PER_H * h) } ?: 0.0
        return split.add(mode, ms, k, l)
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
        /** Boot times of one boot differ by clock jitter only; used when `BOOT_COUNT` is unreadable. */
        const val BOOT_SLACK_MS = 5_000L

        /** The head unit's sleep time may grow by this much between two ticks within a cycle. */
        const val SLEEP_SLACK_MS = 5_000L

        /** `powerState == 0` readings must span this long to end the cycle. */
        const val OFF_CONFIRM_MS = 5_000L

        /** No tick for longer than this inside a cycle is a gap (the app was away); it is attributed by the odometer. */
        const val GAP_NOTE_MS = 30_000L

        /** An old session whose author's session is gone is still adopted when its last tick is this recent. */
        const val MIGRATE_FRESH_MS = 120_000L
        private const val REASON_FIRST = "first"
        private const val REASON_BOOT = "boot"
        private const val REASON_SLEEP = "sleep"
        private const val REASON_OFF = "off"
        private const val REASON_MIGRATE = "migrate"
        private const val MS_PER_SECOND = 1000L
        const val MOVED_KM = 0.1
        const val FINISHED_TTL_MS = 31 * 24 * 3_600_000L
        const val MAX_FINISHED = 400

        /** Share of the trip km the rpm rule must cover for the estimate to use it. */
        const val MIN_RPM_COVERAGE = 0.9

        /** Faster than any car: a bigger odometer step is a counter scale change (cf. TripRecorder). */
        const val MAX_SPEED_KMH = 300.0
        const val MIN_JUMP_KM = 2.0
        const val MAX_TRIP_KM = 1500.0
        val KWH_RANGE = -5.0..500.0
        val LITRES_RANGE = -1.0..100.0
        private const val MS_PER_HOUR = 3_600_000.0
        private const val MAX_KW = 300.0
        private const val MAX_LITRES_PER_H = 60.0
    }
}

/**
 * Pure-Kotlin text form of [LiveTripState] for the prefs: `L7|open|finished|E`, fields joined by
 * commas, null = empty field, finished drives joined by `;`. `L6`, `L5`, `L4`, `L3`, `L2` and `L1` still read (older ones
 * without split, all without ice/ev km). The closing `E` makes a truncated string fail; any failure decodes
 * to an empty state.
 */
object LiveTripCodec {
    private const val HEAD = "L7"
    private const val HEAD_V6 = "L6"
    private const val HEAD_V5 = "L5"
    private const val HEAD_V4 = "L4"
    private const val HEAD_V3 = "L3"
    private const val HEAD_V2 = "L2"
    private const val HEAD_V1 = "L1"
    private const val TAIL = "E"
    private const val NONE = "-"
    private const val SESSION_FIELDS = 11
    private const val SPLIT_FIELDS = 6
    private const val ESTIMATE_FIELDS = 6
    private const val ICE_FIELDS = 2
    private const val SESSION_ICE_FIELDS = 3
    private const val SESSION_SEEN_FIELD = 1
    private const val SESSION_BOOT_FIELDS = 5

    fun encode(s: LiveTripState): String {
        val open = s.open?.let { o ->
            listOf(o.anchorMs, o.startMs, o.lastSeenMs).joinToString(",") +
                "," + counters(o.base) + "," + counters(o.latest) + "," + split(o.split) + "," + (o.lastMode?.name ?: "") +
                "," + listOf(o.iceKm, o.evKm, o.lastRpm).joinToString(",") { it?.toString() ?: "" } +
                "," + (if (o.rpmSeen) "1" else "0") +
                "," + listOf(o.boot?.bootCount, o.boot?.bootMs, o.boot?.sleepMs, o.sleepAtLastMs, o.offSinceMs)
                    .joinToString(",") { it?.toString() ?: "" }
        } ?: NONE
        val fin = s.finished.takeIf { it.isNotEmpty() }?.joinToString(";") {
            listOf(it.startMs, it.endMs, it.km, it.kwh ?: "", it.litres ?: "", it.hevKm ?: "").joinToString(",") +
                "," + (it.split?.let { sp -> split(sp) } ?: ",".repeat(SPLIT_FIELDS - 1)) +
                "," + listOf(it.iceKm, it.evKm).joinToString(",") { v -> v?.toString() ?: "" }
        } ?: NONE
        return listOf(HEAD, open, fin, TAIL).joinToString("|")
    }

    fun decode(raw: String?): LiveTripState = try {
        val parts = raw.orEmpty().split("|")
        require(parts.size == 4 && parts[0] in listOf(HEAD, HEAD_V6, HEAD_V5, HEAD_V4, HEAD_V3, HEAD_V2, HEAD_V1) && parts[3] == TAIL)
        val v = parts[0].substring(1).toInt()
        LiveTripState(open = session(parts[1], parts[0], v), finished = estimates(parts[2], v))
    } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
        LiveTripState()
    }

    private fun split(d: DrivePark) =
        listOf(d.driveMs, d.parkMs, d.driveKwh, d.parkKwh, d.driveL, d.parkL).joinToString(",")

    private fun counters(c: TripCounters) =
        listOf(c.odometerKm, c.elecKwh, c.fuelL, c.hevKm).joinToString(",") { it?.toString() ?: "" }

    /**
     * L3+: anchor,start,lastSeen,...; L2/L1: start,lastOn,lastReading,... (anchor = start, lastSeen = lastReading).
     * L4 adds split and mode, L5 ice km, ev km and rpm.
     */
    private fun session(s: String, head: String, v: Int): LiveSession? {
        if (s == NONE) return null
        val f = s.split(",")
        require(f.size == SESSION_FIELDS + (if (v >= 4) SPLIT_FIELDS + 1 else 0) + (if (v >= 5) SESSION_ICE_FIELDS else 0) + (if (v >= 6) SESSION_SEEN_FIELD else 0) + (if (v >= 7) SESSION_BOOT_FIELDS else 0))
        val first = f[0].toLong()
        val base = counters(f, 3)
        val latest = counters(f, 7)
        return when {
            v >= 4 -> {
                val at = SESSION_FIELDS + SPLIT_FIELDS + 1
                LiveSession(
                    first, f[1].toLong(), f[2].toLong(), base, latest,
                    split(f, SESSION_FIELDS) ?: DrivePark(), f[at - 1].takeIf { it.isNotEmpty() }?.let { Mode.valueOf(it) },
                    iceKm = null, evKm = null,
                ).withRpmFields(f, at, v).withBootFields(f, at + SESSION_ICE_FIELDS + SESSION_SEEN_FIELD, v)
            }
            head == HEAD_V3 -> LiveSession(first, f[1].toLong(), f[2].toLong(), base, latest, iceKm = null, evKm = null)
            else -> LiveSession(first, first, f[2].toLong(), base, latest, iceKm = null, evKm = null)
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

    private fun estimates(s: String, v: Int): List<TripEstimate> =
        if (s == NONE) emptyList() else s.split(";").map { estimate(it, v) }.takeLast(LiveTripTracker.MAX_FINISHED)

    private fun estimate(s: String, v: Int): TripEstimate {
        val f = s.split(",")
        require(f.size == ESTIMATE_FIELDS + (if (v >= 4) SPLIT_FIELDS else 0) + (if (v >= 5) ICE_FIELDS else 0))
        return TripEstimate(
            f[0].toLong(), f[1].toLong(), f[2].toDouble(), num(f[3]), num(f[4]), num(f[5]),
            if (v >= 4) split(f, ESTIMATE_FIELDS) else null,
            if (v >= 5) num(f[ESTIMATE_FIELDS + SPLIT_FIELDS]) else null,
            if (v >= 5) num(f[ESTIMATE_FIELDS + SPLIT_FIELDS + 1]) else null,
        )
    }

    private fun num(s: String): Double? = if (s.isEmpty()) null else s.toDouble()
}

/**
 * Prefs layout without Android: the open session under one key (rewritten often), the finished list under its own
 * (rewritten only when it changes), and the 4.4.1.14 single string migrated once.
 */
object LiveTripPersistence {
    class Restored(val state: LiveTripState, val migrated: Boolean)

    fun encodeOpen(s: LiveTripState): String = LiveTripCodec.encode(LiveTripState(open = s.open))

    fun encodeFinished(s: LiveTripState): String = LiveTripCodec.encode(LiveTripState(finished = s.finished))

    fun shouldSaveFinished(saved: List<TripEstimate>, now: List<TripEstimate>): Boolean = saved != now

    /**
     * [finishedRaw]: the finished-list file; [oldFinishedRaw]: the finished key an earlier build wrote beside the open
     * session; [legacyRaw]: the 4.4.1.14 single string. Each older source fills only what the newer ones lack, and
     * [Restored.migrated] asks the caller to write the finished file and drop the old keys.
     */
    fun restore(openRaw: String?, finishedRaw: String?, legacyRaw: String?, oldFinishedRaw: String? = null): Restored {
        val legacy = legacyRaw?.let { LiveTripCodec.decode(it) }
        val open = if (openRaw != null) LiveTripCodec.decode(openRaw).open else legacy?.open
        val finished = when {
            finishedRaw != null -> LiveTripCodec.decode(finishedRaw).finished
            oldFinishedRaw != null -> LiveTripCodec.decode(oldFinishedRaw).finished
            else -> legacy?.finished.orEmpty()
        }
        return Restored(LiveTripState(open, finished), migrated = legacyRaw != null || oldFinishedRaw != null)
    }
}

/** L5+ session tail at [at]: ice km, ev km, last rpm, (L6) rpm-seen flag; older versions keep ice/ev null. */
private fun LiveSession.withRpmFields(f: List<String>, at: Int, v: Int): LiveSession {
    if (v < 5) return this
    val rpm = f[at + 2].takeIf { it.isNotEmpty() }?.toInt()
    return copy(
        iceKm = f[at].toDoubleOrNull(), evKm = f[at + 1].toDoubleOrNull(), lastRpm = rpm,
        rpmSeen = if (v >= 6) f[at + 3] == "1" else rpm != null,
    )
}

/** L7 session tail at [at]: boot count, boot ms, sleep ms (all empty = no boot), sleep at the last tick, off-since. */
private fun LiveSession.withBootFields(f: List<String>, at: Int, v: Int): LiveSession {
    if (v < 7) return this
    val bootMs = f[at + 1].takeIf { it.isNotEmpty() }?.toLong()
    val boot = bootMs?.let { BootInfo(f[at].takeIf { c -> c.isNotEmpty() }?.toInt(), it, f[at + 2].toLong()) }
    return copy(boot = boot, sleepAtLastMs = f[at + 3].toLong(), offSinceMs = f[at + 4].takeIf { it.isNotEmpty() }?.toLong())
}
