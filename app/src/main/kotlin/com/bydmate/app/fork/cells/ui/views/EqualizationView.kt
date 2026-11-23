package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.cellVolts
import com.bydmate.app.fork.cells.ui.cellColor
import com.bydmate.app.fork.cells.ui.signedMv
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.ui.zoneRes
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * «Выравнивание» (mockup V4): after a charge, the spread by the hour after its end (the first hour without a verdict) with
 * the previous full charge dashed; otherwise the rest spread of two weeks against the plateau lines. On the right the
 * verdict and the five cells that move most. [BatteryCheckUi.equalization] is read on IO; until then the chart is empty. fork-local.
 */
@Composable
internal fun EqualizationView(ui: BatteryCheckUi) {
    val e = ui.selected ?: return
    val d = ui.equalization
    val model = remember(e, d, ui.levels) { d?.let { equalizationModel(e, it, ui.levels) } }
    val mode = d?.mode ?: eqModeOf(e)
    val narrow = LocalCheckNarrow.current
    // the list does not wait for the read: the cells are the measurement's own
    val cells = model?.cells ?: remember(e, mode) { extremeCells(e, highest = mode == EqMode.AFTER_CHARGE) }
    TwoColumns(
        0.62f,
        left = { m -> EqChartCard(e, mode, model, ui.equalizationFailed, m) },
        right = { m ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = m) {
                VerdictSummary(e, Modifier.fillMaxWidth())
                CellsListCard(e, mode, cells, if (narrow) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f))
            }
        },
    )
}

/**
 * What the chart means in words: after a charge, how the BMS balances; for the parkings, by the verdict, or the selected
 * measurement's own value when it is not a plateau one (it is not on the plateau chart).
 */
private fun explainText(e: Evaluation, mode: EqMode): TextRes {
    if (mode == EqMode.AFTER_CHARGE) return TextRes(R.string.fork_cells_eq_text_charge)
    if (e.row != ZoneRow.PLATEAU) return TextRes(R.string.fork_cells_eq_text_other, TextRes(zoneRes(e.row)), e.spreadMv, e.limits.normMv)
    val svc = eqLines(e, mode).second ?: ZoneRow.PLATEAU.serviceMv ?: 0
    return when (e.level) {
        Level.SERVICE -> TextRes(R.string.fork_cells_eq_text_service)
        Level.WATCH -> TextRes(R.string.fork_cells_eq_text_watch, svc)
        Level.OK -> TextRes(R.string.fork_cells_eq_text_ok)
        Level.NONE -> TextRes(R.string.fork_cells_eq_text_none)
    }
}

@Composable
private fun EqChartCard(e: Evaluation, mode: EqMode, model: EqualizationModel?, failed: Boolean, modifier: Modifier) {
    val narrow = LocalCheckNarrow.current
    val charge = mode == EqMode.AFTER_CHARGE
    val axis = when {
        !charge -> R.string.fork_cells_eq_axis_parkings
        model?.awakeAxis == true -> R.string.fork_cells_eq_axis_wake
        else -> R.string.fork_cells_eq_axis_charge
    }
    val cold = e.limits.cold && e.row == (if (charge) ZoneRow.TOP else ZoneRow.PLATEAU)
    ViewCard(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp).then(if (narrow) Modifier else Modifier.fillMaxSize())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { CardHead(TextRes(if (charge) R.string.fork_cells_eq_title_charge else R.string.fork_cells_eq_title_parkings).text()) }
                if (cold) Text(stringResource(R.string.fork_cells_norm_cold_note), color = AccentBlue, fontSize = 12.5.sp, style = tnum())
            }
            Spacer(Modifier.height(4.dp))
            EqChart(e, mode, model, failed, if (narrow) Modifier.fillMaxWidth().height(300.dp) else Modifier.fillMaxWidth().weight(1f))
            // the grey first hour after a charge end is explained here, not inside its narrow band
            val firstHour = if (charge && model?.awakeAxis == false) " · " + stringResource(R.string.fork_cells_eq_first_hour) else ""
            Text(
                stringResource(axis) + firstHour,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
            Text(explainText(e, mode).text(), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, style = tnum(), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * «Выравниваются сильнее всего · ячейки выше середины …» (the highest after a charge) or «Ниже всех · …» on one line, then
 * five rows № · voltage · deviation. In a wide window the rows share the card's height (up to 40 dp each), so all five show
 * on the smallest screen; in a narrow one they are 36 dp.
 */
@Composable
private fun CellsListCard(e: Evaluation, mode: EqMode, cells: List<Pair<Int, Double>>, modifier: Modifier) {
    val charge = mode == EqMode.AFTER_CHARGE
    val narrow = LocalCheckNarrow.current
    val muted = TextMuted
    val head = buildAnnotatedString {
        append(stringResource(if (charge) R.string.fork_cells_eq_top_title else R.string.fork_cells_eq_low_title))
        withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal, fontSize = 12.sp)) {
            append(" · " + stringResource(if (charge) R.string.fork_cells_eq_top_sub else R.string.fork_cells_eq_low_sub))
        }
    }
    ViewCard(modifier) {
        Column(Modifier.then(if (narrow) Modifier else Modifier.fillMaxSize()).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
            Text(head, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            if (cells.isEmpty()) {
                Text(stringResource(R.string.fork_cells_dev_none), color = TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = if (narrow) Modifier else Modifier.weight(1f)) {
                cells.forEach { (cell, dev) ->
                    val row = if (narrow) Modifier.height(ROW_NARROW) else Modifier.weight(1f).heightIn(max = ROW_MAX)
                    CellLine(e, cell, dev, row)
                }
            }
        }
    }
}

private val ROW_MAX = 40.dp
private val ROW_NARROW = 36.dp

@Composable
private fun CellLine(e: Evaluation, cell: Int, dev: Double, modifier: Modifier) {
    val cls = e.classes.getOrElse(cell - 1) { CellClass.NORMAL }
    val locale = LocalConfiguration.current.locales[0]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().background(CardSurface, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp),
    ) {
        Text(TextRes(R.string.fork_cells_cell_no, cell).text(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, style = tnum())
        Text(
            " · " + TextRes(R.string.fork_cells_val_cell_v, cellVolts(e.input.cellsMv.getOrElse(cell - 1) { 0 }, locale)).text(),
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            style = tnum(),
            modifier = Modifier.weight(1f),
        )
        Text(
            TextRes(R.string.fork_cells_val_signed_mv, signedMv(dev)).text(),
            color = if (cls == CellClass.NORMAL) TextSecondary else cellColor(cls, e.level),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            maxLines = 1,
            style = tnum(),
        )
    }
}
