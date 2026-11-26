package com.bydmate.app.fork.update

import android.content.Context
import android.util.Log
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.UpdateChecker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Kicks off the silent background download at process start, so a new release downloads while the
 * app is only alive in the background (its widget showing) — without waiting for the window to be
 * opened. This is the point of the whole feature: on car-on the process comes up, the widget shows,
 * and the APK is already downloading by the time the driver taps it.
 *
 * Mirrors the window gate ([ForkUpdateGate]) but fires from `BYDMateApp.onCreate`, which runs
 * whenever the process starts (autostart, widget, boot). Both call [BackgroundUpdateManager.refresh],
 * whose download-in-progress guard makes them share one download, never two. Skips demo mode and
 * honours the auto-check toggle, exactly like the gate. fork-local.
 */
object BackgroundUpdateStartup {
    private const val TAG = "BgUpdateStartup"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget: launches the throttled check + silent download off the main thread. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (DemoMode.isOn(app)) return
        if (!UpdateChecker.isAutoCheckEnabled(app)) return
        scope.launch {
            val manager = EntryPointAccessors.fromApplication(
                app,
                BackgroundUpdateEntryPoint::class.java,
            ).backgroundUpdateManager()
            runCatching { manager.refresh() }
                .onFailure { Log.i(TAG, "background update check failed: ${it.message}") }
        }
    }
}
