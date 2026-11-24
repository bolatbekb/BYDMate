package com.bydmate.app.fork.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bydmate.app.R
import com.bydmate.app.service.UpdateChecker
import com.bydmate.app.ui.overlay.OverlayNotificationManager
import dagger.hilt.android.EntryPointAccessors

/**
 * The fork's app-open update gate, called once from `AppNavigation`. Replaces the author's inline
 * «download + install» dialog: the APK is downloaded silently in the background, and at open we
 * either offer to install (parked), unobtrusively notify (moving), or stay silent. The install
 * itself runs only from the prompt, i.e. only when parked. fork-local.
 */
@Composable
fun ForkUpdateGate() {
    val context = LocalContext.current
    val manager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BackgroundUpdateEntryPoint::class.java,
        ).backgroundUpdateManager()
    }

    val prompt = remember { mutableStateOf<ReadyApk?>(null) }
    val promptDismissed = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!UpdateChecker.isAutoCheckEnabled(context)) return@LaunchedEffect

        suspend fun applyDecision() {
            val decision = manager.decide()
            when (decision.offer) {
                UpdateOffer.OFFER ->
                    if (!promptDismissed.value && prompt.value == null) prompt.value = decision.ready
                UpdateOffer.NOTIFY -> {
                    val version = decision.ready?.version
                    if (version != null && manager.shouldNotifyThisLaunch()) {
                        OverlayNotificationManager.show(
                            context,
                            context.getString(R.string.fork_update_notify_title, version),
                            context.getString(R.string.fork_update_notify_text),
                        )
                    }
                }
                UpdateOffer.SILENT -> Unit
            }
        }

        applyDecision() // a ready APK left by an earlier session
        runCatching { manager.refresh() } // silent background download of a newer release, over any network
        applyDecision() // in case that download just finished this session
    }

    prompt.value?.let { ready ->
        ForkInstallPrompt(
            version = ready.version,
            onInstall = {
                manager.install(ready)
                prompt.value = null
                promptDismissed.value = true
            },
            onDismiss = {
                prompt.value = null
                promptDismissed.value = true
            },
        )
    }
}
