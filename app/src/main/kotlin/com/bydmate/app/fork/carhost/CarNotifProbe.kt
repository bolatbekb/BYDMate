package com.bydmate.app.fork.carhost

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.bydmate.app.diagnostics.Trace
import com.bydmate.app.diagnostics.TraceArea
import com.bydmate.app.fork.naviprobe.AsciiText

/**
 * Passive recorder (probe build): one `carhost_notif` line per notification of a discovered
 * navigator, with its car extensions flagged. Reads only; never connects to the app. [enabled] is
 * not persisted, so it is off after every process start.
 */
object CarNotifProbe {
    private const val CAR_EXT = "android.car.EXTENSIONS"
    private const val CARAPP_EXT = "androidx.car.app.EXTENSIONS"
    private const val MIN_GAP_MS = 2_000L
    private const val KEYS_MAX = 120

    @Volatile var enabled: Boolean = false
    @Volatile var targets: Set<String> = emptySet()
    /** Package to the app label shown in the trace; the package itself is never traced. */
    @Volatile var labels: Map<String, String> = emptyMap()

    internal var sink: (String, List<Pair<String, String>>) -> Unit = { name, fields ->
        Trace.event(TraceArea.HUD, name, *fields.toTypedArray())
    }
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private class Last(val title: String, val text: String, val at: Long)
    private val last = HashMap<String, Last>()

    internal fun resetForTest(full: Boolean = false) {
        synchronized(last) { last.clear() }
        if (full) {
            enabled = false
            targets = emptySet()
            labels = emptyMap()
            sink = { name, fields -> Trace.event(TraceArea.HUD, name, *fields.toTypedArray()) }
            clock = { System.currentTimeMillis() }
        }
    }

    fun onPosted(sbn: StatusBarNotification) {
        try {
            if (!enabled) return
            val pkg = sbn.packageName
            if (pkg !in targets) return
            val d = digest(sbn.notification, pkg)
            val title = d.first { it.first == "title" }.second
            val text = d.first { it.first == "text" }.second
            val now = clock()
            synchronized(last) {
                val p = last[pkg]
                if (p != null && p.title == title && p.text == text && now - p.at < MIN_GAP_MS) return
                last[pkg] = Last(title, text, now)
            }
            val label = AsciiText.encode(labels[pkg] ?: "app") ?: "app"
            sink("carhost_notif", listOf("pkg" to label) + d)
        } catch (_: Throwable) {
            // never affects the author's notification handling
        }
    }

    fun digest(n: Notification): List<Pair<String, String>> = digest(n, "")

    private fun digest(n: Notification, pkg: String): List<Pair<String, String>> {
        val ex = n.extras
        val keys = ex.keySet().sorted().joinToString(",")
        fun safe(s: CharSequence?): String =
            (if (pkg.isEmpty()) AsciiText.encode(s) else traceSafe(s?.toString(), pkg)) ?: ""
        return listOf(
            "channel" to safe(n.channelId),
            "category" to safe(n.category),
            "ongoing" to if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) "yes" else "no",
            "keys" to safe(keys.take(KEYS_MAX)),
            "car_ext" to if (ex.containsKey(CAR_EXT)) "yes" else "no",
            "carapp_ext" to if (ex.containsKey(CARAPP_EXT)) "yes" else "no",
            "title" to safe(ex.getCharSequence(Notification.EXTRA_TITLE)),
            "text" to safe(ex.getCharSequence(Notification.EXTRA_TEXT)),
            "actions" to (n.actions?.size ?: 0).toString(),
        )
    }
}
