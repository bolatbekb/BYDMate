package com.bydmate.app.ui.home.graphite

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class RangeScale(val ticks: List<Int>)

internal fun rangeScale(totalKm: Int): RangeScale {
    // At most ~7 labels, so a 1 200 km range does not crowd the scale.
    val step = when {
        totalKm >= 2000 -> 500
        totalKm >= 700 -> 200
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

internal fun outsideNote(c: Int?, lang: GrLang = GrLang.RU): String? = if (c != null && c <= 3) lang.words.ice else null

internal fun cabinNote(acAuto: Boolean?, acOn: Boolean?, acTemp: Int?, lang: GrLang = GrLang.RU): String? {
    if (acOn != true || acTemp == null) return null
    val t = grDec(acTemp.toDouble(), lang = lang) + "°"
    return (if (acAuto == true) lang.words.acAuto else lang.words.climate) + " · $t"
}

/** «Ready» stays as the car shows it. */
internal fun powerChip(powerState: Int?, lang: GrLang = GrLang.RU): String? = when (powerState) {
    2 -> lang.words.powerOn + " · Ready"
    1 -> lang.words.powerOn
    0 -> lang.words.powerOff
    else -> null
}

/** The rpm itself is on the fuel dial, so the chip only says the engine runs. */
internal fun engineChip(rpm: Int?, lang: GrLang = GrLang.RU): String? =
    if (rpm != null && rpm > 0) lang.words.engineRunning else null

internal fun modeChip(driveMode: String?, profile: String?): String? {
    val base = when (driveMode) {
        "EV" -> "EV"
        "HEV" -> "HEV"
        else -> return null
    }
    // Profiles read as the car names them: ECO, NORMAL, SPORT, SNOW…
    return if (profile == null) base else "$base · $profile"
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

/** Longer than the slowest poll (60 s backoff), so a parked car between reads stays online. */
internal const val LIVE_FRESH_MS = 90_000L

/** The car answers ([connected]) and the last snapshot is recent; 0 = never got one. */
internal fun isOnline(connected: Boolean, lastDataAtMs: Long, nowMs: Long): Boolean =
    connected && lastDataAtMs > 0L && nowMs - lastDataAtMs <= LIVE_FRESH_MS
