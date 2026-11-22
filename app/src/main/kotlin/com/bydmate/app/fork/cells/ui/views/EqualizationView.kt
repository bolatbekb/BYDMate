package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.cellVolts
import com.bydmate.app.fork.cells.ui.classColor
import com.bydmate.app.fork.cells.ui.signedMv
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
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
    TwoColumns(
        0.62f,
        left = { m -> EqChartCard(e, mode, model, m) },
        right = { m ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = m) {
                VerdictSummary(e, Modifier.fillMaxWidth())
                CellsListCard(e, mode, if (narrow) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f))
            }
        },
    )
}

@Composable
private fun EqChartCard(e: Evaluation, mode: EqMode, model: EqualizationModel?, modifier: Modifier) {
    val narrow = LocalCheckNarrow.current
    val charge = mode == EqMode.AFTER_CHARGE
    val axis = when {
        !charge -> R.string.fork_cells_eq_axis_parkings
        model?.awakeAxis == true -> R.string.fork_cells_eq_axis_wake
        else -> R.string.fork_cells_eq_axis_charge
    }
    val explain = when {
        charge -> R.string.fork_cells_eq_text_charge
        e.level == Level.SERVICE -> R.string.fork_cells_eq_text_service
        e.level == Level.WATCH -> R.string.fork_cells_eq_text_watch
        e.level == Level.OK -> R.string.fork_cells_eq_text_ok
        else -> R.string.fork_cells_eq_text_none
    }
    ViewCard(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 14.dp).then(if (narrow) Modifier else Modifier.fillMaxSize())) {
            CardHead(TextRes(if (charge) R.string.fork_cells_eq_title_charge else R.string.fork_cells_eq_title_parkings).text())
            Spacer(Modifier.height(6.dp))
            EqChart(e, mode, model, if (narrow) Modifier.fillMaxWidth().height(300.dp) else Modifier.fillMaxWidth().weight(1f))
            Text(stringResource(axis), color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            Text(stringResource(explain), color = TextSecondary, fontSize = 13.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** «Выравниваются сильнее всего» (the highest after a charge) or «Ниже всех»: № · voltage · deviation. */
@Composable
private fun CellsListCard(e: Evaluation, mode: EqMode, modifier: Modifier) {
    val charge = mode == EqMode.AFTER_CHARGE
    val cells = remember(e, charge) { extremeCells(e, highest = charge) }
    val locale = LocalConfiguration.current.locales[0]
    ViewCard(modifier) {
        Column(cardScroll().padding(horizontal = 20.dp, vertical = 16.dp)) {
            CardHead(
                stringResource(if (charge) R.string.fork_cells_eq_top_title else R.string.fork_cells_eq_low_title),
                stringResource(if (charge) R.string.fork_cells_eq_top_sub else R.string.fork_cells_eq_low_sub),
            )
            Spacer(Modifier.height(10.dp))
            if (cells.isEmpty()) {
                Text(stringResource(R.string.fork_cells_dev_none), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cells.forEach { (cell, dev) ->
                    val cls = e.classes.getOrElse(cell - 1) { CellClass.NORMAL }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().background(CardSurface, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(TextRes(R.string.fork_cells_cell_no, cell).text(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tnum())
                        Text(
                            " · " + TextRes(R.string.fork_cells_val_cell_v, cellVolts(e.input.cellsMv.getOrElse(cell - 1) { 0 }, locale)).text(),
                            color = TextSecondary,
                            fontSize = 15.sp,
                            style = tnum(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            TextRes(R.string.fork_cells_val_signed_mv, signedMv(dev)).text(),
                            color = if (cls == CellClass.NORMAL) TextSecondary else classColor(cls),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            style = tnum(),
                        )
                    }
                }
            }
        }
    }
}
