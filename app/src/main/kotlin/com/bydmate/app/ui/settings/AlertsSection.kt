package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.alerts.center.SourcePref
import com.bydmate.app.fork.alerts.center.AlertSettings
import com.bydmate.app.fork.alerts.model.AlertBlock
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.ui.ActiveAlerts
import com.bydmate.app.fork.alerts.ui.AlertsSettingsViewModel
import com.bydmate.app.fork.alerts.ui.AlertsViewModel
import com.bydmate.app.fork.alerts.ui.blockLabel
import com.bydmate.app.fork.alerts.ui.severityLabel
import com.bydmate.app.fork.alerts.ui.sourceTitle
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.theme.CardSurfaceElevated

/** Rail icon; lives here so SettingsScreen.kt needs no extra import. fork-local. */
internal val AlertsSectionIcon: ImageVector get() = Icons.Outlined.NotificationsActive

/**
 * Settings › «Предупреждения»: who may send. Sources are grouped by block; each has on/off and a
 * minimum level (send from this level up). Storage carries its free-space threshold. fork-local.
 */
@Composable
internal fun AlertsSection(
    vm: AlertsSettingsViewModel = hiltViewModel(),
    alertsVm: AlertsViewModel = hiltViewModel(),
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val active by alertsVm.active.collectAsStateWithLifecycle()
    SectionHeader(text = stringResource(R.string.fork_alerts_settings_title))
    ActiveAlerts(active, nowMs = System.currentTimeMillis(), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    SettingHint(text = stringResource(R.string.fork_alerts_settings_hint))
    AlertBlock.entries.forEach { block ->
        val ids = AlertSourceId.entries.filter { it.block == block }
        if (ids.isEmpty()) return@forEach
        SettingSubhead(blockLabel(block))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ids.forEachIndexed { index, id ->
                    if (index > 0) SettingDivider()
                    val pref = config.sources[id] ?: SourcePref(true, Severity.INFO)
                    SourceRows(vm, id, pref, config.storageWarnPercent)
                }
            }
        }
    }
}

@Composable
private fun SourceRows(vm: AlertsSettingsViewModel, id: AlertSourceId, pref: SourcePref, storageWarn: Int) {
    SettingToggleRow(
        title = sourceTitle(id),
        checked = pref.enabled,
        onCheckedChange = { vm.setEnabled(id, it) },
    )
    val levels = Severity.entries
    SettingChipRow(
        title = stringResource(R.string.fork_alerts_min_severity),
        options = levels.map { severityLabel(it) },
        selectedIndex = levels.indexOf(pref.minSeverity),
        onSelect = { vm.setMinSeverity(id, levels[it]) },
        enabled = pref.enabled,
    )
    if (id == AlertSourceId.STORAGE_FREE) {
        SettingSliderRow(
            title = stringResource(R.string.fork_alerts_storage_threshold),
            value = storageWarn.toFloat(),
            onValueChange = { vm.setStorageWarnPercent(it.toInt()) },
            valueRange = AlertSettings.STORAGE_WARN_MIN.toFloat()..AlertSettings.STORAGE_WARN_MAX.toFloat(),
            valueLabel = stringResource(R.string.fork_alerts_percent, storageWarn),
            steps = AlertSettings.STORAGE_WARN_MAX - AlertSettings.STORAGE_WARN_MIN - 1,
            enabled = pref.enabled,
        )
    }
}
