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
 * A source over one scalar reading (12 V, a temperature, insulation). [input] gives the current
 * value or null when it is unavailable; the source keeps one item's hysteresis and emits either no
 * alert or one. The value is carried structured ([AlertValue.Scalar]) so the UI formats the unit.
 */
class ScalarAlertSource(
    override val id: AlertSourceId,
    private val spec: ThresholdSpec,
    private val unit: ScalarUnit,
    private val input: Flow<Double?>,
    private val now: () -> Long,
) : AlertSource {
    override fun alerts(): Flow<List<Alert>> = input
        .scan(Step(null, null)) { step, value -> next(step, value) }
        .drop(1)
        .map { it.alert?.let(::listOf) ?: emptyList() }
        .distinctUntilChanged()

    private data class Step(val state: ItemState?, val alert: Alert?)

    private fun next(prev: Step, value: Double?): Step {
        val state = advance(prev.state, value, spec, now)
        val alert = if (state != null && value != null) {
            Alert(id, itemKey = "", state.severity, state.firstSeenMs, AlertValue.Scalar(value, unit))
        } else {
            null
        }
        return Step(state, alert)
    }
}
