package com.bydmate.app.fork.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.Prefill
import com.bydmate.app.fork.ledger.domain.PrefillSource
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * New refuel, edit of a stored one, or confirmation of a draft ([initial].base.status == "draft").
 * Only a new entry is prefilled (again on every date change); a stored entry keeps its odometer
 * and car counter.
 */
@Composable
internal fun RefuelEditorDialog(
    initial: LedgerEditorState,
    currency: String,
    parked: Boolean,
    prefill: suspend (Long) -> Prefill,
    onSave: (RefuelEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val fmt = rememberLedgerFmt()
    var form by remember { mutableStateOf(initial) }
    var source by remember { mutableStateOf(PrefillSource.NONE) }
    if (initial.base == null) {
        LaunchedEffect(form.dateMs) {
            val p = prefill(form.dateMs)
            source = p.source
            form = form.applyPrefill(p)
        }
    }
    val errors = form.errors()
    val result = form.toRefuel()
    val title = when {
        initial.base == null -> R.string.ledger_refuel_new
        initial.base.status == "draft" -> R.string.ledger_refuel_confirm_title
        else -> R.string.ledger_refuel_edit
    }
    val odoHint = if (form.odometerEditedByUser || initial.base != null) null else when (source) {
        PrefillSource.LIVE -> stringResource(R.string.ledger_prefill_live)
        PrefillSource.DRAFT -> stringResource(R.string.ledger_prefill_draft)
        PrefillSource.SNAPSHOT -> stringResource(R.string.ledger_prefill_snapshot)
        PrefillSource.NONE -> null
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.8f),
        title = { Text(stringResource(title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerDateTimeRow(form.dateMs, fmt, parked) { form = form.copy(dateMs = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerField(
                        stringResource(R.string.ledger_field_odometer), form.odometer,
                        { form = form.onOdometerTyped(it) },
                        modifier = Modifier.weight(1f),
                        error = RefuelField.ODOMETER in errors,
                        supporting = odoHint,
                        enabled = parked,
                    )
                    LedgerField(
                        stringResource(R.string.ledger_field_liters), form.liters,
                        { form = form.copy(liters = it) },
                        modifier = Modifier.weight(1f), error = RefuelField.LITERS in errors,
                        supporting = form.sensorLiters?.takeIf { form.liters.isBlank() }
                            ?.let { stringResource(R.string.ledger_sensor_liters, fmt.two(it)) },
                        enabled = parked,
                    )
                    LedgerField(
                        stringResource(R.string.ledger_field_total, currency), form.total,
                        { form = form.copy(total = it) },
                        modifier = Modifier.weight(1f), error = RefuelField.TOTAL in errors, enabled = parked,
                    )
                    LedgerField(
                        stringResource(R.string.ledger_field_price), form.price,
                        { form = form.copy(price = it) },
                        modifier = Modifier.weight(1f), error = RefuelField.PRICE in errors, enabled = parked,
                    )
                }
                if (result != null) {
                    Text(
                        stringResource(
                            R.string.ledger_computed,
                            fmt.two(result.liters), fmt.two(result.pricePerLiter ?: 0.0),
                            fmt.money(result.totalCost ?: 0.0), currency,
                        ),
                        color = TextSecondary, fontSize = 13.sp,
                    )
                } else if (RefuelField.MISMATCH in errors) {
                    Text(stringResource(R.string.ledger_error_mismatch), color = SocRed, fontSize = 13.sp)
                } else if (errors.isEmpty()) {
                    Text(stringResource(R.string.ledger_hint_two_of_three), color = TextMuted, fontSize = 13.sp)
                }
                Row {
                    LedgerCheck(stringResource(R.string.ledger_full_tank), form.fullTank, parked) { form = form.copy(fullTank = it) }
                    LedgerCheck(stringResource(R.string.ledger_missed_before), form.missedBefore, parked) {
                        form = form.copy(missedBefore = it)
                    }
                }
                LedgerField(
                    stringResource(R.string.ledger_field_note), form.note, { form = form.copy(note = it) },
                    modifier = Modifier.fillMaxWidth(), numeric = false, enabled = parked,
                )
            }
        },
        confirmButton = {
            LedgerButton(stringResource(R.string.ledger_save), enabled = parked && result != null) {
                result?.let(onSave)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) LedgerButton(stringResource(R.string.ledger_delete), primary = false, enabled = parked, onClick = onDelete)
                LedgerButton(stringResource(R.string.ledger_cancel), primary = false, onClick = onDismiss)
            }
        },
    )
}
