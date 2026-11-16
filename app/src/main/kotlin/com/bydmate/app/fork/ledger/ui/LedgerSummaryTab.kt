package com.bydmate.app.fork.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.domain.MonthTotals
import com.bydmate.app.ui.settings.SettingDivider
import com.bydmate.app.ui.settings.SettingHint
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import java.time.YearMonth

// ─── Summary ────────────────────────────────────────────────────────────────

@Composable
internal fun SummaryTab(state: LedgerUiState, fmt: LedgerFmt) {
    if (state.loaded && state.months.isEmpty()) {
        SettingHint(stringResource(R.string.ledger_summary_empty))
        return
    }
    SettingHint(stringResource(R.string.ledger_amounts_in, state.currencySymbol))
    TableRow(header = true) {
        Cell(stringResource(R.string.ledger_col_month), 1.6f, start = true)
        Cell(stringResource(R.string.ledger_col_electricity))
        Cell(stringResource(R.string.ledger_col_fuel))
        Cell(stringResource(R.string.ledger_col_other))
        Cell(stringResource(R.string.ledger_col_total))
        Cell(stringResource(R.string.ledger_col_km))
        Cell(stringResource(R.string.ledger_col_per_km))
    }
    SettingDivider()
    state.months.forEach { r ->
        SummaryLine(fmt.month(r.month), MonthTotals(r.electricity, r.fuel, r.other, r.byCategory, r.km, r.fuelCalculated), fmt)
    }
    SettingDivider()
    state.yearTotals?.let { SummaryLine(stringResource(R.string.ledger_row_year, YearMonth.now().year), it, fmt, bold = true) }
    state.totals?.let { SummaryLine(stringResource(R.string.ledger_row_all), it, fmt, bold = true) }
    if (state.months.any { it.fuelCalculated != null }) SettingHint(stringResource(R.string.ledger_fuel_calc_hint))
}

@Composable
private fun SummaryLine(label: String, t: MonthTotals, fmt: LedgerFmt, bold: Boolean = false) {
    TableRow(bold = bold) {
        Cell(label, 1.6f, start = true)
        Cell(fmt.money(t.electricity))
        Column(Modifier.weight(1f)) {
            Text(fmt.money(t.fuel), color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            t.fuelCalculated?.let {
                Text("≈ ${fmt.money(it)}", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
        Cell(fmt.money(t.other))
        Cell(fmt.money(t.total))
        Cell(fmt.km(t.km))
        Cell(t.costPerKm?.let(fmt::two) ?: "—")
    }
}

@Composable
private fun TableRow(header: Boolean = false, bold: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val weight = if (header || bold) FontWeight.SemiBold else FontWeight.Normal
    CompositionLocalProvider(LocalCellStyle provides CellStyle(header, weight)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
            content = content,
        )
    }
}

private data class CellStyle(val header: Boolean, val weight: FontWeight)

private val LocalCellStyle = compositionLocalOf { CellStyle(false, FontWeight.Normal) }

@Composable
private fun RowScope.Cell(text: String, weight: Float = 1f, start: Boolean = false) {
    val style = LocalCellStyle.current
    Text(
        text,
        color = if (style.header) TextSecondary else TextPrimary,
        fontSize = if (style.header) 12.sp else 13.sp,
        fontWeight = style.weight,
        textAlign = if (start) TextAlign.Start else TextAlign.End,
        modifier = Modifier.weight(weight),
    )
}
