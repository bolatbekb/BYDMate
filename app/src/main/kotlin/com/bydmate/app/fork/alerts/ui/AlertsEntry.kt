package com.bydmate.app.fork.alerts.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.tech.DialogButton
import com.bydmate.app.ui.theme.TextMuted

/**
 * The «Предупреждения» entry on «Машина»: a button under «Диагностика ВВБ» in the «Лимиты BMS» card
 * and, under it, a one-line summary of how many active alerts there are, by level and colour. The
 * window host is the list overlay. Each is one marked line in an author file. fork-local.
 */
@Composable
fun AlertsDiagButton(vm: AlertsViewModel = hiltViewModel()) {
    val alerts by vm.active.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        DialogButton(
            text = stringResource(R.string.fork_alerts_entry_button),
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(20.dp)) },
            enabled = true,
            accent = false,
            onClick = vm::openWindow,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { SummaryLine(alerts) }
    }
}

/**
 * The list while it is open. Leaving «Машина» closes it (it never listens behind the driver's back);
 * a configuration change (the head unit's day/night theme) keeps it — the view model outlives the activity.
 */
@Composable
fun AlertsHost(vm: AlertsViewModel = hiltViewModel()) {
    val open by vm.open.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    DisposableEffect(vm, activity) {
        onDispose { if (activity?.isChangingConfigurations != true) vm.closeWindow() }
    }
    if (open) {
        val alerts by vm.active.collectAsStateWithLifecycle()
        AlertsList(alerts, nowMs = System.currentTimeMillis(), onClose = vm::closeWindow)
    }
}

@Composable
private fun SummaryLine(alerts: List<Alert>) {
    if (alerts.isEmpty()) {
        Text(stringResource(R.string.fork_alerts_entry_none), color = TextMuted, fontSize = 12.sp)
        return
    }
    // One «● N» chip per level present, in the order critical → warning → info.
    Severity.entries.reversed().forEach { level ->
        val count = alerts.count { it.severity == level }
        if (count == 0) return@forEach
        CountChip(severityColor(level), count)
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun CountChip(color: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(count.toString(), color = color, fontSize = 12.sp)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
