package com.bydmate.app.fork.alerts.center

import android.content.SharedPreferences
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-source choice: may this source send, and from which level up. */
data class SourcePref(val enabled: Boolean, val minSeverity: Severity)

/** The whole «Предупреждения» settings state: a choice per source plus the storage warn threshold. */
data class AlertsConfig(
    val sources: Map<AlertSourceId, SourcePref>,
    val storageWarnPercent: Int,
)

/**
 * The «кто может слать» settings in the fork's own prefs file (the demo has another one, same
 * defaults). Defaults: every source on, minimum level «к сведению» (INFO), storage warn at 10 %.
 * Keys are the source names, so adding a source needs no migration. fork-local.
 */
class AlertSettings(private val prefs: SharedPreferences) {
    private val state = MutableStateFlow(read())
    val config: StateFlow<AlertsConfig> = state.asStateFlow()

    fun setEnabled(id: AlertSourceId, on: Boolean) = write { putBoolean(enabledKey(id), on) }

    fun setMinSeverity(id: AlertSourceId, severity: Severity) = write { putString(minKey(id), severity.name) }

    fun setStorageWarnPercent(percent: Int) =
        write { putInt(KEY_STORAGE_WARN, percent.coerceIn(STORAGE_WARN_MIN, STORAGE_WARN_MAX)) }

    /** The settings gate the center applies: the source is on and the level is at least its minimum. */
    fun passes(alert: Alert): Boolean {
        val pref = state.value.sources[alert.sourceId] ?: return true
        return pref.enabled && alert.severity.ordinal >= pref.minSeverity.ordinal
    }

    private fun write(edit: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(edit).apply()
        state.value = read()
    }

    private fun read(): AlertsConfig = AlertsConfig(
        sources = AlertSourceId.entries.associateWith { id ->
            SourcePref(
                enabled = prefs.getBoolean(enabledKey(id), true),
                minSeverity = prefs.getString(minKey(id), null)
                    ?.let { name -> Severity.entries.firstOrNull { it.name == name } }
                    ?: Severity.INFO,
            )
        },
        storageWarnPercent = prefs.getInt(KEY_STORAGE_WARN, STORAGE_WARN_DEFAULT)
            .coerceIn(STORAGE_WARN_MIN, STORAGE_WARN_MAX),
    )

    companion object {
        const val STORAGE_WARN_DEFAULT = 10
        const val STORAGE_WARN_MIN = 5
        const val STORAGE_WARN_MAX = 30
        private const val KEY_STORAGE_WARN = "storage_warn_percent"

        private fun enabledKey(id: AlertSourceId) = "src_${id.name}_enabled"
        private fun minKey(id: AlertSourceId) = "src_${id.name}_min"

        fun prefsName(demo: Boolean): String = if (demo) "bydmate_fork_alerts_demo" else "bydmate_fork_alerts"
    }
}
