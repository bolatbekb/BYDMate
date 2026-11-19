package com.bydmate.app.fork.hud

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Fork: custom HUD icons from /storage/emulated/0/Download/bydmate_hud_icons/0x<hex>.png.
 *  Priority for the maneuver picture (f8): custom -> built-in BYDMate -> the navigator's own. */
object HudCustomIcons {
    private const val TAG = "HudCustomIcons"
    const val DIR = "/storage/emulated/0/Download/bydmate_hud_icons"
    const val RESCAN_MS = 30_000L
    const val MAX_FILES = 64
    const val MAX_BYTES = 256 * 1024
    const val MAX_SIDE = 512
    enum class Source { CUSTOM, BYDMATE, NAVIGATOR }

    private val used = Array(3) { AtomicInteger() }

    private val defaultScanner by lazy { Scanner(File(DIR)) }
    internal var scannerOverride: Scanner? = null
    private val default get() = scannerOverride ?: defaultScanner

    private const val PREFS = "bydmate_fork_hud"
    private const val KEY_ENABLED = "custom_icons"

    /** Cached switch «Использовать свои иконки для навигатора»; default off. While off nothing is scanned. */
    @Volatile var enabled: Boolean = false
        internal set

    fun init(context: Context) {
        enabled = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        }.getOrDefault(false)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply() }
    }

    /** Custom icon for a Gaode maneuver code, or null. Cheap: IO at most once per 30 s; none when the switch is off. */
    fun iconFor(code: Int): ByteArray? = if (enabled) default.iconFor(code) else null

    fun pick(
        code: Int,
        custom: (Int) -> ByteArray?,
        builtin: (Int) -> ByteArray?,
        navigator: ByteArray?,
    ): Pair<ByteArray?, Source> {
        val c = if (code > 0) custom(code) else null
        if (c != null) return c to Source.CUSTOM.also { used[0].incrementAndGet() }
        val b = if (code > 0) builtin(code) else null
        if (b != null) return b to Source.BYDMATE.also { used[1].incrementAndGet() }
        used[2].incrementAndGet()
        return navigator to Source.NAVIGATOR
    }

    /** The hook for the push loop. */
    fun pickIcon(code: Int, builtin: (Int) -> ByteArray?, navigator: ByteArray?): ByteArray? =
        pick(code, ::iconFor, builtin, navigator).first

    /** Custom, then built-in; for the HUD check frame (not counted). */
    fun customOr(code: Int, builtin: (Int) -> ByteArray?): ByteArray? = iconFor(code) ?: builtin(code)

    fun counters(): List<Int> = used.map { it.get() }
    internal fun resetCounters() = used.forEach { it.set(0) }

    fun lines(): List<String> = runCatching {
        buildList {
            add("--- hud icons (fork) ---")
            add("switch: ${if (enabled) "on" else "off"}")
            if (enabled) addAll(default.lines())
            add("sources used: custom=${used[0].get()} bydmate=${used[1].get()} navigator=${used[2].get()}")
        }
    }.getOrElse { listOf("--- hud icons (fork) ---", "(unavailable: ${it.javaClass.simpleName})") }

    private class Entry(val size: Long, val mtime: Long, val code: Int?, val bytes: ByteArray?, val reason: String?)

    class Scanner internal constructor(
        private val dir: File,
        private val clock: () -> Long = System::currentTimeMillis,
        private val reader: (File) -> ByteArray? = { it.readBytes() },
        private val log: (String) -> Unit = { msg -> runCatching { Log.i(TAG, msg) } },
    ) {
        @Volatile private var icons: Map<Int, ByteArray> = emptyMap()
        @Volatile private var rejects: List<String> = emptyList()
        @Volatile private var lastScan = Long.MIN_VALUE
        @Volatile private var lastScanWall = 0L
        @Volatile private var dirOk = false
        @Volatile private var dirReadable = false
        private var entries: Map<String, Entry> = emptyMap()
        private var lastSummary: String? = null
        private var errorLogged = false

        fun iconFor(code: Int): ByteArray? {
            if (code <= 0) return null
            val now = clock()
            if (lastScan == Long.MIN_VALUE || now - lastScan >= RESCAN_MS) rescan(now)
            return icons[code]
        }

        fun codes(): Set<Int> = icons.keys
        fun rejected(): List<String> = rejects

        @Synchronized private fun rescan(now: Long) {
            if (lastScan != Long.MIN_VALUE && now - lastScan < RESCAN_MS) return
            lastScan = now
            lastScanWall = System.currentTimeMillis()
            runCatching {
                dirOk = dir.isDirectory
                dirReadable = dirOk && dir.canRead()
                val files = if (dirReadable) dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty() else emptyList()
                val old = entries
                val fresh = HashMap<String, Entry>()
                val map = HashMap<Int, ByteArray>()
                val rej = ArrayList<String>()
                for (f in files) {
                    val size = f.length(); val mtime = f.lastModified()
                    val e = old[f.name]?.takeIf { it.size == size && it.mtime == mtime } ?: load(f, size, mtime)
                    fresh[f.name] = e
                    when {
                        e.reason != null -> rej.add("${f.name}: ${e.reason}")
                        map.containsKey(e.code!!) -> rej.add("${f.name}: duplicate")
                        map.size >= MAX_FILES -> rej.add("${f.name}: too_many")
                        else -> map[e.code] = e.bytes!!
                    }
                }
                entries = fresh
                icons = map
                rejects = rej
                val summary = "loaded ${map.size} custom icons from $dir: " +
                    map.keys.sorted().joinToString(",") { "0x" + it.toString(16) } +
                    "; rejected: " + rej.joinToString(", ").ifEmpty { "none" }
                if (summary != lastSummary) { lastSummary = summary; log(summary) }
            }.onFailure {
                icons = emptyMap(); entries = emptyMap()
                if (!errorLogged) { errorLogged = true; log("scan failed: ${it.javaClass.simpleName} ${it.message}") }
            }
        }

        private fun load(f: File, size: Long, mtime: Long): Entry {
            val code = HudIconFiles.parseName(f.name)
            val bytes = if (code != null && size <= MAX_BYTES) reader(f) else null
            val reason = HudIconFiles.reject(f.name, size, bytes)
            return Entry(size, mtime, code.takeIf { reason == null }, bytes.takeIf { reason == null }, reason)
        }

        fun lines(): List<String> {
            if (lastScan == Long.MIN_VALUE) rescan(clock())
            val t = if (lastScanWall == 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastScanWall))
            return listOf(
                "dir: $dir exists=$dirOk readable=$dirReadable",
                "loaded: " + (icons.keys.sorted().joinToString(",") { "0x" + it.toString(16) }.ifEmpty { "none" }),
                "rejected: " + (rejects.joinToString(", ").ifEmpty { "none" }),
                "last scan: $t",
            )
        }
    }
}

