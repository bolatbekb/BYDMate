package com.bydmate.app.fork.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.MonthTotals
import com.bydmate.app.fork.ledger.domain.RefuelSegment
import com.bydmate.app.ui.settings.SettingDivider
import com.bydmate.app.ui.settings.SettingHint
import com.bydmate.app.ui.settings.SettingSubhead
import com.bydmate.app.ui.settings.UnitChip
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

private sealed interface LedgerEditor {
    data object NewRefuel : LedgerEditor
    data class EditRefuel(val e: RefuelEntity) : LedgerEditor
    data object NewExpense : LedgerEditor
    data class EditExpense(val e: ExpenseEntity) : LedgerEditor
}

/** Body of Settings › Expenses. Plain Columns: it lives inside the scrolling settings card. */
@Composable
fun LedgerTabs(vm: LedgerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<LedgerEditor?>(null) }
    val fmt = rememberLedgerFmt()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.ledger_tab_summary, R.string.ledger_tab_refuels,
            R.string.ledger_tab_expenses, R.string.ledger_tab_settings,
        ).forEachIndexed { i, res -> UnitChip(stringResource(res), selected = tab == i) { tab = i } }
    }
    if (!state.parked) {
        Text(stringResource(R.string.ledger_locked_while_driving), color = AccentOrange, fontSize = 13.sp)
    }
    when (tab) {
        0 -> SummaryTab(state, fmt)
        1 -> RefuelsTab(state, fmt, onAdd = { editor = LedgerEditor.NewRefuel }) { editor = LedgerEditor.EditRefuel(it) }
        2 -> ExpensesTab(state, fmt, onAdd = { editor = LedgerEditor.NewExpense }) { editor = LedgerEditor.EditExpense(it) }
        else -> LedgerSettingsTab(state, vm)
    }

    val close = { editor = null }
    when (val ed = editor) {
        null -> {}
        LedgerEditor.NewRefuel, is LedgerEditor.EditRefuel -> {
            val base = (ed as? LedgerEditor.EditRefuel)?.e
            RefuelEditorDialog(
                initial = base?.let(LedgerEditorState::from) ?: LedgerEditorState.new(System.currentTimeMillis()),
                currency = state.currencySymbol,
                parked = state.parked,
                prefill = vm::prefillFor,
                onSave = { r ->
                    if (base?.status == "draft") vm.confirmDraft(r) else vm.saveRefuel(r)
                    close()
                },
                onDelete = base?.let { b -> { vm.deleteRefuel(b); close() } },
                onDismiss = close,
            )
        }
        LedgerEditor.NewExpense, is LedgerEditor.EditExpense -> {
            val base = (ed as? LedgerEditor.EditExpense)?.e
            ExpenseEditorDialog(
                initial = base,
                nowMs = System.currentTimeMillis(),
                categories = state.categories,
                currency = state.currencySymbol,
                parked = state.parked,
                prefill = vm::prefillFor,
                onSave = { e -> vm.saveExpense(e); close() },
                onDelete = base?.let { b -> { vm.deleteExpense(b); close() } },
                onDismiss = close,
            )
        }
    }
}

// ─── Summary ────────────────────────────────────────────────────────────────

@Composable
private fun SummaryTab(state: LedgerUiState, fmt: LedgerFmt) {
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

// ─── Refuels ────────────────────────────────────────────────────────────────

@Composable
private fun RefuelsTab(state: LedgerUiState, fmt: LedgerFmt, onAdd: () -> Unit, onOpen: (RefuelEntity) -> Unit) {
    LedgerButton(stringResource(R.string.ledger_add_refuel), enabled = state.parked, onClick = onAdd)
    val drafts = state.refuels.filter { it.status == "draft" }
    val confirmed = state.refuels.filter { it.status != "draft" }
    if (state.loaded && state.refuels.isEmpty()) SettingHint(stringResource(R.string.ledger_refuels_empty))
    if (drafts.isNotEmpty()) {
        SettingSubhead(stringResource(R.string.ledger_drafts_title))
        drafts.forEach { d -> DraftRow(d, fmt, state.parked) { onOpen(d) } }
    }
    val segByEnd = state.segments.associateBy { it.to.id }
    confirmed.groupBy { it.date.month() }.forEach { (m, list) ->
        SettingSubhead(fmt.month(m))
        list.forEach { r ->
            RefuelRow(r, fmt, state.currencySymbol, state.parked) { onOpen(r) }
            segByEnd[r.id]?.let { SegmentLine(it, fmt, state.currencySymbol, state.mismatchThresholdPct) }
        }
    }
}

@Composable
private fun DraftRow(d: RefuelEntity, fmt: LedgerFmt, parked: Boolean, onConfirm: () -> Unit) {
    EntryRow(clickable = false, onClick = {}) {
        Text("${fmt.date(d.date)} ${fmt.time(d.date)}", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1.4f))
        val before = d.tankPercentBefore
        val after = d.tankPercentAfter
        Text(
            if (before != null && after != null) stringResource(R.string.ledger_draft_tank, "$before%", "$after%") else "",
            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1.2f),
        )
        Text(
            if (d.liters > 0.0) stringResource(R.string.ledger_draft_liters, fmt.two(d.liters)) else "",
            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f),
        )
        LedgerButton(stringResource(R.string.ledger_confirm), enabled = parked, onClick = onConfirm)
    }
}

