package com.bydmate.app.fork.cells.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CellsConfig(val profile: CarProfile, val collect: Boolean, val intervalSec: Int, val view: CheckView)

/** Battery-check settings in their own prefs file (demo has another one, same defaults: collection off). */
class CellsSettings(
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val state = MutableStateFlow(read())
    val config: StateFlow<CellsConfig> = state.asStateFlow()

    var lastFull100WallMs: Long
        get() = prefs.getLong(KEY_FULL, 0L)
        set(v) { prefs.edit().putLong(KEY_FULL, v).apply() }

    /** Wall time when collection was last switched off: a session anchor older than this is dropped. */
    var anchorClearedAtMs: Long
        get() = prefs.getLong(KEY_CLEARED, 0L)
        set(v) { prefs.edit().putLong(KEY_CLEARED, v).apply() }

    fun setProfile(p: CarProfile) = write { putString(KEY_PROFILE, p.id) }

    fun setCollect(on: Boolean) = write {
        putBoolean(KEY_COLLECT, on)
        if (!on) putLong(KEY_CLEARED, clock())
    }

    fun setIntervalSec(sec: Int) = write { putInt(KEY_INTERVAL, if (sec in INTERVALS_SEC) sec else DEFAULT_INTERVAL_SEC) }

    fun setView(v: CheckView) = write { putString(KEY_VIEW, v.name) }

    private fun write(edit: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(edit).apply()
        state.value = read()
    }

    private fun read(): CellsConfig {
        val interval = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SEC)
        return CellsConfig(
            profile = CarProfile.byId(prefs.getString(KEY_PROFILE, null)),
            collect = prefs.getBoolean(KEY_COLLECT, false),
            intervalSec = if (interval in INTERVALS_SEC) interval else DEFAULT_INTERVAL_SEC,
            view = CheckView.entries.firstOrNull { it.name == prefs.getString(KEY_VIEW, null) } ?: CheckView.WHAT_TO_DO,
        )
    }

    companion object {
        val INTERVALS_SEC = listOf(10, 30, 60, 300)
        const val DEFAULT_INTERVAL_SEC = 30
        private const val KEY_PROFILE = "car_profile"
        private const val KEY_COLLECT = "collect"
        private const val KEY_INTERVAL = "interval_sec"
        private const val KEY_VIEW = "view"
        private const val KEY_FULL = "last_full100_wall_ms"
        private const val KEY_CLEARED = "anchor_cleared_at_ms"

        fun prefsName(demo: Boolean): String = if (demo) "bydmate_fork_cells_demo" else "bydmate_fork_cells"
    }
}
