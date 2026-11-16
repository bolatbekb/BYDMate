package com.bydmate.app.ui.home.graphite

import java.time.LocalDate
import java.time.LocalDateTime

private const val NNBSP = ' '
private const val MINUS = '−'

private fun group(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    s.forEachIndexed { i, c ->
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(NNBSP)
        sb.append(c)
    }
    return sb.toString()
}

internal fun grInt(v: Int): String =
    if (v < 0) "$MINUS" + group(-v.toLong()) else group(v.toLong())

internal fun grDec(v: Double, digits: Int = 1): String {
    val neg = v < 0
    val plain = java.math.BigDecimal(Math.abs(v)).setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString()
    val intPart = plain.substringBefore('.')
    val frac = if (plain.contains('.')) plain.substringAfter('.') else ""
    val body = group(intPart.toLong()) + if (frac.isNotEmpty()) ",$frac" else ""
    val isZero = body.all { it == '0' || it == ',' }
    return if (neg && !isZero) "$MINUS$body" else body
}

internal fun grDeg(v: Int?): String = if (v == null) "—" else grInt(v) + "°"

internal fun grDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m мин"
        m == 0 -> "$h ч"
        else -> "$h ч $m мин"
    }
}

internal fun grClock(now: LocalDateTime): String =
    "%02d:%02d".format(now.hour, now.minute)

private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

internal fun grDate(now: LocalDate): String =
    "${WEEKDAYS[now.dayOfWeek.value - 1]}, ${now.dayOfMonth} ${MONTHS[now.monthValue - 1]}"

internal fun grTrackTime(ms: Long?): String {
    if (ms == null) return "—:—"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
