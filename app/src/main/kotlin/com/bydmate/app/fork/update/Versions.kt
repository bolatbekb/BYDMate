package com.bydmate.app.fork.update

/**
 * Numeric version comparison, the same rule the author's `UpdateChecker.isNewer` and
 * `UpdateApkCleanup` use: dotted ints, zero-padded, so 3.9 < 3.10 and 3.17 == 3.17.0. Kept in the
 * fork so the background updater does not reach into the author's private method. fork-local.
 */
object Versions {

    /** Numeric head of a version string: "4.5.0.4-test" → [4, 5, 0, 4]; null when it has none. */
    fun parse(version: String): List<Int>? {
        val head = Regex("^v?(\\d+(?:\\.\\d+)*)").find(version)?.groupValues?.get(1) ?: return null
        return head.split('.').map { it.toIntOrNull() ?: return null }
    }

    /** True when [remote] is strictly newer than [local]. Unparseable [remote] is never newer. */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return true
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
