package com.bydmate.app.ui.home.graphite

import android.content.Context
import android.util.Log
import com.bydmate.app.data.remote.DiParsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One tick of the trip log: the raw reading plus what the tracker made of it. No position, no names. */
data class TripTick(
    val anchorMs: Long?, val gear: Int?, val speed: Int?, val rpm: Int?, val powerState: Int?,
    val odoKm: Double?, val kwhCounter: Double?, val fuelL: Double?, val hevKm: Int?,
    val mode: Mode?, val km: Double?, val iceKm: Double?, val evKm: Double?, val driveMs: Long?, val parkMs: Long?,
    /** True = a new reading this tick, false = the ticker re-fed the old one, null = not told. */
    val fresh: Boolean? = null,
) {
    companion object {
        fun of(anchorMs: Long?, d: DiParsData?, open: LiveSession?, fresh: Boolean? = null): TripTick {
            val est = open?.estimate()
            return TripTick(
                anchorMs, d?.gear, d?.speed, d?.engineRpm, d?.powerState, d?.mileage, d?.totalElecConsumption,
                d?.fuelTotalL, d?.mileageHevKm, open?.lastMode, est?.km, open?.iceKm, open?.evKm,
                open?.split?.driveMs, open?.split?.parkMs, fresh,
            )
        }
    }
}

/** Pure text forms of the trip log. A tick line starts with the local time; an event line is `# EVENT name key=value…`. */
object TripLogFormat {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
    const val HEADER_COLUMNS = "time a=anchor g=gear v=speed rpm ps=powerState f=fresh(1/0) odo kwh fuel hev m=mode km ice ev dr=driveMs pk=parkMs"

    fun clock(ms: Long, zone: ZoneId): String = CLOCK.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun num(v: Double?): String = when {
        v == null -> "-"
        !v.isFinite() -> "nan"
        else -> String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    private fun any(v: Any?): String = when (v) {
        null -> "-"
        is Double -> num(v)
        else -> v.toString().replace(' ', '_')
    }

    fun header(day: LocalDate): String = "# BYDMate trip log v1 date=$day columns: $HEADER_COLUMNS"

    fun tick(tsMs: Long, t: TripTick, zone: ZoneId): String = buildString {
        append(clock(tsMs, zone))
        append(" a=").append(t.anchorMs?.let { clock(it, zone) } ?: "-")
        append(" g=").append(any(t.gear)).append(" v=").append(any(t.speed)).append(" rpm=").append(any(t.rpm))
        append(" ps=").append(any(t.powerState)).append(" f=").append(t.fresh?.let { if (it) "1" else "0" } ?: "-").append(" odo=").append(num(t.odoKm)).append(" kwh=").append(num(t.kwhCounter))
        append(" fuel=").append(num(t.fuelL)).append(" hev=").append(any(t.hevKm))
        append(" m=").append(t.mode?.name?.first() ?: '-')
        append(" km=").append(num(t.km)).append(" ice=").append(num(t.iceKm)).append(" ev=").append(num(t.evKm))
        append(" dr=").append(any(t.driveMs)).append(" pk=").append(any(t.parkMs))
    }

    fun event(tsMs: Long, name: String, zone: ZoneId, fields: List<Pair<String, Any?>> = emptyList()): String =
        buildString {
            append("# EVENT ").append(name).append(" t=").append(clock(tsMs, zone))
            fields.forEach { (k, v) -> append(' ').append(k).append('=').append(any(v)) }
        }
}

/**
 * Rolling daily files `YYYY-MM-DD.log` in [dir], the last [keepDays] days kept (today included). Every line is appended
 * and the file closed, so a power-off loses nothing already written. Plain java.io, no Android.
 */
class TripLogStore(
    private val dir: File,
    private val clock: () -> Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val keepDays: Int = 3,
    private val maxDayBytes: Long = MAX_DAY_BYTES,
) {
    private var lastDay: LocalDate? = null
    private var cappedDay: LocalDate? = null
    private var out: FileOutputStream? = null
    private var outDay: LocalDate? = null

    init { prune() }

    private fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun fileOf(day: LocalDate) = File(dir, "$day.log")

    /** Appends one line to the file of the day of [tsMs]; the first line of a new file is its header. */
    @Synchronized
    fun append(tsMs: Long, line: String) {
        val day = dayOf(tsMs)
        if (day != lastDay) prune()
        dir.mkdirs()
        val f = fileOf(day)
        // Past the cap only event lines go on, announced once per day.
        var capLine: String? = null
        if (f.length() >= maxDayBytes && !line.startsWith(EVENT_PREFIX)) {
            if (cappedDay == day) return
            cappedDay = day
            capLine = TripLogFormat.event(tsMs, "tick_cap", zone, listOf("bytes" to f.length()))
        }
        val text = if (capLine != null) capLine + "\n" else if (f.length() == 0L) TripLogFormat.header(day) + "\n" + line + "\n" else line + "\n"
        write(day, f, text.toByteArray(Charsets.UTF_8))
    }

    /** The day's stream stays open (a flush per line); it is closed on a day change and reopened after an IO error. */
    private fun write(day: LocalDate, f: File, bytes: ByteArray) {
        if (outDay != day) closeStream()
        try {
            val s = out ?: FileOutputStream(f, true).also { out = it; outDay = day }
            s.write(bytes)
            s.flush()
        } catch (e: java.io.IOException) {
            closeStream()
            throw e
        }
    }

    /** Closes the open day stream (the next line reopens it). */
    @Synchronized
    fun close() = closeStream()

    private fun closeStream() {
        runCatching { out?.close() }
        out = null
        outDay = null
    }

    /** Deletes the day files older than [keepDays] days before today. */
    @Synchronized
    fun prune() {
        val today = dayOf(clock())
        lastDay = today
        val oldest = today.minusDays((keepDays - 1).toLong())
        dir.listFiles()?.forEach { f ->
            val day = runCatching { LocalDate.parse(f.name.removeSuffix(".log")) }.getOrNull()
            if (f.name.endsWith(".log") && day != null && day.isBefore(oldest)) f.delete()
        }
    }

    /**
     * The newest lines of the kept files, oldest first, from at most [maxBytes] of file data (older data is dropped
     * first; a "(truncated)" line leads when anything was). Nothing larger than that is ever held in memory.
     */
    @Synchronized
    fun readLines(maxBytes: Long = MAX_DUMP_BYTES): List<String> {
        val files = (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".log") }.sortedByDescending { it.name }
        var left = maxBytes
        var truncated = false
        val parts = ArrayList<List<String>>()
        for (f in files) {
            if (left <= 0L) { truncated = true; break }
            val part = runCatching { tail(f, left) }.getOrNull()
            if (part != null) {
                if (part.cut) truncated = true
                left -= part.bytes
                parts += part.lines
            }
        }
        val out = ArrayList<String>()
        if (truncated) out += "(truncated: older log lines omitted)"
        parts.asReversed().forEach { out += it }
        return out
    }

