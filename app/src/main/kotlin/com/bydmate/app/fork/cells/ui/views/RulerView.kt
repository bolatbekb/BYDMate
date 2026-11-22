package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.CellCanvas
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.levelColor
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.titleRes
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.ui.zoneRes
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardBorder
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.ceil

private const val GRID_COLUMNS = 46
private const val GRID_COLUMNS_NARROW = 23
private const val TILE_ALPHA = 0.55f

/**
 * «Линейка» (mockup V2): the zone's scale with an arrow at the spread on the left; the verdict in words, the four checks
 * and the 184 cells (colour only on the ones off the median) on the right. fork-local.
 */
@Composable
internal fun RulerView(ui: BatteryCheckUi) {
    val e = ui.selected ?: return
    val model = remember(e) { rulerModel(e) }
    if (LocalCheckNarrow.current) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TitleCard(e, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ScaleCard(e, model, Modifier.width(170.dp).height(360.dp))
                ChecksGrid(model, e, Modifier.weight(1f))
            }
            CellsGridCard(e, GRID_COLUMNS_NARROW, Modifier.fillMaxWidth())
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        ScaleCard(e, model, Modifier.width(190.dp).fillMaxHeight())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
            TitleCard(e, Modifier.fillMaxWidth())
            ChecksGrid(model, e, Modifier.fillMaxWidth())
            CellsGridCard(e, GRID_COLUMNS, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** «разброс, мВ», the ruler, «шкала для зоны плато 15–85 %». */
@Composable
private fun ScaleCard(e: Evaluation, model: RulerModel, modifier: Modifier) {
    ViewCard(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.fork_cells_ruler_caption), color = TextSecondary, fontSize = 13.sp)
            RulerScale(model, Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp))
            Text(stringResource(R.string.fork_cells_ruler_zone), color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Text(TextRes(zoneRes(e.row)).text(), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, style = tnum())
        }
    }
}

/** The verdict as a headline in its colour and what it says in words. */
@Composable
private fun TitleCard(e: Evaluation, modifier: Modifier) {
    ViewCard(modifier) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(titleRes(e)),
                    color = if (e.level == Level.NONE) TextPrimary else levelColor(e.level),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 32.sp,
                )
                if (e.preliminary && e.level != Level.NONE) {
                    Text(stringResource(R.string.fork_cells_level_prelim), color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
                }
            }
            summaryTexts(e).firstOrNull()?.let {
                Text(it.text(), color = TextSecondary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, style = tnum(), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The cells as tiles (square, 46 × 4), with what the colours mean beside them (under them when narrow). */
@Composable
private fun CellsGridCard(e: Evaluation, columns: Int, modifier: Modifier) {
    val narrow = LocalCheckNarrow.current
    val rows = ceil(e.classes.size / columns.toDouble()).toInt().coerceAtLeast(1)
    ViewCard(modifier) {
        val grid = @Composable { m: Modifier -> CellCanvas.CellGrid(e, columns, m.aspectRatio(columns / rows.toFloat())) }
        if (narrow) {
            Column(Modifier.padding(16.dp)) {
                grid(Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                GridLegend(e)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { grid(Modifier.fillMaxWidth()) }
                Spacer(Modifier.width(22.dp))
                Column(Modifier.width(230.dp)) { GridLegend(e) }
            }
        }
    }
}

@Composable
private fun GridLegend(e: Evaluation) {
    Text(TextRes(R.string.fork_cells_norm_cells, e.classes.size).text(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tnum())
    Text(stringResource(R.string.fork_cells_ruler_grid_caption), color = TextSecondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    listOf(
        CardBorder.copy(alpha = TILE_ALPHA) to R.string.fork_cells_legend_normal,
        AccentOrange to R.string.fork_cells_legend_noticeable,
        SocRed to R.string.fork_cells_legend_strong,
    ).forEach { (c, res) -> LegendLine(c, stringResource(res)) }
}

@Composable
private fun LegendLine(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(11.dp).background(c, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextSecondary, fontSize = 12.5.sp)
    }
}
