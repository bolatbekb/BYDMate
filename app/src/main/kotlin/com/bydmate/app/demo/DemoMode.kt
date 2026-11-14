package com.bydmate.app.demo

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * DEMO mode: the app runs against a simulated car and a separate seeded database, so every
 * screen can be looked at on an emulator. The real database and settings are never touched.
 *
 * The flag is read once per process: data sources and the database are singletons, so a
 * switch saves the flag and restarts the process. Fork-local.
 */
object DemoMode {
    private const val PREFS = "bydmate_demo"
    private const val KEY_ON = "demo_on"

    /** The demo database file, next to the real one (bydmate.db). */
    const val DB_FILE = "bydmate_demo.db"

    @Volatile private var cached: Boolean? = null

    fun isOn(context: Context): Boolean = cached ?: runCatching {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
    }.getOrDefault(false).also { cached = it }

    /** Database file for this process: the demo one when demo is on. */
    fun dbFile(context: Context, realFile: String): String = if (isOn(context)) DB_FILE else realFile

    /** Saves the flag and restarts the app into the other mode. */
    fun setAndRestart(context: Context, on: Boolean) {
        val app = context.applicationContext
        // commit(), not apply(): the process is killed right after.
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).commit()
        val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (launch != null) app.startActivity(launch)
        exitProcess(0)
    }
}
