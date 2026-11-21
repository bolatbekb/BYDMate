package com.bydmate.app.fork.fidscan

// fork: what the fid scan never reads, whatever the list says: setters and command-like fids.
// Reading a CAN signal only returns the last value the car sent; what a read of a service fid
// does inside autoservice is unknown, so those with an action in the name are left to the push
// recorder, which only listens.

/** Setter variants of the firmware: `_SET_lrse_hal_only`, `_SET_RSE_hal_only`, `_SET_TV_SCREEN_hal_only`... */
private val SETTER_VARIANT = Regex("_SET(?:_[A-Za-z0-9]+)*_hal_only$")

/** A setter, not a value: `..._SET` and its `_SET_<screen>_hal_only` variants. `_SET_GET` is a getter. */
internal fun isSetterName(symbol: String): Boolean = symbol.endsWith("_SET") || SETTER_VARIANT.containsMatchIn(symbol)

/** Lowest fid of a CAN signal: `(CAN_ID << 20) | (page << 12) | (startbit + 8)` with CAN id 1.
 *  Below it (and every negative fid) the value is the service's own, not a frame on the bus. */
internal const val CAN_SIGNAL_MIN_FID = 0x100000

/** Top byte of the command class: the diagnostic TX 0xAA00010A, the monitor-table writes, frame injection. */
internal const val COMMAND_CLASS_TOP = 0xAA

/** Action words of a service fid name, as whole `_`-separated words (RESTART and STARTUP do not match);
 *  SET only inside the name (`AUDIO_IVI_SET_MUSIC_INFO`), not as the Setting class's `SET_` start. */
private val ACTION_WORD = Regex(
    "(?:^|_)(?:CMD|COMMAND|RESET|ERASE|CLEAR|SEND|WRITE|START|STOP|REBOOT|QUERY|REQ|REQUEST|UPGRADE|" +
        "TRIGGER|DELETE|FORMAT|FACTORY)(?:_|$)|_SET_"
)

/**
 * A read the poll does not make. Reading a CAN signal only returns the last value the car sent,
 * whatever its name; what a read of a service fid does inside autoservice is unknown, so the
 * command class (top byte 0xAA) and service fids with an action word in the name are left to the
 * push recorder, which only listens. [constant] is the name without the class prefix.
 */
internal fun isUnsafeRead(fid: Int, constant: String): Boolean =
    fid ushr 24 == COMMAND_CLASS_TOP || (fid < CAN_SIGNAL_MIN_FID && ACTION_WORD.containsMatchIn(constant))

/** Every fid one of whose catalog names is an unsafe read. Device-id constants are not fids, and a
 *  setter name is no reason to skip the getter that shares its fid (the setter itself is never read). */
internal fun unsafeFids(catalogDump: String): Set<Int> =
    catalogDump.lineSequence()
        .mapNotNull(::catalogPair)
        .filter { (name, fid) ->
            !name.startsWith("BYDAutoConstants.") && !isSetterName(name) && isUnsafeRead(fid, name.substringAfterLast('.'))
        }
        .map { it.second }
        .toSet()

/** `Name=fid` of a dump line, or null when the line is not one. */
private fun catalogPair(raw: String): Pair<String, Int>? {
    val eq = raw.lastIndexOf('=')
    if (eq <= 0) return null
    val fid = raw.substring(eq + 1).trim().toIntOrNull() ?: return null
    return raw.substring(0, eq).trim() to fid
}
