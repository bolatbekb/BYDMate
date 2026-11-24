package com.bydmate.app.fork.alerts.source

import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.Severity

/**
 * One origin of problem alerts. A source carries its id (and so its block) and emits the list of
 * its currently active alerts. A source applies its own hysteresis, so the flow does not flap, and
 * it stays silent (empty list) whenever its reading is unavailable — it never invents a problem.
 */
interface AlertSource {
    val id: AlertSourceId
    fun alerts(): kotlinx.coroutines.flow.Flow<List<Alert>>
}

/** A fired item: its severity now and the wall time it first fired, held across recomputations. */
internal data class ItemState(val severity: Severity, val firstSeenMs: Long)

/**
 * The next state of one item given its previous state, the latest reading and the thresholds. A
 * null reading clears the item (we cannot assert a problem we cannot read). A first fire stamps
 * [ItemState.firstSeenMs]; later fires keep it so the "appeared at" time does not jump.
 */
internal fun advance(prev: ItemState?, value: Double?, spec: ThresholdSpec, now: () -> Long): ItemState? {
    if (value == null) return null
    val severity = Hysteresis.evaluate(spec, value, prev != null) ?: return null
    return ItemState(severity, prev?.firstSeenMs ?: now())
}
