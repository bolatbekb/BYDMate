package com.bydmate.app.fork.fidscan

import android.util.Log
import com.bydmate.app.helper.push.FidRecLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// fork: raw stream capture of the fid scan test build. Runs in the helper daemon, next to the
// author's recorder: the recorder hands every event's byte buffer here (one hook line in
// FidRecorder.kt), and the lines go into the recorder's own file, so the 400 MB budget of the
// scan counts them. Goal: per-cell voltages and temperatures of the battery (BMS frames 0x43D,
// 0x43C, 0x43A...) from the GB / BigData / Yun streams, which carry one CAN frame per buffer.
// Listen only: nothing here talks to the car.

/** The daemon's single tap; [attach] replaces it with a fresh one for each recorder file. */
internal object FidRawTap {
    @Volatile private var tap: RawTap? = null
    @Volatile private var failureLogged = false

    /** The author's recorder opened a new file: start over, writing into [log]. */
    fun attach(log: FidRecLog) {
        tap = null
        runCatching {
            val fresh = RawTap(write = { line -> log.header(listOf(line)) })
            fresh.describe()
            tap = fresh
        }.onFailure { warn("attach", it) }
    }

    /** Vendor binder thread. Never throws: a failure costs this buffer only. */
    fun onBuffer(fid: Int, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        val current = tap ?: return
        runCatching { current.onBuffer(fid, bytes) }.onFailure { warn("buffer fid=$fid", it) }
    }

    /** Tests only: forget the tap. */
    internal fun reset() {
        tap = null
        failureLogged = false
    }

    private fun warn(what: String, t: Throwable) {
        if (failureLogged) return
        failureLogged = true
        runCatching { Log.w("FidRawTap", "$what failed: $t") }
    }
}

/** Envelope of a stream record: CAN id, sub, channel; [payloadLen] = buffer size minus [ENVELOPE_BYTES]. */
internal data class RawRecord(val can: Int, val sub: Int, val ch: Int, val payloadLen: Int)

/**
 * The 10-byte envelope of a stream callback buffer (denza-lab, DiLink 5.1): CAN id 4 bytes BE,
 * sub, channel, counter 4 bytes BE, then the CAN payload. Only fids of the stream callbacks
 * (`*_DYNAMIC_DATA_CALLBACK`, top byte 0x99) are read this way; null for anything else.
 */
internal fun parseEnvelope(fid: Int, b: ByteArray): RawRecord? {
    if (fid ushr 24 != STREAM_FID_TOP || b.size < ENVELOPE_BYTES) return null
    val can = (u(b[0]) shl 24) or (u(b[1]) shl 16) or (u(b[2]) shl 8) or u(b[3])
    if (can !in 0..MAX_CAN_ID) return null
    return RawRecord(can, u(b[4]), u(b[5]), b.size - ENVELOPE_BYTES)
}

/** BMS frames: each page (payload byte 0) gets its own line on every change. */
internal fun isBatteryFrame(can: Int): Boolean = can in BATTERY_CAN_FIRST..BATTERY_CAN_LAST

/** Upper-case hex of at most [MAX_HEX_BYTES] bytes, `+` marking a cut. */
internal fun hexOf(b: ByteArray): String {
    val n = minOf(b.size, MAX_HEX_BYTES)
    val sb = StringBuilder(n * 2 + 1)
    for (i in 0 until n) {
        val v = u(b[i])
        sb.append(HEX[v ushr 4]).append(HEX[v and 0xF])
    }
    if (b.size > n) sb.append('+')
    return sb.toString()
}

/**
 * Decides which buffers earn a line. Three kinds of lines:
 * - `buf`: first sighting of a key, then every change of its content (the stream's counter
 *   bytes ignored). Key = fid + CAN id + sub + channel, plus the page byte for BMS frames.
 *   BMS keys: at most one line per [FAST_GAP_MS]; others: per [SLOW_GAP_MS]. A change is
 *   compared with the last WRITTEN content, so the last value of a burst still lands with the
 *   next cyclic frame.
 * - `win`: every buffer, unfiltered, for [WINDOW_MS] every [PERIOD_MS] (first window at
 *   attach) — to check the envelope and the rates on this car.
 * - `bufstat`: per route (fid, CAN id, sub, channel, size) how many buffers came and how many
 *   lines were written in the last period; written when a period ends.
 *
 * Intervals use [clock] (monotonic); [wall] only stamps the lines. All calls are serialized.
 */
