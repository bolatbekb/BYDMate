package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.verdict.Laggard
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import java.util.Locale

/** «История»: spread per rest period (up to 30, one each), dashed norm, dots by level; the laggards under it. */
@Composable
internal fun HistoryTab(ui: BatteryCheckUi) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.fork_cells_history_caption), color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        if (ui.history.isEmpty()) {
            Text(stringResource(R.string.fork_cells_history_empty), color = TextSecondary, fontSize = 15.sp)
        } else {
            CellCanvas.SpreadHistory(ui.history, ui.selectedId, Modifier.fillMaxWidth().height(250.dp))
        }
        val laggards = ui.selected?.laggards.orEmpty()
        if (laggards.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { laggards.forEach { LaggardRow(it) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LaggardRow(l: Laggard) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(CardSurface, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(TextRes(R.string.fork_cells_history_laggard, l.cell, l.times, l.of).text(), color = TextPrimary, fontSize = 15.sp, style = tnum(), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.fork_cells_history_watch), color = AccentOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** «Замеры»: every measurement of the picker, newest first, with its conditions; a tap selects it. */
@Composable
internal fun MeasuresTab(ui: BatteryCheckUi, actions: BatteryCheckActions) {
    val items = remember(ui.picker) { ui.picker.sortedByDescending { it.ts } }
    if (items.isEmpty()) {
        Text(stringResource(R.string.fork_cells_empty), color = TextSecondary, fontSize = 15.sp)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item -> MeasureRow(item, item.id == ui.selectedId) { actions.select(item.id) } }
    }
}

@Composable
private fun MeasureRow(item: PickerItem, selected: Boolean, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val res = LocalContext.current.resources
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) CardSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelDot(item.level)
            Spacer(Modifier.width(12.dp))
            Text(
                "${stringResource(kindRes(item.kind))} · ${dayMonthTime(item.ts)}",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                style = tnum(),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(TextRes(R.string.fork_cells_val_mv, item.spreadMv).text(), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = tnum())
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 22.dp, top = 3.dp)) {
            Text(
                measureLine(item, locale).joinToString(" · ") { resolve(it, res) },
                color = TextMuted,
                fontSize = 13.sp,
                style = tnum(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            LevelLabel(item.level, item.preliminary, 13.sp)
        }
    }
}

/** «Заряд 56 % · ток 3,1 А · покой 9 ч · датчики 19–20 °C»; unknown values are left out. */
private fun measureLine(item: PickerItem, locale: Locale): List<TextRes> = buildList {
    item.soc?.let { add(TextRes(R.string.fork_cells_chip_soc, TextRes(R.string.fork_cells_val_percent, it))) }
    item.currentA?.let { add(TextRes(R.string.fork_cells_part_current, decimal(it, 1, locale))) }
    if (item.kind != MeasureKind.LOAD) {
        add(item.restMin?.let { TextRes(R.string.fork_cells_part_rest, durationText(it)) } ?: TextRes(R.string.fork_cells_part_rest_unknown))
    }
    if (item.probeMinC != null && item.probeMaxC != null) add(TextRes(R.string.fork_cells_part_probes, rangeText(item.probeMinC, item.probeMaxC)))
}
