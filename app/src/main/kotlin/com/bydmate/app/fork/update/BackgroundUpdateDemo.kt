package com.bydmate.app.fork.update

import android.content.Context
import com.bydmate.app.demo.DemoMode
import java.io.File

/**
 * DEMO-only override so the update prompt and the notice can be looked at on an emulator without a
 * real download or a real car. Active only when [DemoMode] is on. The outcome is chosen by a
 * one-word file pushed into the app's external files dir (no root, no author-file changes):
 *
 *   adb push offer.txt /sdcard/Android/data/com.bydmate.app/files/bgupdate_demo.txt   # «offer» | «notify»
 *
 * «offer» → the install prompt (as if parked), «notify» → the «на стоянке» notice (as if moving),
 * anything else / absent → no override, normal behaviour. fork-local. Never used in production.
 */
object BackgroundUpdateDemo {
    private const val FILE = "bgupdate_demo.txt"
    private const val FAKE_VERSION = "9.9.9"

    /** A forced decision in demo mode, or null to let [BackgroundUpdateManager.decide] run normally. */
    fun decision(context: Context): UpdateDecision? {
        if (!DemoMode.isOn(context)) return null
        val mode = runCatching {
            File(context.getExternalFilesDir(null), FILE)
                .takeIf { it.isFile }?.readText()?.trim()?.lowercase()
        }.getOrNull() ?: return null
        val fake = fakeReady(context) ?: return null
        return when (mode) {
            "offer" -> UpdateDecision(UpdateOffer.OFFER, fake)
            "notify" -> UpdateDecision(UpdateOffer.NOTIFY, fake)
            else -> null
        }
    }

    internal fun fakeReady(context: Context): ReadyApk? = runCatching {
        val dir = File(context.cacheDir, UpdateApkStore.SUBDIR).apply { mkdirs() }
        val file = File(dir, "BYDMate-$FAKE_VERSION.apk")
        if (!file.exists()) file.writeBytes(ByteArray(0))
        ReadyApk(FAKE_VERSION, file)
    }.getOrNull()
}
