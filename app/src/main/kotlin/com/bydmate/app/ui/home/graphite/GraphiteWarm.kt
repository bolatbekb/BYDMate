package com.bydmate.app.ui.home.graphite

import android.content.Context
import com.bydmate.app.data.autoservice.Pm25
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.ui.home.cdrive.ChartPeriod

/** What the last PM2.5 read found: nothing known yet, no sensor, or a reading. */
internal sealed interface Pm25Memo {
    object Unknown : Pm25Memo
    object None : Pm25Memo
    data class Reading(val pm: Pm25) : Pm25Memo
}

/** The values [GraphiteWarm] keeps across a process restart. */
internal data class WarmPrefs(
    val pm25: Pm25Memo,
    val sohPercent: Float?,
    val tankL: Double?,
    val capacityKwh: Double?,
    val trip: StoredTrip? = null,
)

/** The trip card as last seen, with the local date it belongs to (ISO yyyy-MM-dd). */
internal data class StoredTrip(val day: String, val model: TripCardModel)

/**
 * The stored trip card when it is from [today], else null. A CURRENT card is returned as LAST: after a restart the
 * live session decides again within a tick, and a LAST header is right in the common case (ignition was turned off).
 */
internal fun tripForToday(stored: StoredTrip?, today: String): TripCardModel? {
    val s = stored?.takeIf { it.day == today } ?: return null
    return if (s.model.mode == TripCardMode.CURRENT) s.model.copy(mode = TripCardMode.LAST) else s.model
}

/**
 * The PM2.5 memo after one more read. A null read also happens while the autoservice is not connected yet, so
 * "no sensor" is stored only after two nulls in a row; one null keeps [current]. A null while the car link is
 * down ([linkUp] false) counts as nothing. Returns the memo and the new count.
 */
internal fun nextPmMemo(current: Pm25Memo, read: Pm25?, nullsInARow: Int, linkUp: Boolean = true): Pair<Pm25Memo, Int> {
    if (read != null) return Pm25Memo.Reading(read) to 0
    if (!linkUp) return current to nullsInARow // a null from a dead car link is a failed read, not "no sensor"
    val nulls = nullsInARow + 1
    return (if (nulls >= 2) Pm25Memo.None else current) to nulls
}

/** Key of a warm energy series: its labels depend on the app language. */
internal data class EnergyKey(val period: ChartPeriod, val lang: GrLang)

/** Pure text codec of [WarmPrefs]: `W3|pm|soh|tank|capacity|trip|E` (W2 = the same with the old trip card, W1 without trip: both decode with no trip); any damage decodes to the empty value. */
internal object GraphiteWarmCodec {
    private const val HEAD = "W3"
    private const val HEAD_V2 = "W2"
    private const val HEAD_V1 = "W1"
    private const val TAIL = "E"
    private const val UNKNOWN = "?"
    private const val NONE = "-"
    private const val PM_FIELDS = 4
    private const val PARTS = 7
    private const val PARTS_V1 = 6
    private const val TRIP_FIELDS = 9

    fun encode(w: WarmPrefs): String {
        val pm = when (val m = w.pm25) {
            Pm25Memo.Unknown -> UNKNOWN
            Pm25Memo.None -> NONE
            is Pm25Memo.Reading -> listOf(m.pm.inside, m.pm.outside, m.pm.levelIn, m.pm.levelOut)
                .joinToString(",") { it?.toString() ?: "" }
        }
        return listOf(HEAD, pm, w.sohPercent ?: "", w.tankL ?: "", w.capacityKwh ?: "", trip(w.trip), TAIL).joinToString("|")
    }

    fun decode(raw: String?): WarmPrefs = try {
        val p = raw.orEmpty().split("|")
        val v1 = p.size == PARTS_V1 && p[0] == HEAD_V1
        // the old trip card (avg speed, electric share) has another shape: dropped, the rest is kept
        val v2 = p.size == PARTS && p[0] == HEAD_V2
        require(v1 || v2 || (p.size == PARTS && p[0] == HEAD))
        require(p.last() == TAIL)
        WarmPrefs(
            pm25 = pm(p[1]),
            sohPercent = p[2].takeIf { it.isNotEmpty() }?.toFloat()?.takeIf { it.isFinite() },
            tankL = p[3].takeIf { it.isNotEmpty() }?.toDouble()?.takeIf { it.isFinite() },
            capacityKwh = p[4].takeIf { it.isNotEmpty() }?.toDouble()?.takeIf { it.isFinite() },
            trip = if (v1 || v2) null else tripOrNull(p[5]),
        )
    } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
        EMPTY
    } catch (@Suppress("SwallowedException") e: java.time.format.DateTimeParseException) {
        EMPTY
    }

    private val EMPTY = WarmPrefs(Pm25Memo.Unknown, null, null, null)

    /** `day;mode;km;driveMin;parkMin;driveKwh;driveL;parkKwh;parkL`; empty = no trip. EMPTY cards are never stored. */
    private fun trip(t: StoredTrip?): String {
        val m = t?.model ?: return ""
        if (m.mode == TripCardMode.EMPTY) return ""
        return listOf(t.day, m.mode.name, m.km, m.driveMinutes, m.parkMinutes, m.driveKwh, m.driveL, m.parkKwh, m.parkL)
            .joinToString(";") { it?.toString() ?: "" }
    }

    private fun tripOrNull(s: String): StoredTrip? {
        if (s.isEmpty()) return null
        val f = s.split(";")
        require(f.size == TRIP_FIELDS)
        java.time.LocalDate.parse(f[0])
        val mode = TripCardMode.valueOf(f[1])
        require(mode != TripCardMode.EMPTY)
        fun d(i: Int) = f[i].takeIf { it.isNotEmpty() }?.toDouble()?.takeIf { it.isFinite() }
        fun n(i: Int) = f[i].takeIf { it.isNotEmpty() }?.toInt()
        return StoredTrip(f[0], TripCardModel(mode, d(2), n(3), n(4), d(5), d(6), d(7), d(8)))
    }

    // NumberFormatException is an IllegalArgumentException, so a bad number is caught by decode.
    private fun pm(s: String): Pm25Memo = when (s) {
        UNKNOWN -> Pm25Memo.Unknown
        NONE -> Pm25Memo.None
        else -> {
            val f = s.split(",")
            require(f.size == PM_FIELDS)
            val n = f.map { it.takeIf { v -> v.isNotEmpty() }?.toInt() }
            Pm25Memo.Reading(Pm25(n[0], n[1], n[2], n[3]))
        }
    }
}

