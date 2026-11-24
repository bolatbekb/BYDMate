package com.bydmate.app.fork.alerts.ui

import androidx.lifecycle.ViewModel
import com.bydmate.app.fork.alerts.center.AlertSettings
import com.bydmate.app.fork.alerts.center.AlertsConfig
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.Severity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Backs the «Предупреждения» settings section: the per-source choices and the storage threshold. */
@HiltViewModel
class AlertsSettingsViewModel @Inject constructor(private val settings: AlertSettings) : ViewModel() {
    val config: StateFlow<AlertsConfig> = settings.config

    fun setEnabled(id: AlertSourceId, on: Boolean) = settings.setEnabled(id, on)
    fun setMinSeverity(id: AlertSourceId, severity: Severity) = settings.setMinSeverity(id, severity)
    fun setStorageWarnPercent(percent: Int) = settings.setStorageWarnPercent(percent)
}
