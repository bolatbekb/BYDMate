package com.bydmate.app.fork.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.settings.SettingDivider
import com.bydmate.app.ui.settings.SettingSubhead
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/** «Настройки учёта»: tank volume, detector / mismatch thresholds, expense categories. */
@Composable
internal fun LedgerSettingsTab(state: LedgerUiState, vm: LedgerViewModel) {
    if (!state.loaded) return
    NumberSetting(
        title = stringResource(R.string.ledger_settings_tank),
        description = stringResource(R.string.ledger_settings_tank_desc),
        current = state.tankCapacityL?.let(LedgerEditorState::plain).orEmpty(),
        parked = state.parked,
        allowEmpty = true,
    ) { vm.setTankCapacity(it) }
    SettingDivider()
    NumberSetting(
        title = stringResource(R.string.ledger_settings_detect),
        description = stringResource(R.string.ledger_settings_detect_desc),
        current = state.detectThresholdPp.toString(),
        parked = state.parked,
        integer = true,
    ) { v -> v?.let { vm.setThresholds(it.toInt(), state.mismatchThresholdPct) } }
    SettingDivider()
    NumberSetting(
        title = stringResource(R.string.ledger_settings_mismatch),
        description = stringResource(R.string.ledger_settings_mismatch_desc),
        current = LedgerEditorState.plain(state.mismatchThresholdPct),
        parked = state.parked,
    ) { v -> v?.let { vm.setThresholds(state.detectThresholdPp, it) } }

    SettingSubhead(stringResource(R.string.ledger_settings_categories))
    var renaming by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }
    state.categories.forEach { c ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                categoryLabel(c) + if (c.archived) " (${stringResource(R.string.ledger_category_hidden)})" else "",
                color = if (c.archived) TextMuted else TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (c.builtinKey == null) {
                LedgerButton(stringResource(R.string.ledger_category_rename), primary = false, enabled = state.parked) { renaming = c }
                Spacer(Modifier.width(8.dp))
            }
            LedgerButton(
                stringResource(if (c.archived) R.string.ledger_category_show else R.string.ledger_category_hide),
                primary = false, enabled = state.parked,
            ) { vm.saveCategory(c.copy(archived = !c.archived)) }
        }
    }
    var newName by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LedgerField(
            stringResource(R.string.ledger_category_add_hint), newName, { newName = it },
            modifier = Modifier.weight(1f), numeric = false, enabled = state.parked,
        )
        LedgerButton(stringResource(R.string.ledger_category_add), enabled = state.parked && newName.isNotBlank()) {
            vm.saveCategory(ExpenseCategoryEntity(name = newName, builtinKey = null, color = null, sort = 0))
            newName = ""
        }
    }

    SettingSubhead(stringResource(R.string.ledger_import_title))
    Text(stringResource(R.string.ledger_import_desc), color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    var importing by remember { mutableStateOf(false) }
    LedgerButton(stringResource(R.string.ledger_import_choose), primary = false) {
        vm.resetImport()
        importing = true
    }
    if (importing) {
        val importState by vm.importState.collectAsState()
        FuelioImportDialog(importState, state.parked, vm) {
            importing = false
            vm.resetImport()
        }
    }

    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        AppAlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = CardSurface,
            title = { Text(stringResource(R.string.ledger_category_rename), color = TextPrimary, fontSize = 16.sp) },
            text = {
                LedgerField(stringResource(R.string.ledger_field_title), name, { name = it }, numeric = false, enabled = state.parked)
            },
            confirmButton = {
                LedgerButton(stringResource(R.string.ledger_save), enabled = state.parked && name.isNotBlank()) {
                    vm.saveCategory(c.copy(name = name))
                    renaming = null
                }
            },
            dismissButton = {
                LedgerButton(stringResource(R.string.ledger_cancel), primary = false) { renaming = null }
            },
        )
    }
}

/** Title + hint + numeric field + Save; Save is offered only for a valid, changed value. */
@Composable
private fun NumberSetting(
    title: String,
    description: String,
    current: String,
    parked: Boolean,
    allowEmpty: Boolean = false,
    integer: Boolean = false,
    onSave: (Double?) -> Unit,
) {
    var text by remember(current) { mutableStateOf(current) }
    val value = LedgerEditorState.num(text)
    val bad = if (text.isBlank()) !allowEmpty else LedgerEditorState.bad(text) || (integer && value != null && value % 1.0 != 0.0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            Text(description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        LedgerField(
            null, text, { text = it }, modifier = Modifier.width(140.dp),
            error = text.isNotBlank() && bad, enabled = parked,
        )
        LedgerButton(stringResource(R.string.ledger_save), enabled = parked && !bad && text != current) { onSave(value) }
    }
}
