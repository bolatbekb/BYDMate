package com.bydmate.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.fidscan.FidScan
import com.bydmate.app.fork.fidscan.ListSkip
import com.bydmate.app.fork.fidscan.ListSkipped
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.skin.TextButton
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.CardSurfaceElevated
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// fork: «Запись параметров по списку» (test build, branch feat/fid-scan): one switch under the
// author's fid recorder. The session runs in FidScan; this block only shows its status.

private const val BUDGET_MB = 400

@Composable
internal fun FidScanBlock() {
    val context = LocalContext.current
    val status by FidScan.status.collectAsStateWithLifecycle()
    var help by remember { mutableStateOf(false) }
    if (help) {
        AppAlertDialog(
            onDismissRequest = { help = false },
            containerColor = CardSurface,
            title = { Text(stringResource(R.string.fork_fidscan_title), color = TextPrimary) },
            text = {
                Text(
                    stringResource(R.string.fork_fidscan_help),
                    color = TextSecondary, fontSize = 14.sp, lineHeight = 19.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = { help = false }) {
                    Text(stringResource(R.string.nav_autostart_dialog_button), color = AccentGreen)
                }
            },
        )
    }
    var picker by remember { mutableStateOf<List<File>?>(null) }
    var showSkipped by remember { mutableStateOf(false) }
    val list by FidScan.list.collectAsStateWithLifecycle()
    picker?.let { files ->
        FidScanListPicker(
            files = files,
            onPick = { FidScan.chooseList(context, it); picker = null },
            onDismiss = { picker = null },
        )
    }
    if (showSkipped) {
        list?.let { FidScanSkippedDialog(it.skipped) { showSkipped = false } }
    }
    SettingDivider()
    SettingActionRow(
        title = stringResource(R.string.fork_fidscan_list_title),
        buttonLabel = stringResource(R.string.fork_fidscan_list_button),
        onClick = { picker = FidScan.listCandidates(context) },
        enabled = !status.active,
    )
    list?.let { l ->
        SettingHint(
            text = stringResource(R.string.fork_fidscan_list_summary, l.name, l.lines, l.read, l.skipped.size) +
                " · " + stringResource(
                    if (l.withCatalog) R.string.fork_fidscan_list_checked else R.string.fork_fidscan_list_check_at_start,
                ),
        )
        if (l.skipped.isNotEmpty()) {
            val n = l.skipped.groupingBy { it.reason }.eachCount()
            SettingHint(
                text = stringResource(
                    R.string.fork_fidscan_list_skipped_counts,
                    n[ListSkip.SETTER] ?: 0, n[ListSkip.COMMAND] ?: 0, n[ListSkip.DIAG_DEVICE] ?: 0,
                    n[ListSkip.UNKNOWN_GROUP] ?: 0, n[ListSkip.NOT_IN_CAR] ?: 0, n[ListSkip.BAD_LINE] ?: 0,
                ),
            )
            TextButton(onClick = { showSkipped = true }) {
                Text(stringResource(R.string.fork_fidscan_list_show_skipped), color = AccentGreen)
            }
        }
        l.error?.let { SettingHint(text = it) }
    }
    SettingDivider()
    SettingToggleRow(
        title = stringResource(R.string.fork_fidscan_title),
        traceId = "fid_scan",
        description = stringResource(R.string.fork_fidscan_desc),
        checked = status.active,
        onCheckedChange = { if (it) FidScan.start(context) else FidScan.stop() },
        onHelp = { help = true },
    )
    val file = status.fileName
    if (file != null) {
        SettingHint(
            text = stringResource(
                R.string.fork_fidscan_status,
                file,
                (status.usedBytes / (1024 * 1024)).toInt(),
                BUDGET_MB,
                status.sweeps,
                stringResource(if (status.pushRunning) R.string.fork_fidscan_push_on else R.string.fork_fidscan_push_off),
            ),
        )
    }
    status.error?.let { SettingHint(text = it) }
    SettingDivider()
    SettingActionRow(
        title = stringResource(R.string.fork_fidscan_zip_button),
        buttonLabel = stringResource(R.string.fork_fidscan_zip_label),
        onClick = { FidScan.pack(context) },
        enabled = !status.packing,
    )
    if (status.packing) {
        SettingHint(text = stringResource(R.string.fork_fidscan_zip_packing))
    } else {
        status.lastZip?.let { SettingHint(text = it) }
        status.packError?.let { SettingHint(text = it) }
    }
}

@Composable
private fun FidScanListPicker(files: List<File>, onPick: (File) -> Unit, onDismiss: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fork_fidscan_list_pick_title), color = TextPrimary) },
        text = {
            if (files.isEmpty()) {
                Text(stringResource(R.string.fork_fidscan_list_empty), color = TextSecondary)
            } else {
                val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(files) { file ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(file) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(file.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                            Text(dateFormat.format(Date(file.lastModified())), color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fork_fidscan_list_close), color = TextSecondary)
            }
        },
        containerColor = CardSurfaceElevated,
    )
}

/** Every cancelled line, in file order: «строка N: текст — причина». */
@Composable
private fun FidScanSkippedDialog(skipped: List<ListSkipped>, onDismiss: () -> Unit) {
    val sorted = remember(skipped) { skipped.sortedBy { it.line } }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceElevated,
        title = { Text(stringResource(R.string.fork_fidscan_list_skipped_title), color = TextPrimary) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sorted) { s ->
                    Text(
                        stringResource(R.string.fork_fidscan_list_skipped_line, s.line, s.text, stringResource(reasonRes(s.reason))),
                        color = TextSecondary, fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.fork_fidscan_list_close), color = AccentGreen)
            }
        },
    )
}

private fun reasonRes(r: ListSkip): Int = when (r) {
    ListSkip.SETTER -> R.string.fork_fidscan_skip_setter
    ListSkip.COMMAND -> R.string.fork_fidscan_skip_command
    ListSkip.DIAG_DEVICE -> R.string.fork_fidscan_skip_diag
    ListSkip.UNKNOWN_GROUP -> R.string.fork_fidscan_skip_unknown_group
    ListSkip.NOT_IN_CAR -> R.string.fork_fidscan_skip_not_in_car
    ListSkip.BAD_LINE -> R.string.fork_fidscan_skip_bad_line
}
