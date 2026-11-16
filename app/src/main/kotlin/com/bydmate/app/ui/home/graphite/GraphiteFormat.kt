package com.bydmate.app.ui.home.graphite

import java.time.LocalDate
import java.util.Locale

private const val MINUS = '−'

private fun group(n: Long, sep: Char): String {
    val s = n.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(sep)
        sb.append(c)
    }
    return sb.toString()
}

internal fun grInt(v: Int, lang: GrLang = GrLang.RU): String {
    val sep = lang.words.group
    return if (v < 0) "$MINUS" + group(-v.toLong(), sep) else group(v.toLong(), sep)
}

internal fun grDec(v: Double, digits: Int = 1, lang: GrLang = GrLang.RU): String {
    if (!v.isFinite()) return "—"
    val w = lang.words
    val neg = v < 0
    val plain = java.math.BigDecimal(Math.abs(v)).setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString()
    val intPart = plain.substringBefore('.')
    val frac = if (plain.contains('.')) plain.substringAfter('.') else ""
    val body = group(intPart.toLong(), w.group) + if (frac.isNotEmpty()) "${w.decimal}$frac" else ""
    val isZero = body.all { it == '0' || it == w.decimal }
    return if (neg && !isZero) "$MINUS$body" else body
}

internal fun grDeg(v: Int?, lang: GrLang = GrLang.RU): String = if (v == null) "—" else grInt(v, lang) + "°"

internal fun grDuration(minutes: Int, lang: GrLang = GrLang.RU): String {
    val w = lang.words
    val h = minutes / 60
    val m = minutes % 60
    val hours = "$h${w.unitGap}${w.hour}"
    val mins = "$m${w.unitGap}${w.minute}"
    return when {
        h == 0 -> mins
        m == 0 -> hours
        else -> hours + w.unitGap + mins
    }
}

internal fun grDate(now: LocalDate, lang: GrLang = GrLang.RU): String {
    val w = lang.words
    return w.date(w.dateWeekdays[now.dayOfWeek.value - 1], now.dayOfMonth, w.months[now.monthValue - 1], now.year)
}

/** Digits and colons only, the same in every language. */
internal fun grTrackTime(ms: Long?): String {
    if (ms == null) return "—:—"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
