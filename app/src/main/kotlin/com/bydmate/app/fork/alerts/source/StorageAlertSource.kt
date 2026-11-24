package com.bydmate.app.fork.alerts.source

import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlin.math.roundToInt

/**
 * Free space on every mounted volume (the owner's main source). [input] gives the volumes on each
 * poll; each volume keeps its own hysteresis and first-seen time. The warn threshold is read fresh
 * each poll through [specOf], so the settings slider changes it live; CRITICAL and the clear margin
 * are fixed. The alert carries the free percentage and the GB both ways, the UI writes the line.
 */
class StorageAlertSource(
    private val input: Flow<List<VolumeInfo>>,
    private val specOf: () -> ThresholdSpec,
    private val now: () -> Long,
) : AlertSource {
    override val id: AlertSourceId = AlertSourceId.STORAGE_FREE

    override fun alerts(): Flow<List<Alert>> = input
        .scan(Step(emptyMap(), emptyList())) { step, volumes -> next(step, volumes) }
        .drop(1)
        .map { it.alerts }
        .distinctUntilChanged()

    private data class Step(val states: Map<String, ItemState>, val alerts: List<Alert>)

    private fun next(prev: Step, volumes: List<VolumeInfo>): Step {
        val spec = specOf()
        val states = mutableMapOf<String, ItemState>()
        val alerts = mutableListOf<Alert>()
        volumes.forEach { vol ->
            val state = advance(prev.states[vol.id], vol.freePercent, spec, now)
            if (state != null) {
                states[vol.id] = state
                alerts += Alert(
                    id, itemKey = vol.id, state.severity, state.firstSeenMs,
                    AlertValue.Storage(
                        label = vol.label,
                        percent = vol.freePercent.roundToInt(),
                        freeGb = gb(vol.freeBytes),
                        totalGb = gb(vol.totalBytes),
                    ),
                )
            }
        }
        return Step(states, alerts)
    }

    private companion object {
        const val BYTES_PER_GB = 1_000_000_000.0
        fun gb(bytes: Long): Int = (bytes / BYTES_PER_GB).roundToInt()
    }
}
