package com.bydmate.app.fork.alerts.source

import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A source that is registered (so its block and its settings row appear) but emits nothing yet —
 * its signal is not cleanly readable as a stage-1 flow, and a made-up reading would be a false
 * alert. It is wired up in a later pass, when the signal is exposed as a singleton flow.
 *
 * TODO(alerts, stage 2):
 *  - CELL_IMBALANCE: take the battery-check verdict («в сервис» / «стоит последить») once it is
 *    exposed as a singleton flow (today it lives inside BatteryCheckViewModel). No raw-delta
 *    threshold here on purpose — the whole point of that verdict is that a plain delta flaps with
 *    SOC and temperature.
 *  - COOLANT_TEMP: poll [com.bydmate.app.fork.cells.coolant.CoolantReader] on a ticker; the high
 *    threshold (~105 °C) is well known, the polling plumbing is the deferred part.
 *  - MOTOR_TEMP: poll [com.bydmate.app.fork.cells.motors.MotorTempsReader]; needs car-verified
 *    high thresholds (the reader clamps to ≤150 °C; normal is <50 °C under load).
 */
class StubAlertSource(override val id: AlertSourceId) : AlertSource {
    override fun alerts(): Flow<List<Alert>> = flowOf(emptyList())
}
