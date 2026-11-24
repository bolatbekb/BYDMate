package com.bydmate.app.fork.carhost

/** Did the navigator's own screen survive the car-app session (filled in by the UI guard). */
enum class GuardVerdict { OK, CHANGED, BLOCKED, UNKNOWN }

/** What the foreground looked like at one moment: top activity, window focus, uiautomator tree. */
data class UiSnapshot(
    val pkgOnTop: String?,
    val activity: String?,
    val focus: String?,
    val ids: Set<String>,
    val texts: List<String>,
    val nodes: Int,
)

/** Pure parsing and the verdict rules of the UI guard. */
object UiGuard {
    val CAR_WORDS = listOf(
        "android auto", "в автомобиле", "в машине", "экране автомобиля",
        "car screen", "connected to car", "подключен к автомобилю",
    )

    private val NODE = Regex("<node\\b[^>]*>")
    private val ATTR = Regex("""\b(resource-id|text|content-desc)="([^"]*)"""")
    private val COMPONENT = Regex("""u\d+\s+([\w.]+)/([\w.$]+)""")
    private val FOCUS = Regex("""([\w.]+)/([\w.$]+)\}""")

    /** Resource ids, texts (text and content-desc, non-empty) and node count of a uiautomator dump. */
    fun parseUiDump(xml: String): Triple<Set<String>, List<String>, Int> {
        val ids = LinkedHashSet<String>()
        val texts = ArrayList<String>()
        var nodes = 0
        for (m in NODE.findAll(xml)) {
            nodes++
            for (a in ATTR.findAll(m.value)) {
                val v = unescape(a.groupValues[2])
                if (v.isEmpty()) continue
                if (a.groupValues[1] == "resource-id") ids.add(v) else texts.add(v)
            }
        }
        return Triple(ids, texts, nodes)
    }

    private fun unescape(s: String) = s.replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

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

    fun verdict(before: UiSnapshot, after: UiSnapshot, targetPkg: String): Pair<GuardVerdict, String> {
        if (before.pkgOnTop != targetPkg) return GuardVerdict.UNKNOWN to "not_in_front_before"
        if (after.pkgOnTop != targetPkg) return GuardVerdict.BLOCKED to "left_front"
        if (after.activity != before.activity) return GuardVerdict.BLOCKED to "activity_changed"
        val had = before.texts.map { it.lowercase() }
        val carWords = after.texts.map { it.lowercase() }.any { t ->
            CAR_WORDS.any { w -> w in t && had.none { w in it } }
        }
        if (carWords) return GuardVerdict.BLOCKED to "car_words"
        if (after.nodes < before.nodes / 3) return GuardVerdict.BLOCKED to "tree_collapsed"
        val union = (before.ids + after.ids).size
        val jaccard = if (union == 0) 1.0 else before.ids.intersect(after.ids).size.toDouble() / union
        if (jaccard < 0.5) return GuardVerdict.CHANGED to "tree_changed"
        return GuardVerdict.OK to "same"
    }
}
