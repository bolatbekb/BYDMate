package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.helper.push.FID_PUSH_DEVICE_CLASSES
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

/** A batch call longer than this holds the helper's one mutex too long (its watchdog ping has 2 s). */
internal const val SLOW_BATCH_MS = 1000L

/** A slow batch halves the session's batch size, never below this many symbols. */
internal const val MIN_BATCH_SYMBOLS = 8

/** An item whose batch failed in this many sweeps is not read again in the session. */
internal const val SKIP_AFTER_FAILURES = 2

/** Failed push starts back off 60 s, 120 s, 240 s … up to this. */
internal const val MAX_PUSH_BACKOFF_MS = 30 * 60_000L

internal data class ScanItem(val dev: Int, val fid: Int, val symbol: String)

/** What a sweep reads, and what it leaves out on purpose (see [isUnsafeRead]). */
internal data class ScanPlan(val items: List<ScanItem>, val unsafe: List<ScanItem>)

/** Readable symbols per device: devices ascending, catalog order inside; no setter ([isSetterName]),
 *  no Mqtt device, no unsafe read ([isUnsafeRead]). */
internal fun scanList(catalogDump: String): List<ScanItem> = scanPlan(catalogDump).items

/**
 * The `_SET` lines are dropped before the author's per-fid dedup, which keeps the first symbol of a
 * fid: a setter alias listed first must not hide the getter that shares its fid. A fid is left out
 * as unsafe when ANY of its names (the flat `BYDAutoFeatureIds` aliases included) is unsafe: the
 * dedup may have kept a harmless alias of a command-like fid.
 */
internal fun scanPlan(catalogDump: String): ScanPlan {
    val readable = catalogDump.lineSequence().filterNot { raw ->
        val eq = raw.lastIndexOf('=')
        eq > 0 && isSetterName(raw.substring(0, eq).trim())
    }.joinToString("\n")
    val all = groupCatalogByDevice(readable).toSortedMap().flatMap { (dev, symbols) ->
        if (dev == EXCLUDED_DEVICE) emptyList()
        else symbols.filterNot { isSetterName(it.symbol) }.map { ScanItem(dev, it.fid, it.symbol) }
    }
    val unsafe = unsafeFids(catalogDump)
    val (left, kept) = all.partition { it.fid in unsafe }
    return ScanPlan(kept, left)
}

/** The devices the push recorder subscribes to: every known one but the Mqtt diagnostic channel. */
internal fun pushDevices(): IntArray =
    FID_PUSH_DEVICE_CLASSES.keys.filter { it != EXCLUDED_DEVICE }.sorted().toIntArray()

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

/** True while the engine turns, false when it stands, null when the rpm is unknown. */
internal fun engineRunning(d: DiParsData?): Boolean? = d?.engineRpm?.let { it > 0 }

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
        "gun=${v(d?.chargeGunState)} rpm=${v(d?.engineRpm)} chg=${v(d?.chargingStatus)} bms=${v(d?.bmsState)} " +
        "power=${v(d?.powerState)} odo=${v(d?.mileage)}"
}

internal fun sweepDue(nowMs: Long, lastSweepMs: Long?, modeChanged: Boolean): Boolean {
    if (lastSweepMs == null) return true
    val gap = nowMs - lastSweepMs
    return gap >= SWEEP_INTERVAL_MS || (modeChanged && gap >= MODE_SWEEP_MIN_GAP_MS)
}

/** A message inside one log line: line breaks would start a line the format does not know. */
internal fun oneLine(text: String): String = text.replace('\r', ' ').replace('\n', ' ')
