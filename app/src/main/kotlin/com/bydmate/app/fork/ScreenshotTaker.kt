package com.bydmate.app.fork

import android.content.Context
import android.widget.Toast
import com.bydmate.app.R
import com.bydmate.app.data.autoservice.AdbOnDeviceClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Whole-screen screenshot, the car's own status strip and climate bar included: only the shell
 * uid may read the frame buffer, so it goes through the on-device ADB the app already holds.
 * Shot after a short delay so the user can switch to the screen they want; the delay runs in
 * an app-wide scope, so leaving Settings does not cancel it.
 */
object ScreenshotTaker {
    const val DELAY_S = 5
    private const val DIR = "/sdcard/Download"
    val PATH_REGEX = Regex("""^/sdcard/Download/BYDMate-screen-\d{8}-\d{6}\.png$""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun adb(): AdbOnDeviceClient
    }

    fun shoot(context: Context) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        val adb = EntryPointAccessors.fromApplication(app, Deps::class.java).adb()
        toast(app, app.getString(R.string.settings_screenshot_wait, DELAY_S))
        job = scope.launch {
            delay(DELAY_S * 1000L)
            val name = "BYDMate-screen-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.png"
            val ok = adb.screencap("$DIR/$name")
            toast(app, if (ok) app.getString(R.string.settings_screenshot_saved, "Download/$name")
            else app.getString(R.string.settings_screenshot_failed))
        }
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
