package com.bydmate.app.fork.ledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pick a CSV from Downloads → counts preview → import (parked only). */
@Composable
internal fun FuelioImportDialog(
    importState: FuelioImportState,
    parked: Boolean,
    vm: LedgerViewModel,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = { Text(stringResource(R.string.ledger_import_title), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                when (val s = importState) {
                    FuelioImportState.Idle -> FileList(vm)
                    is FuelioImportState.Preview -> {
                        val p = s.preview
                        Text(
                            stringResource(
                                R.string.ledger_import_counts,
                                p.newRefuels, p.newCosts, p.duplicateRefuels + p.duplicateCosts,
                            ),
                            color = TextPrimary, fontSize = 14.sp,
                        )
                        if (p.newCategories.isNotEmpty()) {
                            Text(
                                stringResource(R.string.ledger_import_newcats, p.newCategories.joinToString(", ")),
                                color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (p.skippedRows > 0) {
                            Text(
                                stringResource(R.string.ledger_import_skipped, p.skippedRows),
                                color = AccentOrange, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (!parked) {
                            Text(
                                stringResource(R.string.ledger_import_parked),
                                color = AccentOrange, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    is FuelioImportState.Done -> Text(
                        stringResource(R.string.ledger_import_done, s.result.addedRefuels, s.result.addedCosts),
                        color = TextPrimary, fontSize = 14.sp,
                    )
                    is FuelioImportState.Error -> Text(s.message, color = AccentOrange, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            when (importState) {
                is FuelioImportState.Preview ->
                    LedgerButton(stringResource(R.string.ledger_import_run), enabled = parked) { vm.applyFuelio() }
                is FuelioImportState.Done, is FuelioImportState.Error ->
                    LedgerButton(stringResource(R.string.ledger_import_close), onClick = onDismiss)
                FuelioImportState.Idle -> {}
            }
        },
        dismissButton = {
            if (importState is FuelioImportState.Preview || importState is FuelioImportState.Idle) {
                LedgerButton(stringResource(R.string.ledger_cancel), primary = false, onClick = onDismiss)
            }
        },
    )
}

@Composable
private fun FileList(vm: LedgerViewModel) {
    val files by produceState<List<File>?>(null) {
        value = withContext(Dispatchers.IO) { vm.listCsvFiles() }
    }
    val list = files ?: return
    if (list.isEmpty()) {
        Text(stringResource(R.string.ledger_import_none), color = TextSecondary, fontSize = 14.sp)
        return
    }
    Text(stringResource(R.string.ledger_import_pick), color = TextSecondary, fontSize = 13.sp)
    list.forEach { f ->
        Text(
            f.name, color = TextPrimary, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable { vm.previewFuelio(f) }.padding(vertical = 10.dp),
        )
    }
}
