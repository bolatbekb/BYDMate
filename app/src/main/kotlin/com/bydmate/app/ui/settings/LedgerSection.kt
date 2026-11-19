package com.bydmate.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.ui.LedgerTabs
import com.bydmate.app.fork.ledger.ui.LedgerViewModel

/** Rail icon; lives here so SettingsScreen.kt needs no extra import. */
internal val LedgerSectionIcon: ImageVector get() = Icons.AutoMirrored.Outlined.ReceiptLong

/** Settings › Expenses: refuels, other expenses and the monthly summary. fork-local. */
@Composable
internal fun LedgerSection(vm: LedgerViewModel = hiltViewModel()) {
    SectionHeader(text = stringResource(R.string.ledger_section_title))
    LedgerTabs(vm)
}