    private class Tail(val lines: List<String>, val bytes: Long, val cut: Boolean)

    private fun tail(f: File, max: Long): Tail {
        val len = f.length()
        if (len <= max) return Tail(f.readLines(), len, false)
        java.io.RandomAccessFile(f, "r").use { r ->
            r.seek(len - max)
            val buf = ByteArray(max.toInt())
            r.readFully(buf)
            val text = String(buf, Charsets.UTF_8)
            val nl = text.indexOf('\n')
            val lines = (if (nl >= 0) text.substring(nl + 1) else "").lines().dropLastWhile { it.isEmpty() }
            return Tail(lines, max, true)
        }
    }

    companion object {
        const val MAX_DAY_BYTES = 4L * 1024 * 1024
        const val MAX_DUMP_BYTES = 8L * 1024 * 1024
        const val EVENT_PREFIX = "# EVENT"
    }
}

/**
 * Decides which ticks are written. With an open session: at most one line per [TICK_MS], and at once when a discrete
 * field changed (anchor, gear, mode, power state, rpm or speed switching between 0 and above 0). Without one, a tick
 * only when a logged value changed or [quietMs] passed since the last written one, so a parked car writes little.
 */
class TickThrottle(private val quietMs: Long = QUIET_MS, private val tickMs: Long = TICK_MS) {
    private var last: TripTick? = null
    private var lastMs = Long.MIN_VALUE

    @Synchronized
    fun allow(tick: TripTick, nowMs: Long, sessionOpen: Boolean): Boolean {
        val prev = last
        val write = if (prev == null || nowMs < lastMs) true
        else if (sessionOpen) nowMs - lastMs >= tickMs || discreteChanged(prev, tick)
        else tick != prev || nowMs - lastMs >= quietMs
        if (write) { last = tick; lastMs = nowMs }
        return write
    }

    private fun discreteChanged(a: TripTick, b: TripTick): Boolean =
        a.anchorMs != b.anchorMs || a.gear != b.gear || a.mode != b.mode || a.powerState != b.powerState ||
            ((a.rpm ?: 0) > 0) != ((b.rpm ?: 0) > 0) || ((a.speed ?: 0) > 0) != ((b.speed ?: 0) > 0)

    companion object {
        const val QUIET_MS = 5 * 60_000L
        const val TICK_MS = 2_000L
    }
}

/**
 * The always-on trip log (fork): lines are queued and written on a background thread, so the tracker's collector never
 * waits for the disk; an IO failure is reported once and then ignored.
 */
object TripLog {
    private const val TAG = "TripLog"
    private const val DIR = "fork_trip_log"
    private const val DIR_DEMO = "fork_trip_log_demo"
    private const val QUEUE = 2_000

    private class Item(val tsMs: Long, val line: String)

    @Volatile private var store: TripLogStore? = null
    @Volatile private var queue: Channel<Item>? = null
    @Volatile private var reported = false
    private val throttle = TickThrottle()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    @Synchronized
    fun init(context: Context, demo: Boolean) {
        if (queue != null) return
        val q = Channel<Item>(QUEUE, BufferOverflow.DROP_OLDEST)
        queue = q
        val dir = File(context.applicationContext.filesDir, if (demo) DIR_DEMO else DIR)
        // The store prunes old files on creation: do that off the main thread too.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val s = runCatching { TripLogStore(dir, System::currentTimeMillis) }.getOrElse { fail(it); queue = null; return@launch }
            store = s
            for (item in q) runCatching { s.append(item.tsMs, item.line) }.onFailure { fail(it) }
        }
    }

    private fun fail(t: Throwable) {
        if (reported) return
        reported = true
        runCatching { Log.w(TAG, "trip log write failed (reported once, later failures are silent): ${t.message}") }
    }

    fun tick(nowMs: Long, tick: TripTick, sessionOpen: Boolean) {
        val q = queue ?: return
        if (!throttle.allow(tick, nowMs, sessionOpen)) return
        q.trySend(Item(nowMs, TripLogFormat.tick(nowMs, tick, zone)))
    }

    fun event(nowMs: Long, name: String, fields: List<Pair<String, Any?>> = emptyList()) {
        val q = queue ?: return
        q.trySend(Item(nowMs, TripLogFormat.event(nowMs, name, zone, fields)))
    }

    fun readLines(): List<String> = store?.readLines().orEmpty()
}
