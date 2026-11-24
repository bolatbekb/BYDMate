package com.bydmate.app.fork.alerts.ui

import androidx.lifecycle.ViewModel
import com.bydmate.app.fork.alerts.center.AlertCenter
import com.bydmate.app.fork.alerts.model.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The «Машина» entry and its list. [active] is the center's filtered, sorted alerts; collecting it
 * is what turns the sources on (the center is cold until watched). [open] drives the list overlay.
 * fork-local.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(center: AlertCenter) : ViewModel() {
    val active: StateFlow<List<Alert>> = center.active

    private val openState = MutableStateFlow(false)
    val open: StateFlow<Boolean> = openState.asStateFlow()

    fun openWindow() { openState.value = true }
    fun closeWindow() { openState.value = false }
}
