package com.bydmate.app.fork.alerts.demo

import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.ScalarUnit
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.source.AlertSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The demo seed: a fixed set of alerts across several blocks and all three levels, so the list and
 * the settings can be looked at on the emulator. It stands in for every real source while demo mode
 * is on, so the picture is the same every launch. Each alert still carries a real source id, so the
 * settings filter works on it exactly as in the car. fork-local.
 */
class DemoAlertSource(private val now: () -> Long = System::currentTimeMillis) : AlertSource {
    // A demo source needs an id; it reuses the storage id, the filter keys off each alert's own id.
    override val id: AlertSourceId = AlertSourceId.STORAGE_FREE

    override fun alerts(): Flow<List<Alert>> = flowOf(seed())

    private fun seed(): List<Alert> {
        val t = now()
        return listOf(
            Alert(
                AlertSourceId.STORAGE_FREE, "dashcam", Severity.CRITICAL, t - mins(3),
                AlertValue.Storage(label = "Карта регистратора", percent = 2, freeGb = 1, totalGb = 64),
            ),
            Alert(
                AlertSourceId.STORAGE_FREE, "internal", Severity.WARNING, t - mins(41),
                AlertValue.Storage(label = "Внутренняя память", percent = 8, freeGb = 10, totalGb = 128),
            ),
            Alert(
                AlertSourceId.VOLT_12V, "", Severity.WARNING, t - mins(12),
                AlertValue.Scalar(11.6, ScalarUnit.VOLT),
            ),
            Alert(
                AlertSourceId.TYRE_PRESSURE, "RL", Severity.WARNING, t - mins(2),
                AlertValue.Scalar(175.0, ScalarUnit.KPA, label = "RL"),
            ),
            Alert(
                AlertSourceId.BATTERY_TEMP, "", Severity.INFO, t - mins(55),
                AlertValue.Scalar(49.0, ScalarUnit.CELSIUS),
            ),
            Alert(
                AlertSourceId.INSULATION, "", Severity.INFO, t - mins(130),
                AlertValue.Scalar(450.0, ScalarUnit.KOHM),
            ),
        )
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        fun mins(n: Int): Long = n * MINUTE_MS
    }
}
