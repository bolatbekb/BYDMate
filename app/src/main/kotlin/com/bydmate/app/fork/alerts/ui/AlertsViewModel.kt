package com.bydmate.app.fork.alerts.ui

import androidx.lifecycle.ViewModel
import com.bydmate.app.fork.alerts.center.AlertCenter
import com.bydmate.app.fork.alerts.model.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The active alerts shown in Settings › «Предупреждения». [active] is the center's filtered, sorted
 * alerts; collecting it is what turns the sources on (the center is cold until watched). fork-local.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(center: AlertCenter) : ViewModel() {
    val active: StateFlow<List<Alert>> = center.active
}
