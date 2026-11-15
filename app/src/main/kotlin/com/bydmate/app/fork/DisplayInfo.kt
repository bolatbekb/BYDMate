package com.bydmate.app.fork

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/**
 * The screen the app runs on, for the diagnostic dump header. On DiLink the car keeps its own
 * status strip on top and the climate bar at the bottom, so the panel size says little: what
 * matters is the window the app really gets, the bars it loses to the car, and the density.
 * The activity reports its window here on every layout; the dump reads the last report.
 */
object DisplayInfo {
    private const val TAG = "BYDMateDisplay"

    @Volatile private var window: String? = null

    /** Called once from BYDMateApp.onCreate: watches every activity's window. */
    fun init(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = watch(activity)
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun watch(activity: Activity) {
        val decor = activity.window.decorView
        decor.viewTreeObserver.addOnGlobalLayoutListener { report(activity, decor) }
    }

    private fun report(activity: Activity, decor: View) {
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val density = activity.resources.displayMetrics.density
        val bars = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.systemBars())
        val line = buildString {
            append("window ${decor.width}x${decor.height} px")
            append(" (${(decor.width / density).roundToInt()}x${(decor.height / density).roundToInt()} dp)")
            if (bars != null) {
                append(", system bars top=${bars.top} bottom=${bars.bottom} left=${bars.left} right=${bars.right} px")
                val w = decor.width - bars.left - bars.right
                val h = decor.height - bars.top - bars.bottom
                append(", usable ${w}x$h px (${(w / density).roundToInt()}x${(h / density).roundToInt()} dp)")
            }
            append(", content view ${content.width}x${content.height} px")
        }
        if (line != window) {
            window = line
            Log.i(TAG, line)
        }
    }

    fun lines(context: Context): List<String> {
        val m = context.resources.displayMetrics
        val cfg = context.resources.configuration
        val out = mutableListOf(
            "display: density ${m.density} (${m.densityDpi} dpi, xdpi=${m.xdpi.roundToInt()} ydpi=${m.ydpi.roundToInt()}), " +
                "config ${cfg.screenWidthDp}x${cfg.screenHeightDp} dp, metrics ${m.widthPixels}x${m.heightPixels} px",
            "display: ${window ?: "window not laid out yet"}",
        )
        context.getSystemService(DisplayManager::class.java)?.displays?.forEach { d ->
            val mode = d.mode
            out += "display#${d.displayId}: ${d.name} ${mode.physicalWidth}x${mode.physicalHeight} px " +
                "@ ${mode.refreshRate.roundToInt()} Hz"
        }
        return out
    }
}
