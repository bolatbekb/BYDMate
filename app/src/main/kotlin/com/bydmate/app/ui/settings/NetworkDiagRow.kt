package com.bydmate.app.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.fork.net.NetworkDiagViewModel
import com.bydmate.app.ui.components.AppAlertDialog
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.skin.TextButton
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.TextPrimary
import com.bydmate.app.ui.theme.TextSecondary

/**
 * «Диагностика сети» in Settings › «Сервис и данные»: one button that gathers Wi-Fi/network/ping/
 * download-speed into a report shown in a scrollable dialog and saveable via the share sheet. fork-local.
 */
@Composable
internal fun NetworkDiagRow(vm: NetworkDiagViewModel = hiltViewModel()) {
    val running by vm.running.collectAsStateWithLifecycle()
    val report by vm.report.collectAsStateWithLifecycle()
    SettingActionRow(
        title = stringResource(R.string.fork_netdiag_title),
        description = stringResource(R.string.fork_netdiag_desc),
        buttonLabel = stringResource(if (running) R.string.fork_netdiag_running else R.string.fork_netdiag_run),
        onClick = { vm.run(true) },
        enabled = !running,
    )
    report?.let { text ->
        AppAlertDialog(
            onDismissRequest = vm::dismiss,
            containerColor = CardSurface,
            title = { Text(stringResource(R.string.fork_netdiag_title), color = TextPrimary) },
            text = {
                Text(
                    text,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = vm::share) { Text(stringResource(R.string.fork_netdiag_share), color = AccentGreen) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.fork_netdiag_close), color = TextSecondary) }
            },
        )
    }
}
