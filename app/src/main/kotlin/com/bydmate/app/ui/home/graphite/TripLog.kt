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
) {
    companion object {
        fun of(anchorMs: Long?, d: DiParsData?, open: LiveSession?): TripTick {
            val est = open?.estimate()
            return TripTick(
                anchorMs, d?.gear, d?.speed, d?.engineRpm, d?.powerState, d?.mileage, d?.totalElecConsumption,
                d?.fuelTotalL, d?.mileageHevKm, open?.lastMode, est?.km, open?.iceKm, open?.evKm,
                open?.split?.driveMs, open?.split?.parkMs,
            )
        }
    }
}

/** Pure text forms of the trip log. A tick line starts with the local time; an event line is `# EVENT name key=value…`. */
object TripLogFormat {
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
    const val HEADER_COLUMNS = "time a=anchor g=gear v=speed rpm ps=powerState odo kwh fuel hev m=mode km ice ev dr=driveMs pk=parkMs"

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
        append(" ps=").append(any(t.powerState)).append(" odo=").append(num(t.odoKm)).append(" kwh=").append(num(t.kwhCounter))
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
) {
    private var lastDay: LocalDate? = null

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
        val text = if (f.length() == 0L) TripLogFormat.header(day) + "\n" + line + "\n" else line + "\n"
        FileOutputStream(f, true).use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
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

    /** The kept files' lines, oldest day first. */
    @Synchronized
    fun readLines(): List<String> =
        (dir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".log") }.sortedBy { it.name }
            .flatMap { runCatching { it.readLines() }.getOrDefault(emptyList()) }
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
    private const val MAX_MERGES = 50

    private class Item(val tsMs: Long, val line: String)

    @Volatile private var store: TripLogStore? = null
    private var queue: Channel<Item>? = null
    private var reported = false
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val merges = LinkedHashMap<Long, String>()

    @Synchronized
    fun init(context: Context, demo: Boolean) {
        if (store != null) return
        val s = runCatching {
            TripLogStore(File(context.applicationContext.filesDir, if (demo) DIR_DEMO else DIR), System::currentTimeMillis)
        }.getOrElse { fail(it); return }
        val q = Channel<Item>(QUEUE, BufferOverflow.DROP_OLDEST)
        store = s
        queue = q
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (item in q) runCatching { s.append(item.tsMs, item.line) }.onFailure { fail(it) }
        }
    }

    private fun fail(t: Throwable) {
        if (reported) return
        reported = true
        runCatching { Log.w(TAG, "trip log disabled by an IO error: ${t.message}") }
    }

    fun tick(nowMs: Long, tick: TripTick) {
        val q = queue ?: return
        q.trySend(Item(nowMs, TripLogFormat.tick(nowMs, tick, zone)))
    }

    fun event(nowMs: Long, name: String, fields: List<Pair<String, Any?>> = emptyList()) {
        val q = queue ?: return
        q.trySend(Item(nowMs, TripLogFormat.event(nowMs, name, zone, fields)))
    }

    /** Logs the chart merge decisions that changed since the last call (one event per changed row). */
    fun merge(nowMs: Long, decisions: List<MergeDecision>) {
        decisions.forEach { d ->
            val line = d.fields()
            val sig = line.joinToString(" ") { "${it.first}=${it.second}" }
            val changed = synchronized(merges) {
                if (merges[d.rowStartMs] == sig) false else {
                    merges.remove(d.rowStartMs)
                    merges[d.rowStartMs] = sig
                    while (merges.size > MAX_MERGES) merges.remove(merges.keys.first())
                    true
                }
            }
            if (changed) event(nowMs, "merge", line)
        }
    }

    /** The latest merge decision of each recorded row that started on the day of [nowMs]. */
    fun mergeLines(nowMs: Long): List<String> {
        val z = zone
        val today = Instant.ofEpochMilli(nowMs).atZone(z).toLocalDate()
        return synchronized(merges) {
            merges.filterKeys { Instant.ofEpochMilli(it).atZone(z).toLocalDate() == today }.values.toList()
        }
    }

    fun readLines(): List<String> = store?.readLines().orEmpty()
}
