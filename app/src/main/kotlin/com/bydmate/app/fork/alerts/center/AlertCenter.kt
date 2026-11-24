package com.bydmate.app.fork.alerts.center

import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertEvent
import com.bydmate.app.fork.alerts.source.AlertSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * The one place alerts come together. It merges every source, applies the settings filter
 * (on/off + minimum level per source), sorts for the list and keeps a short in-memory history of
 * the events it saw. No persistent store this stage.
 *
 * [active] is cold until the list is open ([SharingStarted.WhileSubscribed]): with nothing watching,
 * the sources and their polling do not run — important on the head unit (low CPU, the owner's rule).
 * The history therefore collects only while the list is open; a service that watches all the time is
 * the next stage (the status-bar badge).
 */
class AlertCenter(
    sources: Set<AlertSource>,
    private val settings: AlertSettings,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val historyLimit: Int = HISTORY_LIMIT,
) {
    private val merged: Flow<List<Alert>> =
        if (sources.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(sources.map { it.alerts() }) { lists -> lists.toList().flatten() }
        }

    private val historyState = MutableStateFlow<List<AlertEvent>>(emptyList())

    /** The latest events first; newest active alerts that were not active before are appended. */
    val history: StateFlow<List<AlertEvent>> = historyState.asStateFlow()

    private var lastActiveKeys: Set<String> = emptySet()

    /** The active alerts the list shows: filtered by settings, sorted most-important first. */
    val active: StateFlow<List<Alert>> = combine(merged, settings.config) { alerts, _ -> filterAndSort(alerts) }
        .onEach { recordHistory(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun filterAndSort(alerts: List<Alert>): List<Alert> = alerts
        .filter(settings::passes)
        .sortedWith(
            compareByDescending<Alert> { it.severity.ordinal }
                .thenBy { it.block.ordinal }
                .thenBy { it.firstSeenMs }
                .thenBy { it.key },
        )

    private fun recordHistory(active: List<Alert>) {
        val keys = active.map { it.key }.toSet()
        val fresh = active.filter { it.key !in lastActiveKeys }
        lastActiveKeys = keys
        if (fresh.isEmpty()) return
        val at = now()
        val events = fresh.map { AlertEvent(it.sourceId, it.itemKey, it.severity, at) }
        historyState.value = (events + historyState.value).take(historyLimit)
    }

    companion object {
        private const val HISTORY_LIMIT = 50
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
