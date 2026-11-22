package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlin.math.ceil

private const val GRID_COLUMNS = 23
private const val GRID_COLUMNS_NARROW = 12
private val GRID_ROW_NARROW = 34.dp

/**
 * The window's body until a chosen view takes its place: the verdict with its reasons, and the
 * 184 cells as a strip and a grid. fork-local.
 */
@Composable
internal fun VerdictBody(ui: BatteryCheckUi) {
    val e = ui.selected ?: return
    if (LocalCheckNarrow.current) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VerdictCard(e, Modifier.fillMaxWidth())
            CellsCard(e, GRID_COLUMNS_NARROW, Modifier.fillMaxWidth(), fill = false)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            VerdictCard(e, Modifier.weight(0.38f).fillMaxHeight())
            CellsCard(e, GRID_COLUMNS, Modifier.weight(0.62f).fillMaxHeight(), fill = true)
        }
    }
}

/** Title in the level's colour, «предварительно» under it, the spread against the norm, the reasons. */
@Composable
internal fun VerdictCard(e: Evaluation, modifier: Modifier) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated), modifier = modifier) {
        // In a narrow window the whole column scrolls already: a second vertical scroll would get no height bound.
        val scroll = if (LocalCheckNarrow.current) Modifier else Modifier.verticalScroll(rememberScrollState())
        Column(scroll.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(e.level)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(titleRes(e)),
                        color = if (e.level == Level.NONE) TextPrimary else levelColor(e.level),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp,
                    )
                    if (e.preliminary && e.level != Level.NONE) {
                        Text(stringResource(R.string.fork_cells_level_prelim), color = TextMuted, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(TextRes(R.string.fork_cells_spread_line, e.spreadMv, e.limits.normMv).text(), color = TextSecondary, fontSize = 16.sp, style = tnum())
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                reasonTexts(e).forEach { ReasonLine(it.text()) }
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Level) {
    val glyph = when (level) {
        Level.OK -> "✓"
        Level.WATCH, Level.SERVICE -> "!"
        Level.NONE -> "–"
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).background(levelColor(level), RoundedCornerShape(50))) {
        Text(glyph, color = NavyDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReasonLine(text: String) {
    Row {
        Box(Modifier.padding(top = 8.dp).size(5.dp).background(TextMuted, RoundedCornerShape(50)))
        Spacer(Modifier.width(12.dp))
        Text(text, color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp, style = tnum())
    }
}

/** «Отклонение каждой ячейки от медианы»: the strip, the legend and the grid; [fill] lets the grid take the card's height. */
@Composable
private fun CellsCard(e: Evaluation, columns: Int, modifier: Modifier, fill: Boolean) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated), modifier = modifier) {
        Column(if (fill) Modifier.fillMaxSize().padding(18.dp) else Modifier.padding(18.dp)) {
            Text(stringResource(R.string.fork_cells_strip_caption), color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Legend()
            Spacer(Modifier.height(12.dp))
            CellCanvas.DeviationStrip(e, Modifier.fillMaxWidth().height(if (fill) 110.dp else 80.dp))
            Spacer(Modifier.height(16.dp))
            val rows = ceil(e.classes.size / columns.toDouble()).toInt()
            CellCanvas.CellGrid(e, columns, if (fill) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(GRID_ROW_NARROW * rows))
        }
    }
}
