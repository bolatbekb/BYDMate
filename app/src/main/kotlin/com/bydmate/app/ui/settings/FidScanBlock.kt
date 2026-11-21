package com.bydmate.app.ui.settings

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.fidscan.FidScan
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.skin.TextButton
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

// fork: «Запись всех параметров машины» (test build, branch feat/fid-scan): one switch under the
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
}
