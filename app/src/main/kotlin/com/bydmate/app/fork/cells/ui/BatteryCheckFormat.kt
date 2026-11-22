package com.bydmate.app.fork.cells.ui

import android.content.res.Resources
import com.bydmate.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// Numbers, dates and string resources of the battery-check window. fork-local.

/** The text of [t]; a resource failure (a bad format in one language) gives an empty string, never a crash. */
@Suppress("SpreadOperator") // at most three format arguments per string
fun resolve(t: TextRes, r: Resources): String = runCatching {
    val args = t.args.map { a ->
        when (a) {
            is TextRes -> resolve(a, r)
            is Joined -> a.parts.joinToString(", ") { resolve(it, r) }
            else -> a
        }
    }
    // A text without arguments is not formatted (a bare «%» there would break String.format); its «%%» is an escaped «%».
    if (args.isEmpty()) r.getString(t.res).replace("%%", "%") else r.getString(t.res, *args.toTypedArray())
}.getOrDefault("")

private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM")
private val DAY_MONTH_TIME = DateTimeFormatter.ofPattern("dd.MM, HH:mm")

fun dayMonth(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = DAY_MONTH.withZone(zone).format(Instant.ofEpochMilli(ms))

fun dayMonthTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = DAY_MONTH_TIME.withZone(zone).format(Instant.ofEpochMilli(ms))

/** A deviation in whole mV with a typographic minus or a plus; zero has no sign. */
fun signedMv(d: Double): String {
    val r = d.roundToInt()
    return when {
        r < 0 -> "−${-r}"
        r > 0 -> "+$r"
        else -> "0"
    }
}

/** [v] with [digits] decimals in the locale's own separator. */
fun decimal(v: Double, digits: Int, locale: Locale): String = String.format(locale, "%.${digits}f", v)

/** A cell voltage in volts with three decimals: 3273 → «3,273». */
fun cellVolts(mv: Int, locale: Locale): String = decimal(mv / 1000.0, 3, locale)

/** «9 ч», «45 мин», «1 ч 20 мин». */
fun durationText(min: Int): TextRes {
    val h = min / 60
    val m = min % 60
    return when {
        h == 0 -> TextRes(R.string.fork_cells_dur_min, m)
        m == 0 -> TextRes(R.string.fork_cells_dur_h, h)
        else -> TextRes(R.string.fork_cells_dur_h_min, h, m)
    }
}

/** «19–20» or «20» when all probes agree. */
fun rangeText(min: Int, max: Int): String = if (min == max) "$min" else "$min–$max"
