package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellStats
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.CheckView
import com.bydmate.app.fork.cells.ui.BlockRow
import com.bydmate.app.fork.cells.ui.CellsSettingsViewModel
import com.bydmate.app.fork.cells.ui.intervalLabelRes
import com.bydmate.app.fork.cells.ui.profileLabelRes
import com.bydmate.app.fork.cells.ui.sizeText
import com.bydmate.app.fork.cells.ui.viewLabelRes
import com.bydmate.app.fork.cells.ui.visibleRows
import com.bydmate.app.ui.skin.AlertDialog
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.skin.TextButton
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings › System: battery check — car profile, cell data collection, view, stored data. fork-local. */
@Composable
internal fun BatteryCheckBlock(vm: CellsSettingsViewModel = hiltViewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val rows = visibleRows(config)
    SectionHeader(text = stringResource(R.string.fork_cells_settings_header))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val profiles = CarProfile.entries
            SettingChipRow(
                title = stringResource(R.string.fork_cells_car_title),
                description = stringResource(R.string.fork_cells_car_desc),
                options = profiles.map { stringResource(profileLabelRes(it)) },
                selectedIndex = profiles.indexOf(config.profile),
                onSelect = { vm.setProfile(profiles[it]) },
            )
            if (BlockRow.COLLECT in rows) {
                SettingDivider()
                SettingToggleRow(
                    title = stringResource(R.string.fork_cells_collect_title),
                    description = stringResource(R.string.fork_cells_collect_desc),
                    checked = config.collect,
                    onCheckedChange = vm::setCollect,
                )
            }
            if (BlockRow.INTERVAL in rows) {
                SettingDivider()
                val intervals = CellsSettings.INTERVALS_SEC
                SettingChipRow(
                    title = stringResource(R.string.fork_cells_interval_title),
                    description = stringResource(R.string.fork_cells_interval_desc),
                    options = intervals.map { stringResource(intervalLabelRes(it)) },
                    selectedIndex = intervals.indexOf(config.intervalSec),
                    onSelect = { vm.setInterval(intervals[it]) },
                )
            }
            if (BlockRow.VIEW in rows) {
                SettingDivider()
                val views = CheckView.entries
                SettingChipRow(
                    title = stringResource(R.string.fork_cells_view_title),
                    options = views.map { stringResource(viewLabelRes(it)) },
                    selectedIndex = views.indexOf(config.view),
                    onSelect = { vm.setView(views[it]) },
                )
            }
            if (BlockRow.STATS in rows) {
                SettingDivider()
                StatsRow(stats, notice, exporting, vm)
            }
        }
    }
}

@Composable
private fun StatsRow(stats: CellStats?, notice: String?, exporting: Boolean, vm: CellsSettingsViewModel) {
    var confirm by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val empty = stats == null || stats.row.snapshots == 0
    val line = if (empty) {
        stringResource(R.string.fork_cells_stats_empty)
    } else {
        val first = stats.row.firstTs?.let { SimpleDateFormat("dd.MM.yyyy", locale).format(Date(it)) }.orEmpty()
        stringResource(
            R.string.fork_cells_stats_value,
            NumberFormat.getIntegerInstance(locale).format(stats.row.snapshots),
            sizeText(stats.bytes, locale),
            first,
        ) + "\n" + stringResource(R.string.fork_cells_stats_measures, stats.row.rest, stats.row.afterCharge, stats.row.load) +
            "\n" + stringResource(R.string.fork_cells_stats_note)
    }
    SettingActionRow(
        title = stringResource(R.string.fork_cells_stats_title),
        description = line,
        buttonLabel = stringResource(R.string.fork_cells_export),
        onClick = vm::exportCsv,
        enabled = !empty && !exporting,
        secondButtonLabel = stringResource(R.string.fork_cells_clear),
        onSecondClick = { confirm = true },
    )
    if (notice != null) SettingHint(text = notice)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.fork_cells_clear_title), color = TextPrimary) },
            text = {
                val size = stats?.let { sizeText(it.bytes, locale) } ?: sizeText(0L, locale)
                Text(stringResource(R.string.fork_cells_clear_text, size), color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.clear() }) { Text(stringResource(R.string.fork_cells_clear_confirm), color = AccentGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.fork_cells_clear_cancel), color = TextSecondary) }
            },
        )
    }
}
