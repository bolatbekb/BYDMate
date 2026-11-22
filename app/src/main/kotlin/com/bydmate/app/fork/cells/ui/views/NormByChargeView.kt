package com.bydmate.app.fork.cells.ui.views

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.CellCanvas
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.rangeText
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

private val STRIP_MARKS = listOf(1, 46, 92, 138, 184)

/**
 * «Норма по заряду» (mockup V1): the chart «charge → allowed spread» with the research thresholds of each band and the point
 * «Вы здесь» on the left; the verdict, why the norm is what it is, and the 184 cells as a strip on the right. fork-local.
 */
@Composable
internal fun NormByChargeView(ui: BatteryCheckUi) {
    val e = ui.selected ?: return
    val model = remember(e) { normChartModel(e) }
    val narrow = LocalCheckNarrow.current
    TwoColumns(
        0.6f,
        left = { m -> NormChartCard(e, model, m) },
        right = { m ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = m) {
                VerdictSummary(e, Modifier.fillMaxWidth())
                WhyCard(e, if (narrow) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f))
                StripCard(e, Modifier.fillMaxWidth())
            }
        },
    )
}

@Composable
private fun NormChartCard(e: Evaluation, model: NormChartModel, modifier: Modifier) {
    val narrow = LocalCheckNarrow.current
    ViewCard(modifier) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp).then(if (narrow) Modifier else Modifier.fillMaxSize())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { CardHead(stringResource(R.string.fork_cells_norm_title)) }
                // the measurement's zone shows its doubled limits in the cold
                if (model.cold) Text(stringResource(R.string.fork_cells_norm_cold_note), color = AccentBlue, fontSize = 12.5.sp, style = tnum())
            }
            Spacer(Modifier.height(6.dp))
            NormChart(model, e, if (narrow) Modifier.fillMaxWidth().height(320.dp) else Modifier.fillMaxWidth().weight(1f))
            Text(
                stringResource(R.string.fork_cells_norm_axis),
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** «Почему так»: why the zone has its norm, and where its thresholds come from. */
@Composable
private fun WhyCard(e: Evaluation, modifier: Modifier) {
    ViewCard(modifier) {
        Column(cardScroll().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)) {
            CardHead(stringResource(R.string.fork_cells_norm_why))
            Text(TextRes(whyRes(e.row)).text(), color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, style = tnum(), modifier = Modifier.padding(top = 4.dp))
            Text(
                TextRes(R.string.fork_cells_norm_source, TextRes(sourceRes(e.row))).text(),
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                style = tnum(),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** «184 ячейки · отклонение от середины · датчики 19–20 °C» over the strip, cell numbers under it. */
@Composable
private fun StripCard(e: Evaluation, modifier: Modifier) {
    val probes = e.input.probesC.filter { it != NO_TEMP }
    val caption = if (probes.isEmpty()) {
        stringResource(R.string.fork_cells_norm_strip_plain)
    } else {
        TextRes(R.string.fork_cells_norm_strip, rangeText(probes.min(), probes.max())).text()
    }
    ViewCard(modifier) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(TextRes(R.string.fork_cells_norm_cells, e.classes.size).text(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tnum())
                Text(caption, color = TextSecondary, fontSize = 12.5.sp, textAlign = TextAlign.End, style = tnum(), maxLines = 1, modifier = Modifier.weight(1f).padding(start = 12.dp))
            }
            CellCanvas.DeviationStrip(e, Modifier.fillMaxWidth().height(50.dp).padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
                STRIP_MARKS.filter { it <= e.classes.size }.forEach { Text("$it", color = TextMuted, fontSize = 11.sp, lineHeight = 13.sp, style = tnum()) }
            }
        }
    }
}
