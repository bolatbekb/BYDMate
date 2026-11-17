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
)

/**
 * The PM2.5 memo after one more read. A null read also happens while the autoservice is not connected yet, so
 * "no sensor" is stored only after two nulls in a row; one null keeps [current]. Returns the memo and the new count.
 */
internal fun nextPmMemo(current: Pm25Memo, read: Pm25?, nullsInARow: Int): Pair<Pm25Memo, Int> {
    if (read != null) return Pm25Memo.Reading(read) to 0
    val nulls = nullsInARow + 1
    return (if (nulls >= 2) Pm25Memo.None else current) to nulls
}

/** Key of a warm energy series: its labels depend on the app language. */
internal data class EnergyKey(val period: ChartPeriod, val lang: GrLang)

/** Pure text codec of [WarmPrefs]: `W1|pm|soh|tank|capacity|E`; any damage decodes to the empty value. */
internal object GraphiteWarmCodec {
    private const val HEAD = "W1"
    private const val TAIL = "E"
    private const val UNKNOWN = "?"
    private const val NONE = "-"
    private const val PM_FIELDS = 4
    private const val PARTS = 6

    fun encode(w: WarmPrefs): String {
        val pm = when (val m = w.pm25) {
            Pm25Memo.Unknown -> UNKNOWN
            Pm25Memo.None -> NONE
            is Pm25Memo.Reading -> listOf(m.pm.inside, m.pm.outside, m.pm.levelIn, m.pm.levelOut)
                .joinToString(",") { it?.toString() ?: "" }
        }
        return listOf(HEAD, pm, w.sohPercent ?: "", w.tankL ?: "", w.capacityKwh ?: "", TAIL).joinToString("|")
    }

    fun decode(raw: String?): WarmPrefs = try {
        val p = raw.orEmpty().split("|")
        require(p.size == PARTS && p[0] == HEAD && p[5] == TAIL)
        WarmPrefs(
            pm25 = pm(p[1]),
            sohPercent = p[2].takeIf { it.isNotEmpty() }?.toFloat()?.takeIf { it.isFinite() },
            tankL = p[3].takeIf { it.isNotEmpty() }?.toDouble()?.takeIf { it.isFinite() },
            capacityKwh = p[4].takeIf { it.isNotEmpty() }?.toDouble()?.takeIf { it.isFinite() },
        )
    } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
        EMPTY
    }

    private val EMPTY = WarmPrefs(Pm25Memo.Unknown, null, null, null)

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
 * changes); the energy series and the trip card live in memory only. Safe to use from any thread.
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
    @Volatile var trip: TripCardModel? = null

    /** The period the chart showed last in this process; the chart grows in only when it changes. */
    @Volatile var lastShownPeriod: ChartPeriod? = null

    /** Applies one more PM2.5 read to the memo. */
    internal fun onPmRead(read: Pm25?) = synchronized(lock) {
        val (memo, nulls) = nextPmMemo(pm25, read, pmNullsInARow)
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
        prefs = p
        save()
    }

    /** Runs a setter body under the lock; saves when it reports a change. */
    private inline fun change(body: () -> Boolean): Unit = synchronized(lock) { if (body()) save() }

    private fun save(): Unit = synchronized(lock) {
        val p = prefs ?: return
        val text = GraphiteWarmCodec.encode(WarmPrefs(pm25, sohPercent, tankL, capacityKwh))
        if (text == saved) return
        saved = text
        runCatching { p.edit().putString(KEY, text).apply() }
    }
}