internal class RawTap(
    private val write: (String) -> Unit,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wall: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val fid: Int, val can: Int, val sub: Int, val ch: Int, val page: Int)
    private data class Route(val fid: Int, val can: Int, val sub: Int, val ch: Int, val size: Int)
    private class Written(var content: ByteArray, var atMs: Long)
    private class Count(var buffers: Long = 0, var lines: Long = 0)

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val last = HashMap<Key, Written>()
    private val routes = LinkedHashMap<Route, Count>()
    private var startMs = clock()
    private var period = 0L
    private var keysDropped = 0L
    private var routesDropped = 0L

    fun describe() = synchronized(this) {
        write(
            "fidscan raw: buffers of stream callbacks (fid 0x99xxxxxx: envelope can 4B BE, sub, ch, " +
                "counter 4B BE, then payload) and of any other fid. buf = first sighting or change " +
                "(counter ignored), BMS frames 0x%X-0x%X per page every change (>= %d ms), others >= %d s; "
                    .format(BATTERY_CAN_FIRST, BATTERY_CAN_LAST, FAST_GAP_MS, SLOW_GAP_MS / 1000) +
                "win = every buffer for %d s every %d min; bufstat = per route counts every %d min"
                    .format(WINDOW_MS / 1000, PERIOD_MS / 60_000, PERIOD_MS / 60_000)
        )
    }

    fun onBuffer(fid: Int, bytes: ByteArray) = synchronized(this) {
        var now = clock()
        if (now < startMs) startMs = now
        now -= startMs
        val current = now / PERIOD_MS
        if (current != period) {
            writeStats()
            period = current
        }
        val rec = parseEnvelope(fid, bytes)
        val count = countOf(fid, rec, bytes.size)
        if (count != null) count.buffers++
        if (now % PERIOD_MS < WINDOW_MS) write(line("win", fid, bytes))

        val key = keyOf(fid, rec, bytes)
        val content = contentOf(rec, bytes)
        val prev = last[key]
        val gap = if (rec != null && isBatteryFrame(rec.can)) FAST_GAP_MS else SLOW_GAP_MS
        when {
            prev == null -> {
                if (last.size >= MAX_KEYS) {
                    keysDropped++
                    return@synchronized
                }
                last[key] = Written(content, now)
            }
            prev.content.contentEquals(content) || now - prev.atMs < gap -> return@synchronized
            else -> {
                prev.content = content
                prev.atMs = now
            }
        }
        if (count != null) count.lines++
        write(line("buf", fid, bytes))
    }

    private fun keyOf(fid: Int, rec: RawRecord?, bytes: ByteArray): Key = when {
        rec == null -> Key(fid, -1, -1, -1, -1)
        isBatteryFrame(rec.can) && rec.payloadLen > 0 -> Key(fid, rec.can, rec.sub, rec.ch, u(bytes[ENVELOPE_BYTES]))
        else -> Key(fid, rec.can, rec.sub, rec.ch, -1)
    }

    /** The buffer with the counter bytes zeroed, so a counter-only change is no change. */
    private fun contentOf(rec: RawRecord?, bytes: ByteArray): ByteArray {
        val copy = bytes.copyOf()
        if (rec != null) for (i in COUNTER_FIRST until ENVELOPE_BYTES) copy[i] = 0
        return copy
    }

    private fun countOf(fid: Int, rec: RawRecord?, size: Int): Count? {
        val route = Route(fid, rec?.can ?: -1, rec?.sub ?: -1, rec?.ch ?: -1, size)
        routes[route]?.let { return it }
        if (routes.size >= MAX_ROUTES) {
            routesDropped++
            return null
        }
        return Count().also { routes[route] = it }
    }

    private fun writeStats() {
        val at = stamp.format(Date(wall()))
        for ((r, c) in routes) {
            if (c.buffers == 0L) continue
            val env = if (r.can < 0) "can=- sub=- ch=-" else "can=0x%X sub=%d ch=%d".format(r.can, r.sub, r.ch)
            write("$at bufstat fid=%08X $env n=${r.size} count=${c.buffers} lines=${c.lines}".format(r.fid))
            c.buffers = 0
            c.lines = 0
        }
        if (keysDropped > 0 || routesDropped > 0) {
            write("$at bufstat dropped keys=$keysDropped routes=$routesDropped")
            keysDropped = 0
            routesDropped = 0
        }
    }

    private fun line(kind: String, fid: Int, bytes: ByteArray): String =
        "${stamp.format(Date(wall()))} $kind fid=%08X n=${bytes.size} ${hexOf(bytes)}".format(fid)
}

private fun u(b: Byte): Int = b.toInt() and 0xFF

private val HEX = "0123456789ABCDEF".toCharArray()

/** Top byte of the stream callback fids (GB 0x9900001F, BigData 0x99000020, Yun 0x99000021...). */
private const val STREAM_FID_TOP = 0x99
internal const val ENVELOPE_BYTES = 10
private const val COUNTER_FIRST = 6
private const val MAX_CAN_ID = 0x1FFFFFFF

/** BMS block of the CAN map (0x43A counts, 0x43B probe extremes, 0x43C probes, 0x43D cells, 0x446/0x447 extremes). */
internal const val BATTERY_CAN_FIRST = 0x438
internal const val BATTERY_CAN_LAST = 0x44F

internal const val FAST_GAP_MS = 250L
internal const val SLOW_GAP_MS = 10_000L
internal const val WINDOW_MS = 15_000L
internal const val PERIOD_MS = 10 * 60_000L
internal const val MAX_KEYS = 4096
internal const val MAX_ROUTES = 4096
internal const val MAX_HEX_BYTES = 128
