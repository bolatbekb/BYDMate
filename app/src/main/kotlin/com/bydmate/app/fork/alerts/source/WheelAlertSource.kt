package com.bydmate.app.fork.alerts.source

import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.ScalarUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * A source over the four wheels (tyre pressure, tyre temperature). [input] gives the four readings
 * in [WHEELS] order, each value or null; every wheel keeps its own hysteresis and first-seen time,
 * so one low tyre fires on its own. The wheel label (FL..RR) rides along untranslated, as elsewhere.
 */
class WheelAlertSource(
    override val id: AlertSourceId,
    private val spec: ThresholdSpec,
    private val unit: ScalarUnit,
    private val input: Flow<List<Double?>>,
    private val now: () -> Long,
) : AlertSource {
    override fun alerts(): Flow<List<Alert>> = input
        .scan(Step(emptyMap(), emptyList())) { step, values -> next(step, values) }
        .drop(1)
        .map { it.alerts }
        .distinctUntilChanged()

    private data class Step(val states: Map<Int, ItemState>, val alerts: List<Alert>)

    private fun next(prev: Step, values: List<Double?>): Step {
        val states = mutableMapOf<Int, ItemState>()
        val alerts = mutableListOf<Alert>()
        WHEELS.indices.forEach { i ->
            val value = values.getOrNull(i)
            val state = advance(prev.states[i], value, spec, now)
            if (state != null && value != null) {
                states[i] = state
                alerts += Alert(
                    id, itemKey = WHEELS[i], state.severity, state.firstSeenMs,
                    AlertValue.Scalar(value, unit, label = WHEELS[i]),
                )
            }
        }
        return Step(states, alerts)
    }

    companion object {
        /** Wheel order of the four readings, matching the tyre window (FL, FR, RL, RR). */
        val WHEELS = listOf("FL", "FR", "RL", "RR")
    }
}
