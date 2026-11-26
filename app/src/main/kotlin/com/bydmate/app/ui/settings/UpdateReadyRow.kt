package com.bydmate.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.fork.update.BackgroundUpdateEntryPoint
import com.bydmate.app.fork.update.ReadyApk
import com.bydmate.app.ui.skin.Text
import com.bydmate.app.ui.theme.TextSecondary
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings status of the silent background update: when a newer APK has already been downloaded
 * (by [com.bydmate.app.fork.update.BackgroundUpdateStartup] at process start, or the window gate),
 * shows it with an «Установить» button that installs the cached file at once — no waiting for a
 * download and no park gate, since the driver tapped it deliberately. Otherwise a muted line so the
 * state is visible either way. Reads the store off the main thread; re-reads whenever the settings
 * screen re-enters composition. fork-local.
 */
@Composable
internal fun UpdateReadyRow() {
    val context = LocalContext.current
    val manager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BackgroundUpdateEntryPoint::class.java,
        ).backgroundUpdateManager()
    }
    val ready by produceState<ReadyApk?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { manager.readyUpdate() }
    }
    ready?.let { apk ->
        SettingActionRow(
            title = stringResource(R.string.fork_update_ready_title),
            description = stringResource(R.string.fork_update_ready_desc, apk.version),
            buttonLabel = stringResource(R.string.fork_update_ready_install),
            onClick = { manager.install(apk) },
        )
    } ?: Text(
        stringResource(R.string.fork_update_none),
        color = TextSecondary,
        fontSize = 13.sp,
    )
}