@Composable
private fun RefuelRow(r: RefuelEntity, fmt: LedgerFmt, currency: String, parked: Boolean, onClick: () -> Unit) {
    EntryRow(clickable = parked, onClick = onClick) {
        Text("${fmt.date(r.date)} ${fmt.time(r.date)}", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1.4f))
        Text(r.odometerKm?.let { stringResource(R.string.ledger_unit_km, fmt.km(it)) } ?: "—", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.ledger_unit_l, fmt.two(r.liters)), color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(0.8f))
        Text(r.totalCost?.let { "${fmt.money(it)} $currency" } ?: "—", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(r.pricePerLiter?.let { stringResource(R.string.ledger_unit_per_l, fmt.two(it), currency) } ?: "", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (r.fullTank) stringResource(R.string.ledger_full_mark) else "", color = AccentGreen, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun SegmentLine(s: RefuelSegment, fmt: LedgerFmt, currency: String, threshold: Double) {
    val parts = buildList {
        if (!s.complete || s.distanceKm == null) {
            add(stringResource(R.string.ledger_seg_incomplete))
        } else {
            add(stringResource(R.string.ledger_seg_km, fmt.km(s.distanceKm)))
            s.per100Receipts?.let { add(stringResource(R.string.ledger_seg_receipts, fmt.one(it))) }
            s.per100Car?.let { add(stringResource(R.string.ledger_seg_car, fmt.one(it))) }
            s.mismatchPct?.let { add(stringResource(R.string.ledger_seg_mismatch, fmt.signedPct(it))) }
            s.costPerKm?.let { add(stringResource(R.string.ledger_seg_cost_km, fmt.two(it), currency)) }
        }
    }
    val alarm = s.mismatchPct?.let { abs(it) > threshold } == true
    Text(
        stringResource(R.string.ledger_seg_label) + ": " + parts.joinToString(" · "),
        color = if (alarm) AccentOrange else TextMuted,
        fontSize = 12.sp,
        fontWeight = if (alarm) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
    )
}

// ─── Expenses ───────────────────────────────────────────────────────────────

@Composable
private fun ExpensesTab(state: LedgerUiState, fmt: LedgerFmt, onAdd: () -> Unit, onOpen: (ExpenseEntity) -> Unit) {
    LedgerButton(stringResource(R.string.ledger_add_expense), enabled = state.parked, onClick = onAdd)
    if (state.loaded && state.expenses.isEmpty()) SettingHint(stringResource(R.string.ledger_expenses_empty))
    val cats = state.categories.associateBy { it.id }
    state.expenses.groupBy { it.date.month() }.forEach { (m, list) ->
        SettingSubhead(fmt.month(m) + " — " + fmt.money(list.sumOf { it.amount }) + " " + state.currencySymbol)
        val byCat = list.groupBy { it.categoryId }.map { (id, l) -> id to l.sumOf { it.amount } }.sortedByDescending { it.second }
        Text(
            byCat.map { (id, sum) -> "${cats[id]?.let { categoryLabel(it) } ?: "?"} ${fmt.money(sum)}" }.joinToString(" · "),
            color = TextSecondary, fontSize = 12.sp,
        )
        list.forEach { e -> ExpenseRow(e, cats[e.categoryId], fmt, state.currencySymbol, state.parked) { onOpen(e) } }
    }
}

@Composable
private fun ExpenseRow(e: ExpenseEntity, cat: ExpenseCategoryEntity?, fmt: LedgerFmt, currency: String, parked: Boolean, onClick: () -> Unit) {
    EntryRow(clickable = parked, onClick = onClick) {
        Text(fmt.date(e.date), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(cat?.let { categoryLabel(it) } ?: "?", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(e.title.orEmpty(), color = TextPrimary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1.6f))
        Text("${fmt.money(e.amount)} $currency", color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EntryRow(clickable: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSurfaceElevated, RoundedCornerShape(8.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private fun Long.month(): YearMonth = YearMonth.from(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

