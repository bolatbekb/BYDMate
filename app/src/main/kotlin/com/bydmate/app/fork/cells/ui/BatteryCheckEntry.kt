package com.bydmate.app.fork.cells.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.cells.ui.views.ChosenView
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.tech.DialogButton
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.TextSecondary

// The entry of the battery check on «Машина»: the button «Диагностика ВВБ» in the «Лимиты BMS» card and the window host.
// Each is one marked line in an author file. fork-local.

/**
 * «Диагностика ВВБ» at the bottom of the «Лимиты BMS» card, the button of the author's «Диагностика» card in look, and under
 * it the latest result in one line («● всё отлично · 4 мВ · 23.11») or what keeps it from one. Nothing without a car. Only
 * the level (or the status) gives way when the line is short: the spread and the date always show. A preliminary result has
 * a hollow dot.
 */
@Composable
fun BatteryDiagButton(vm: BatteryCheckViewModel = hiltViewModel()) {
    val row by vm.entry.collectAsStateWithLifecycle()
    if (row.kind == EntryKind.HIDDEN) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        DialogButton(
            text = stringResource(R.string.fork_cells_btn_diag),
            icon = { Icon(Icons.Outlined.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(20.dp)) },
            enabled = true,
            accent = false,
            onClick = vm::openWindow,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { EntryLine(row) }
    }
}

/**
 * The window while it is open. Leaving «Машина» closes it, so it never listens behind the driver's back; a
 * configuration change (the head unit's automatic day/night theme) keeps it: the view model outlives the activity.
 */
@Composable
fun BatteryCheckHost(vm: BatteryCheckViewModel = hiltViewModel()) {
    val open by vm.open.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    DisposableEffect(vm, activity) {
        onDispose { if (activity?.isChangingConfigurations != true) vm.closeWindow() }
    }
    if (open) OpenWindow(vm)
}

@Composable
private fun OpenWindow(vm: BatteryCheckViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // The app in the background: the window stays, the listening pauses.
    LifecycleStartEffect(vm) {
        vm.setVisible(true)
        onStopOrDispose { vm.setVisible(false) }
    }
    val actions = remember(vm) {
        BatteryCheckActions(
            select = vm::select,
            takeManual = vm::takeManual,
            enableCollection = vm::enableCollection,
            details = vm::showDetails,
            help = vm::showHelp,
            dismiss = { if (vm.ui.value.details != null) vm.showDetails(null) else vm.closeWindow() },
        )
    }
    BatteryCheckWindow(ui, actions) { ChosenView(it) }
}

@Composable
private fun RowScope.EntryLine(row: EntryRow) {
    val size = 12.sp
    val status = entryStatusText(row)?.text()
    if (status != null) {
        val problem = row.kind == EntryKind.UNSUPPORTED || row.kind == EntryKind.NO_STREAM
        Text(
            status,
            color = if (problem) AccentOrange else TextSecondary,
            fontSize = size,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        return
    }
    EntryDot(row.level, row.preliminary)
    Spacer(Modifier.width(6.dp))
    Text(
        row.level?.let { stringResource(levelRes(it)) }.orEmpty(),
        color = TextSecondary,
        fontSize = size,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
    val tail = listOfNotNull(row.spreadMv?.let { TextRes(R.string.fork_cells_val_mv, it).text() }, row.dateMs?.let { dayMonth(it) })
    if (tail.isNotEmpty()) Text(tail.joinToString(" · ", prefix = " · "), color = TextSecondary, fontSize = size, maxLines = 1, softWrap = false, style = tnum())
}

/** The level's dot; hollow while the result is preliminary, as in the history. */
@Composable
private fun EntryDot(level: Level?, preliminary: Boolean) {
    val shape = RoundedCornerShape(50)
    val color = levelColor(level)
    val hollow = preliminary && level != null && level != Level.NONE
    Box(Modifier.size(8.dp).then(if (hollow) Modifier.border(1.5.dp, color, shape) else Modifier.background(color, shape)))
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
