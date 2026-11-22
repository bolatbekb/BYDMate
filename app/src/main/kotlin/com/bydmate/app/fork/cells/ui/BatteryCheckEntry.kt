package com.bydmate.app.fork.cells.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.TextSecondary

// The entries of the battery check on «Машина»: the row under «Прошивки блоков», the click on the
// «Ячейки» section and the window host. Each is one marked line in an author file. fork-local.

/** «Проверка батареи · ● всё отлично · 4 мВ · 22.11 ›» like the firmware row; nothing without a car. */
@Composable
fun BatteryCheckRow(vm: BatteryCheckViewModel = hiltViewModel()) {
    val row by vm.entry.collectAsStateWithLifecycle()
    if (row.kind == EntryKind.HIDDEN) return
    Text(
        entryText(row),
        color = TextSecondary,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = tnum(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { vm.openWindow() }
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

/** The click of the «Ячейки» section: nothing without a car (the section stays as it was). */
@Composable
fun batteryCheckClick(vm: BatteryCheckViewModel = hiltViewModel()): Modifier {
    val config by vm.config.collectAsStateWithLifecycle()
    if (config.profile == CarProfile.NONE) return Modifier
    val label = stringResource(R.string.fork_cells_title)
    return Modifier.clickable(onClickLabel = label) { vm.openWindow() }
}

/** The window while it is open. Leaving «Машина» closes it, so it never listens behind the driver's back. */
@Composable
fun BatteryCheckHost(vm: BatteryCheckViewModel = hiltViewModel()) {
    val open by vm.open.collectAsStateWithLifecycle()
    DisposableEffect(vm) { onDispose { vm.closeWindow() } }
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
    BatteryCheckWindow(ui, actions) { VerdictBody(it) }
}

@Composable
private fun entryText(row: EntryRow): AnnotatedString {
    val title = stringResource(R.string.fork_cells_title)
    val status = entryStatusText(row)?.text()
    val level = row.level
    val levelText = level?.let { stringResource(levelRes(it)) }
    val prelim = stringResource(R.string.fork_cells_level_prelim)
    val spread = row.spreadMv?.let { TextRes(R.string.fork_cells_val_mv, it).text() }
    val problem = row.kind == EntryKind.MISMATCH || row.kind == EntryKind.NO_STREAM
    return buildAnnotatedString {
        append(title)
        append(" · ")
        if (status != null) {
            if (problem) withStyle(SpanStyle(color = AccentOrange)) { append(status) } else append(status)
        } else {
            withStyle(SpanStyle(color = levelColor(level))) { append("● ") }
            append(levelText.orEmpty())
            if (row.preliminary && level != null && level != Level.NONE) append(" · $prelim")
            spread?.let { append(" · $it") }
            row.dateMs?.let { append(" · ${dayMonth(it)}") }
        }
        append(" ›")
    }
}