/** Pure file validation for the custom HUD icons. */
internal object HudIconFiles {
    const val MAX_BYTES = HudCustomIcons.MAX_BYTES
    const val MAX_SIDE = HudCustomIcons.MAX_SIDE
    private val NAME = Regex("0x([0-9a-f]{1,2})\\.png", RegexOption.IGNORE_CASE)
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun parseName(name: String): Int? {
        val m = NAME.matchEntire(name) ?: return null
        return m.groupValues[1].toInt(16).takeIf { it in 1..255 }
    }

    /** Width and height from the IHDR chunk, or null if not a PNG / too short. */
    fun pngSize(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 24) return null
        for (i in SIGNATURE.indices) if (b[i] != SIGNATURE[i]) return null
        if (String(b, 12, 4, Charsets.ISO_8859_1) != "IHDR") return null
        fun be(o: Int) = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
        return be(16) to be(20)
    }

    /** Reason a file is rejected (name, too_big, not_png, bad_size) or null when valid. */
    fun reject(name: String, size: Long, bytes: ByteArray?): String? {
        if (parseName(name) == null) return "name"
        if (size > MAX_BYTES) return "too_big"
        val wh = bytes?.let { pngSize(it) } ?: return "not_png"
        if (wh.first !in 1..MAX_SIDE || wh.second !in 1..MAX_SIDE) return "bad_size"
        return null
    }
}
