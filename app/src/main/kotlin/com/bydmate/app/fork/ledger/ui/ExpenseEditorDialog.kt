package com.bydmate.app.fork.ledger.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.domain.Prefill
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.settings.UnitChip
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.round

/** New or stored expense. A new one gets the odometer prefilled until the user types one. */
@Composable
internal fun ExpenseEditorDialog(
    initial: ExpenseEntity?,
    nowMs: Long,
    categories: List<ExpenseCategoryEntity>,
    currency: String,
    parked: Boolean,
    prefill: suspend (Long) -> Prefill,
    onSave: (ExpenseEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val fmt = rememberLedgerFmt()
    var dateMs by remember { mutableLongStateOf(initial?.date ?: nowMs) }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: categories.firstOrNull { !it.archived }?.id) }
    var amount by remember { mutableStateOf(initial?.amount?.let(LedgerEditorState::plain).orEmpty()) }
    var odometer by remember { mutableStateOf(initial?.odometerKm?.let(LedgerEditorState::plain).orEmpty()) }
    var odometerEdited by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    if (initial == null) {
        LaunchedEffect(dateMs) {
            val p = prefill(dateMs)
            if (!odometerEdited) odometer = p.odometerKm?.let(LedgerEditorState::plain).orEmpty()
        }
    }
    // the stored category stays selectable even if it was hidden later
    val choices = categories.filter { !it.archived || it.id == initial?.categoryId }
    val amountBad = amount.isBlank() || LedgerEditorState.bad(amount)
    val odoBad = LedgerEditorState.bad(odometer)
    // categories may arrive after the dialog opened: fall back to the first visible one
    val cat = categoryId ?: choices.firstOrNull { !it.archived }?.id
    val result = if (amountBad || odoBad || cat == null) null else {
        val base = initial ?: ExpenseEntity(
            date = dateMs, odometerKm = null, categoryId = cat, amount = 0.0,
            title = null, note = null, source = "manual", importKey = null,
        )
        base.copy(
            date = dateMs,
            odometerKm = LedgerEditorState.num(odometer),
            categoryId = cat,
            amount = round(LedgerEditorState.num(amount)!! * 100.0) / 100.0,
            title = title.trim().ifEmpty { null },
            note = note.trim().ifEmpty { null },
        )
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.8f),
        title = {
            Text(
                stringResource(if (initial == null) R.string.ledger_expense_new else R.string.ledger_expense_edit),
                color = TextPrimary, fontSize = 16.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerDateTimeRow(dateMs, fmt, parked) { dateMs = it }
                Text(stringResource(R.string.ledger_field_category), color = TextSecondary, fontSize = 13.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choices.forEach { c ->
                        UnitChip(categoryLabel(c), selected = c.id == cat) { if (parked) categoryId = c.id }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerField(
                        stringResource(R.string.ledger_field_amount, currency), amount, { amount = it },
                        modifier = Modifier.weight(1f), error = amount.isNotBlank() && amountBad, enabled = parked,
                    )
                    LedgerField(
                        stringResource(R.string.ledger_field_odometer), odometer,
                        { odometer = it; odometerEdited = true },
                        modifier = Modifier.weight(1f), error = odoBad, enabled = parked,
                    )
                    LedgerField(
                        stringResource(R.string.ledger_field_title), title, { title = it },
                        modifier = Modifier.weight(1.5f), numeric = false, enabled = parked,
                    )
                }
                LedgerField(
                    stringResource(R.string.ledger_field_note), note, { note = it },
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
