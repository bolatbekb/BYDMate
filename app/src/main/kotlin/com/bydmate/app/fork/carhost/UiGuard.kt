package com.bydmate.app.fork.carhost

/** Did the navigator's own screen survive the car-app session (filled in by the UI guard). */
enum class GuardVerdict { OK, CHANGED, BLOCKED, UNKNOWN }

/**
 * What the foreground looked like at one moment: top activity and window focus (two read-only
 * dumpsys lines), and [texts] — the target's notification texts that belong to this moment
 * (before connect: those seen so far in the session; after: those posted since connect).
 */
data class UiSnapshot(
    val pkgOnTop: String?,
    val activity: String?,
    val focus: String?,
    val texts: List<String>,
)

/** Pure parsing and the verdict rules of the UI guard. */
object UiGuard {
    /** Words of a "this app runs in the car" placeholder or notification; no app names. */
    val CAR_WORDS = listOf(
        "android auto", "running in android auto", "в android auto", "в режиме android auto",
        "в автомобиле", "в машине", "экране автомобиля",
        "car screen", "connected to car", "continue on phone", "подключен к автомобилю",
    )

    private val COMPONENT = Regex("""u\d+\s+([\w.]+)/([\w.$]+)""")
    private val FOCUS = Regex("""([\w.]+)/([\w.$]+)\}""")

    private fun fullClass(pkg: String, cls: String) = if (cls.startsWith(".")) pkg + cls else cls

    /** Package on top, its activity (full class name) and the window focus (`pkg/class`). */
    fun parseTop(activities: String, window: String): Triple<String?, String?, String?> {
        val line = activities.lineSequence().firstOrNull { "topResumedActivity" in it }
            ?: activities.lineSequence().firstOrNull { "ResumedActivity" in it }
        val c = line?.let { COMPONENT.find(it) }
        val pkg = c?.groupValues?.get(1)
        val act = c?.let { fullClass(it.groupValues[1], it.groupValues[2]) }
        val f = window.lineSequence().firstOrNull { "mCurrentFocus" in it }?.let { FOCUS.find(it) }
        val focus = f?.let { it.groupValues[1] + "/" + fullClass(it.groupValues[1], it.groupValues[2]) }
        return Triple(pkg, act, focus)
    }

    /** A snapshot with nothing read from the foreground. */
    fun empty(s: UiSnapshot) = s.pkgOnTop == null && s.activity == null

    fun verdict(before: UiSnapshot, after: UiSnapshot, targetPkg: String): Pair<GuardVerdict, String> {
        if (empty(before)) return GuardVerdict.UNKNOWN to "no_reading"
        if (before.pkgOnTop != targetPkg) return GuardVerdict.UNKNOWN to "not_in_front_before"
        if (empty(after)) return GuardVerdict.UNKNOWN to "no_reading"
        if (after.pkgOnTop != targetPkg) return GuardVerdict.BLOCKED to "left_front"
        if (after.activity != before.activity) return GuardVerdict.BLOCKED to "activity_changed"
        val had = before.texts.map { it.lowercase() }
        val carWords = after.texts.map { it.lowercase() }.any { t ->
            CAR_WORDS.any { w -> w in t && had.none { w in it } }
        }
        if (carWords) return GuardVerdict.BLOCKED to "car_words"
        return GuardVerdict.OK to "same"
    }
}
