package com.bydmate.app.ui.home.graphite

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class RangeScale(val ticks: List<Int>)

internal fun rangeScale(totalKm: Int): RangeScale {
    val step = when {
        totalKm >= 300 -> 100
        totalKm >= 100 -> 50
        else -> 20
    }
    val ticks = mutableListOf(0)
    var t = step
    while (t < totalKm) {
        if (totalKm - t >= 0.5 * step) ticks += t
        t += step
    }
    if (totalKm > 0) ticks += totalKm
    return RangeScale(ticks)
}

internal enum class TempKind(val min: Int, val max: Int) {
    OUTSIDE(-30, 50), CABIN(0, 35), BATTERY(-10, 44)
}

internal fun tempFraction(kind: TempKind, c: Int): Float =
    ((c - kind.min).toFloat() / (kind.max - kind.min)).coerceIn(0f, 1f)

internal fun outsideNote(c: Int?): String? = if (c != null && c <= 3) "Гололёд" else null

internal fun cabinNote(acAuto: Boolean?, acOn: Boolean?, acTemp: Int?): String? {
    if (acOn != true || acTemp == null) return null
    return if (acAuto == true) "Авто · $acTemp°" else "Климат · $acTemp°"
}

internal fun powerChip(powerState: Int?): String? = when (powerState) {
    2 -> "Включен · Ready"
    1 -> "Включен"
    0 -> "Выключен"
    else -> null
}

internal fun engineChip(rpm: Int?): String? =
    if (rpm != null && rpm > 0) "Двигатель заведён · ${grInt(rpm)} об/мин" else null

internal fun modeChip(driveMode: String?, profile: String?): String? {
    val base = when (driveMode) {
        "EV" -> "Электро"
        "HEV" -> "Гибрид"
        else -> return null
    }
    val p = when (profile) {
        null -> return base
        "ECO" -> "Эко"
        "NORMAL" -> "Норма"
        "SPORT" -> "Спорт"
        else -> profile.lowercase().replaceFirstChar { it.uppercase() }
    }
    return "$base · $p"
}

internal object ChargeEta {
    fun minutesToFull(charging: Boolean, socPercent: Int?, capacityKwh: Double?, powerKw: Double?): Int? {
        if (!charging || socPercent == null || socPercent >= 100) return null
        if (capacityKwh == null || capacityKwh <= 0.0) return null
        if (powerKw == null || abs(powerKw) < 0.5) return null
        return ceil((100 - socPercent) / 100.0 * capacityKwh / abs(powerKw) * 60).toInt()
    }
}

internal fun litresFromPercent(percent: Int?, tankL: Double?): Int? =
    if (percent == null || tankL == null) null else (percent / 100.0 * tankL).roundToInt()
