package com.bydmate.app.fork.cells.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.BatteryCheckUi
import com.bydmate.app.fork.cells.ui.CellCanvas
import com.bydmate.app.fork.cells.ui.LocalCheckNarrow
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.levelColor
import com.bydmate.app.fork.cells.ui.normalTileColor
import com.bydmate.app.fork.cells.ui.text
import com.bydmate.app.fork.cells.ui.titleRes
import com.bydmate.app.fork.cells.ui.tnum
import com.bydmate.app.fork.cells.ui.zoneRes
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.ceil

private const val ROWS_WIDE = 4
private const val ROWS_NARROW = 8
private const val MAX_COLUMNS_WIDE = 50
private const val MAX_COLUMNS_NARROW = 25
private val MAX_TILE = 40.dp

/**
 * Columns of the cell grid for a pack of [cells]: four rows when wide (46 columns for 184 cells), eight when narrow (23);
 * a bigger pack keeps at most 50 / 25 columns and takes more rows, so a tile never gets too small to see.
 */
internal fun gridColumns(cells: Int, narrow: Boolean): Int {
    val rows = if (narrow) ROWS_NARROW else ROWS_WIDE
    val max = if (narrow) MAX_COLUMNS_NARROW else MAX_COLUMNS_WIDE
    return ((cells + rows - 1) / rows).coerceIn(1, max)
}

/**
 * «Линейка» (mockup V2): the zone's scale with an arrow at the spread on the left; on the right the verdict in words, every
 * cell of the pack (colour only on the ones off the median) and the four checks. The grid takes the height its width gives
 * it, all rows, never what is left over: on a low window the right column scrolls instead (the checks go under the fold,
 * the cells never do). fork-local.
 */
@Composable
internal fun RulerView(ui: BatteryCheckUi) {
    val e = ui.selected ?: return
    val model = remember(e) { rulerModel(e) }
    val cells = e.classes.size
    if (LocalCheckNarrow.current) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            TitleCard(e, Modifier.fillMaxWidth())
            CellsGridCard(e, gridColumns(cells, narrow = true), Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ScaleCard(e, model, Modifier.width(170.dp).height(360.dp))
                ChecksGrid(model, e, Modifier.weight(1f))
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        ScaleCard(e, model, Modifier.width(190.dp).fillMaxHeight())
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
        ) {
            TitleCard(e, Modifier.fillMaxWidth())
            CellsGridCard(e, gridColumns(cells, narrow = false), Modifier.fillMaxWidth())
            ChecksGrid(model, e, Modifier.fillMaxWidth())
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
            if (e.limits.cold) {
                Text(stringResource(R.string.fork_cells_norm_cold_note), color = AccentBlue, fontSize = 12.sp, textAlign = TextAlign.Center, style = tnum())
            }
        }
    }
}

/** The verdict as a headline in its colour and what it says in words. */
@Composable
private fun TitleCard(e: Evaluation, modifier: Modifier) {
    ViewCard(modifier) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(titleRes(e)),
                    color = if (e.level == Level.NONE) TextPrimary else levelColor(e.level),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 29.sp,
                )
                if (e.preliminary && e.level != Level.NONE) {
                    Text(stringResource(R.string.fork_cells_level_prelim), color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
                }
            }
            summaryTexts(e).firstOrNull()?.let {
                Text(it.text(), color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, style = tnum(), modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

/** Every tile of the pack, the grid as tall as its width makes it; under it what the colours mean. */
@Composable
private fun CellsGridCard(e: Evaluation, columns: Int, modifier: Modifier) {
    ViewCard(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            WidthGrid(e, columns, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            GridLegend(e)
        }
    }
}

/**
 * [CellCanvas.CellGrid] with square tiles as wide as the width allows (3 dp gaps, as the grid draws them, at most [MAX_TILE]):
 * its height follows from the width and the rows, so no row is ever cut.
 */
@Composable
private fun WidthGrid(e: Evaluation, columns: Int, modifier: Modifier) {
    val rows = ceil(e.classes.size / columns.toDouble()).toInt().coerceAtLeast(1)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = 3.dp
        val tile = ((maxWidth - gap * (columns - 1)) / columns).coerceIn(2.dp, MAX_TILE)
        CellCanvas.CellGrid(e, columns, Modifier.size(tile * columns + gap * (columns - 1), tile * rows + gap * (rows - 1)))
    }
}

/** «Ячеек: 184», «цветом — отклонение больше половины нормы», the three swatches, on one line (two when narrow). */
@Composable
private fun GridLegend(e: Evaluation) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(TextRes(R.string.fork_cells_norm_cells, e.classes.size).text(), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tnum())
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.fork_cells_ruler_grid_caption),
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(normalTileColor(), stringResource(R.string.fork_cells_legend_normal))
            LegendItem(AccentOrange, stringResource(R.string.fork_cells_ruler_legend_notice))
            LegendItem(SocRed, stringResource(R.string.fork_cells_ruler_legend_strong))
        }
    }
}

@Composable
private fun LegendItem(c: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(c, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, color = TextSecondary, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, softWrap = false)
    }
}
