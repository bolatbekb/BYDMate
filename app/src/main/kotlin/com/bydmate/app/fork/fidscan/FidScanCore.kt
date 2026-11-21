package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.helper.push.groupCatalogByDevice

// fork: pure core of the fid scan (diagnostic recorder of every autoservice parameter): scan list,
// car mode, log lines and the sweep schedule rule. No Android, no IO.

internal const val SWEEP_INTERVAL_MS = 5 * 60_000L
internal const val MODE_SWEEP_MIN_GAP_MS = 60_000L
internal const val TICK_MS = 10_000L
internal const val BUDGET_BYTES = 400L * 1024 * 1024
internal const val BATCH_SYMBOLS = 64
internal const val BATCH_PAUSE_MS = 20L
internal const val RETRY_MS = 60_000L
internal const val TX_INT = 5
internal const val TX_DOUBLE = 7
internal const val EXCLUDED_DEVICE = 1033

internal data class ScanItem(val dev: Int, val fid: Int, val symbol: String)

/** Readable symbols per device: devices ascending, catalog order inside; no `_SET`, no Mqtt device. */
internal fun scanList(catalogDump: String): List<ScanItem> =
    groupCatalogByDevice(catalogDump).toSortedMap().flatMap { (dev, symbols) ->
        if (dev == EXCLUDED_DEVICE) emptyList()
        else symbols.filterNot { it.symbol.endsWith("_SET") }.map { ScanItem(dev, it.fid, it.symbol) }
    }

internal enum class ScanMode { UNKNOWN, OFF, PARK, DRIVE, CHARGE_AC, CHARGE_DC, V2L }

internal fun scanMode(d: DiParsData?): ScanMode {
    if (d == null) return ScanMode.UNKNOWN
    when (d.chargeGunState) {
        2 -> return ScanMode.CHARGE_AC
        3, 4 -> return ScanMode.CHARGE_DC
        5 -> return ScanMode.V2L
    }
    if (d.powerState == 0) return ScanMode.OFF
    if (d.gear == 2 || d.gear == 4 || (d.speed ?: 0) > 0) return ScanMode.DRIVE
    return ScanMode.PARK
}

internal data class Reading(val intStatus: Int, val intValue: Int, val dblStatus: Int, val dblBits: Int)

internal fun readingText(r: Reading): String {
    val i = if (r.intStatus == 0) "i=${r.intValue}" else "i=!${r.intStatus}"
    val d = if (r.dblStatus == 0) "d=${Float.fromBits(r.dblBits)}" else "d=!${r.dblStatus}"
    return "$i $d"
}

internal fun valueLine(item: ScanItem, r: Reading): String = "V ${item.dev} ${item.symbol} ${readingText(r)}"

internal fun stateLine(timeText: String, mode: ScanMode, d: DiParsData?): String {
    fun v(x: Any?) = x?.toString() ?: "-"
    return "S $timeText mode=${mode.name} gear=${v(d?.gear)} speed=${v(d?.speed)} soc=${v(d?.soc)} " +
        "gun=${v(d?.chargeGunState)} power=${v(d?.powerState)} odo=${v(d?.mileage)}"
}

internal fun sweepDue(nowMs: Long, lastSweepMs: Long?, modeChanged: Boolean): Boolean {
    if (lastSweepMs == null) return true
    val gap = nowMs - lastSweepMs
    return gap >= SWEEP_INTERVAL_MS || (modeChanged && gap >= MODE_SWEEP_MIN_GAP_MS)
}
