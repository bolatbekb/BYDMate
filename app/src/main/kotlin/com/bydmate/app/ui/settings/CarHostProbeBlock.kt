package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.carhost.CarAppTarget
import com.bydmate.app.fork.carhost.CarAppTargets
import com.bydmate.app.fork.carhost.CarHostProbe
import com.bydmate.app.fork.carhost.CarNotifProbe
import com.bydmate.app.fork.carhost.GuardVerdict
import com.bydmate.app.fork.carhost.HostState
import com.bydmate.app.fork.carhost.ProbeRow
import com.bydmate.app.ui.skin.Button
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.Checkbox
import com.bydmate.app.ui.skin.OutlinedButton
import com.bydmate.app.ui.skin.OutlinedTextField
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings › System: Android Auto host probe (test build only). fork-local. */
@Composable
internal fun CarHostProbeBlock() {
    val context = LocalContext.current
    val rows by CarHostProbe.states.collectAsStateWithLifecycle()
    var targets by remember { mutableStateOf<List<CarAppTarget>?>(null) }
    var notif by remember { mutableStateOf(CarNotifProbe.enabled) }
    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) { CarAppTargets.discover(context.packageManager) }
        CarNotifProbe.targets = found.map { it.pkg }.toSet()
        CarNotifProbe.labels = found.associate { it.pkg to it.label }
        CarHostProbe.rememberTargets(found.map { it.pkg })
        targets = found
    }
    SectionHeader(text = stringResource(R.string.fork_carhost_title))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.fork_carhost_hint), color = TextSecondary, fontSize = 13.sp)
            SettingDivider()
            SettingToggleRow(
                title = stringResource(R.string.fork_carhost_notif),
                checked = notif,
                onCheckedChange = { notif = it; CarNotifProbe.enabled = it },
            )
            SettingActionRow(
                title = stringResource(R.string.fork_carhost_stop_all),
                buttonLabel = stringResource(R.string.fork_carhost_stop_all),
                onClick = {
                    CarHostProbe.stopAll()
                    CarNotifProbe.enabled = false
                    notif = false
                },
            )
            val list = targets
            if (list != null && list.isEmpty()) {
                SettingDivider()
                Text(stringResource(R.string.fork_carhost_none), color = TextSecondary, fontSize = 14.sp)
            }
            list?.forEach { t ->
                SettingDivider()
                CarHostTargetRow(t, rows[t.pkg])
            }
        }
    }
}

@Composable
private fun CarHostTargetRow(target: CarAppTarget, row: ProbeRow?) {
    val context = LocalContext.current
    var surface by remember(target.pkg) { mutableStateOf(false) }
    var keepOnBlock by remember(target.pkg) { mutableStateOf(false) }
    var query by remember(target.pkg) { mutableStateOf("") }
    val state = row?.state
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(target.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(statusText(state, row?.failReason), color = TextSecondary, fontSize = 13.sp)
        row?.countdown?.let {
            Text(stringResource(R.string.fork_carhost_countdown, it), color = AccentOrange, fontSize = 13.sp)
        }
        if (row != null) {
            Text(
                stringResource(R.string.fork_carhost_guard, guardText(row.guard, row.keptOnBlock)),
                color = if (row.guard == GuardVerdict.BLOCKED) AccentOrange else TextSecondary,
                fontSize = 13.sp,
            )
        }
        row?.lastTrip?.let {
            Text(stringResource(R.string.fork_carhost_trip, it), color = TextSecondary, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = surface, onCheckedChange = { surface = it })
            Text(stringResource(R.string.fork_carhost_surface), color = TextPrimary, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = keepOnBlock, onCheckedChange = { keepOnBlock = it })
            Text(stringResource(R.string.fork_carhost_keep_on_block), color = TextPrimary, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { CarHostProbe.connect(context, target, surface, keepOnBlock) }) {
                Text(stringResource(R.string.fork_carhost_connect))
            }
            OutlinedButton(onClick = { CarHostProbe.stop(target.pkg) }) {
                Text(stringResource(R.string.fork_carhost_disconnect))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.fork_carhost_route_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { CarHostProbe.navigate(target.pkg, query.trim()) },
                enabled = state == HostState.RUNNING && query.isNotBlank(),
            ) { Text(stringResource(R.string.fork_carhost_route)) }
        }
    }
}

@Composable
private fun statusText(state: HostState?, reason: String?): String {
    if (state == HostState.FAILED && reason != null) return stringResource(R.string.fork_carhost_st_failed, reason)
    return stringResource(
    when (state) {
        null -> R.string.fork_carhost_st_none
        HostState.IDLE, HostState.BINDING, HostState.HANDSHAKE, HostState.CREATING -> R.string.fork_carhost_st_connecting
        HostState.RUNNING -> R.string.fork_carhost_st_ok
        HostState.FAILED -> R.string.fork_carhost_st_failed_plain
        HostState.DISCONNECTED -> R.string.fork_carhost_st_off
    }
    )
}

@Composable
private fun guardText(g: GuardVerdict?, kept: Boolean): String = stringResource(
    when (g) {
        GuardVerdict.OK -> R.string.fork_carhost_g_ok
        GuardVerdict.CHANGED -> R.string.fork_carhost_g_changed
        GuardVerdict.BLOCKED -> if (kept) R.string.fork_carhost_g_blocked_kept else R.string.fork_carhost_g_blocked
        GuardVerdict.UNKNOWN, null -> R.string.fork_carhost_g_unknown
    }
)