/**
 * The last known Graphite values, for the whole process: the home's ViewModel starts from them so the first
 * frame has the final layout. PM2.5, SOH and the capacities survive a restart in prefs (written only when one
 * changes); the trip card (today's only) survives too; the energy series lives in memory only. Safe to use from any thread.
 */
object GraphiteWarm {
    private const val PREFS = "bydmate_graphite_warm"
    private const val PREFS_DEMO = "bydmate_graphite_warm_demo"
    private const val KEY = "state"

    private val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    private var started = false
    private var saved: String? = null

    @Volatile internal var pm25: Pm25Memo = Pm25Memo.Unknown
        set(value) = change { if (field != value) { field = value; true } else false }
    @Volatile var sohPercent: Float? = null
        set(value) = change { if (field != value) { field = value; true } else false }
    @Volatile var tankL: Double? = null
        set(value) = change { if (field != value) { field = value; true } else false }
    @Volatile var capacityKwh: Double? = null
        set(value) = change { if (field != value) { field = value; true } else false }

    /** Consecutive empty PM2.5 reads in this process (see [nextPmMemo]). */
    @Volatile internal var pmNullsInARow: Int = 0

    internal val energy: MutableMap<EnergyKey, EnergySeries> = java.util.concurrent.ConcurrentHashMap()

    private var storedTrip: StoredTrip? = null

    /**
     * Today's trip card as last seen (also across a restart); a card from another day reads as null, a CURRENT one
     * as LAST (see [tripForToday]). EMPTY is not kept.
     */
    var trip: TripCardModel?
        get() = synchronized(lock) { tripForToday(storedTrip, today()) }
        set(value) = change {
            val n = value?.takeIf { it.mode != TripCardMode.EMPTY }?.let { StoredTrip(today(), it) }
            if (n != storedTrip) { storedTrip = n; true } else false
        }

    private fun today(): String = java.time.LocalDate.now().toString()

    /** The period the chart showed last in this process; the chart grows in only when it changes. */
    @Volatile var lastShownPeriod: ChartPeriod? = null

    /** Applies one more PM2.5 read to the memo. */
    internal fun onPmRead(read: Pm25?, linkUp: Boolean) = synchronized(lock) {
        val (memo, nulls) = nextPmMemo(pm25, read, pmNullsInARow, linkUp)
        pmNullsInARow = nulls
        pm25 = memo
    }

    fun init(context: Context): Unit = synchronized(lock) {
        if (started) return
        started = true
        val name = if (DemoMode.isOn(context)) PREFS_DEMO else PREFS
        val p = runCatching { context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) }.getOrNull()
        val raw = runCatching { p?.getString(KEY, null) }.getOrNull()
        val w = GraphiteWarmCodec.decode(raw)
        saved = raw
        // prefs is still null here, so the assignments do not write; one save at the end.
        // Values already recorded before init win over the stored ones.
        if (pm25 == Pm25Memo.Unknown) pm25 = w.pm25
        if (sohPercent == null) sohPercent = w.sohPercent
        if (tankL == null) tankL = w.tankL
        if (capacityKwh == null) capacityKwh = w.capacityKwh
        if (storedTrip == null) storedTrip = w.trip
        prefs = p
        save()
    }

    /** Runs a setter body under the lock; saves when it reports a change. */
    private inline fun change(body: () -> Boolean): Unit = synchronized(lock) { if (body()) save() }

    private fun save(): Unit = synchronized(lock) {
        val p = prefs ?: return
        val text = GraphiteWarmCodec.encode(WarmPrefs(pm25, sohPercent, tankL, capacityKwh, storedTrip))
        if (text == saved) return
        saved = text
        runCatching { p.edit().putString(KEY, text).apply() }
    }
}
