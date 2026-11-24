package com.bydmate.app.fork.alerts.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bydmate.app.R
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertBlock
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.ScalarUnit
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.SocRed
import java.util.Locale

/** The UI side of the model: every user-visible string is resolved here, in the active locale. */

@Composable
fun blockLabel(block: AlertBlock): String = stringResource(
    when (block) {
        AlertBlock.STORAGE -> R.string.fork_alerts_block_storage
        AlertBlock.BATTERY -> R.string.fork_alerts_block_battery
        AlertBlock.DRIVE_COOLING -> R.string.fork_alerts_block_drive
        AlertBlock.TYRES -> R.string.fork_alerts_block_tyres
        AlertBlock.ELECTRICAL -> R.string.fork_alerts_block_electrical
        AlertBlock.DIAGNOSTICS -> R.string.fork_alerts_block_diagnostics
    },
)

@Composable
fun sourceTitle(id: AlertSourceId): String = stringResource(
    when (id) {
        AlertSourceId.STORAGE_FREE -> R.string.fork_alerts_src_storage
        AlertSourceId.BATTERY_TEMP -> R.string.fork_alerts_src_battery_temp
        AlertSourceId.CELL_IMBALANCE -> R.string.fork_alerts_src_cell_imbalance
        AlertSourceId.INSULATION -> R.string.fork_alerts_src_insulation
        AlertSourceId.COOLANT_TEMP -> R.string.fork_alerts_src_coolant
        AlertSourceId.MOTOR_TEMP -> R.string.fork_alerts_src_motor
        AlertSourceId.TYRE_PRESSURE -> R.string.fork_alerts_src_tyre_pressure
        AlertSourceId.TYRE_TEMP -> R.string.fork_alerts_src_tyre_temp
        AlertSourceId.VOLT_12V -> R.string.fork_alerts_src_volt12
    },
)

@Composable
fun severityLabel(severity: Severity): String = stringResource(
    when (severity) {
        Severity.CRITICAL -> R.string.fork_alerts_sev_critical
        Severity.WARNING -> R.string.fork_alerts_sev_warning
        Severity.INFO -> R.string.fork_alerts_sev_info
    },
)

/** Colour of a level: critical red, warning orange, info blue; all follow the active skin. */
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> SocRed
    Severity.WARNING -> AccentOrange
    Severity.INFO -> AccentBlue
}

/** The value line the list shows: «остаток 8 % (10 ГБ из 128 ГБ)», «11,6 В», «RL: 175 кПа». */
@Composable
fun alertValueText(alert: Alert): String = when (val v = alert.value) {
    is AlertValue.Storage -> stringResource(R.string.fork_alerts_value_storage, v.percent, v.freeGb, v.totalGb)
    is AlertValue.Scalar -> {
        val unit = scalarText(v.unit, v.amount)
        if (v.label != null) stringResource(R.string.fork_alerts_value_wheel, v.label, unit) else unit
    }
    is AlertValue.Plain -> v.text
}

/** The volume label is shown on its own line for a storage alert; null for the rest. */
fun storageLabel(alert: Alert): String? = (alert.value as? AlertValue.Storage)?.label

@Composable
private fun scalarText(unit: ScalarUnit, amount: Double): String = when (unit) {
    ScalarUnit.VOLT -> stringResource(R.string.fork_alerts_value_volt, oneDecimal(amount))
    ScalarUnit.CELSIUS -> stringResource(R.string.fork_alerts_value_celsius, amount.toInt())
    ScalarUnit.KPA -> stringResource(R.string.fork_alerts_value_kpa, amount.toInt())
    ScalarUnit.KOHM -> stringResource(R.string.fork_alerts_value_kohm, amount.toInt())
}

private fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

/** How long ago the alert appeared, in plain words. */
@Composable
fun whenText(firstSeenMs: Long, nowMs: Long): String {
    val minutes = ((nowMs - firstSeenMs).coerceAtLeast(0L) / MINUTE_MS).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.fork_alerts_when_now)
        minutes < MINUTES_PER_HOUR -> stringResource(R.string.fork_alerts_when_min, minutes)
        minutes < MINUTES_PER_DAY -> stringResource(R.string.fork_alerts_when_hour, minutes / MINUTES_PER_HOUR)
        else -> stringResource(R.string.fork_alerts_when_day, minutes / MINUTES_PER_DAY)
    }
}

private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 60 * 24
