package com.bydmate.app.fork.hud

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/** fork: our own HUD maneuver icons, shipped in assets/navi_fork/0x<hex>.png (38x41, same format as the
 *  author's assets/navi). Switchable; priority when on: navi_fork -> built-in BYDMate -> the navigator's own. */
object HudCustomIcons {
    private const val TAG = "HudCustomIcons"
    private const val ASSET_DIR = "navi_fork"
    private const val PREFS = "bydmate_fork_hud"
    private const val KEY_ENABLED = "custom_icons"

    enum class Source { CUSTOM, BYDMATE, NAVIGATOR }

    /** Where the icons come from; the AssetManager in the app, a counting fake in tests. */
    internal interface AssetSource {
        fun names(): List<String>
        fun read(name: String): ByteArray
    }

    private class Assets(private val am: AssetManager) : AssetSource {
        override fun names() = am.list(ASSET_DIR).orEmpty().toList()
        override fun read(name: String) = am.open("$ASSET_DIR/$name").use { it.readBytes() }
    }

    private val used = Array(3) { AtomicInteger() }
    private val lock = Any()

    @Volatile private var source: AssetSource? = null
    @Volatile private var icons: Map<Int, ByteArray>? = null

    /** Cached switch «Использовать свои иконки для навигатора»; default off. While off no asset is read. */
    @Volatile var enabled: Boolean = false
        internal set

    fun init(context: Context) {
        source = Assets(context.applicationContext.assets)
        enabled = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        }.getOrDefault(false)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply() }
    }

    internal fun resetForTest(s: AssetSource?) {
        synchronized(lock) { source = s; icons = null }
        used.forEach { it.set(0) }
    }

    /** Our icon for a Gaode maneuver code, or null. Loads navi_fork once, on the first call while the switch is on. */
    fun iconFor(code: Int): ByteArray? {
        if (!enabled || code <= 0) return null
        return (icons ?: load())[code]
    }

    private fun load(): Map<Int, ByteArray> = synchronized(lock) {
        icons ?: runCatching {
            val src = source ?: return@runCatching emptyMap<Int, ByteArray>()
            src.names().mapNotNull { name ->
                val code = name.removePrefix("0x").removeSuffix(".png").toIntOrNull(16)
                    ?.takeIf { name.startsWith("0x") && name.endsWith(".png") } ?: return@mapNotNull null
                runCatching { code to src.read(name) }.getOrNull()
            }.toMap()
        }.onFailure { Log.e(TAG, "icon load failed: ${it.message}") }
            .getOrDefault(emptyMap()).also {
                icons = it
                runCatching { Log.i(TAG, "loaded ${it.size} fork maneuver icons") }
            }
    }

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

    /** Ours, then built-in; for the HUD check frame (not counted). */
    fun customOr(code: Int, builtin: (Int) -> ByteArray?): ByteArray? = iconFor(code) ?: builtin(code)

    fun counters(): List<Int> = used.map { it.get() }

    fun lines(): List<String> = runCatching {
        buildList {
            add("--- hud icons (fork) ---")
            add("switch: ${if (enabled) "on" else "off"}")
            add("icons loaded from navi_fork: ${icons?.size ?: (if (enabled) "not yet" else "not loaded")}")
            add("push-loop ticks by source (not maneuvers): custom=${used[0].get()} bydmate=${used[1].get()} navigator=${used[2].get()}")
        }
    }.getOrElse { listOf("--- hud icons (fork) ---", "(unavailable: ${it.javaClass.simpleName})") }
}
