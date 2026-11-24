package com.bydmate.app.fork.carhost

/**
 * What the UI guard may send through the on-device ADB (probe build): exactly two read-only
 * `dumpsys` lines, matched as whole strings, and screenshots under one file-name pattern.
 * Anything else (another dumpsys, uiautomator, anything appended) stays refused by the barrier.
 */
object CarHostAdbBarrier {
    const val CMD_ACTIVITIES = "dumpsys activity activities | grep -E \"topResumedActivity|mResumedActivity\""
    const val CMD_WINDOW = "dumpsys window | grep -E \"mCurrentFocus\""
    const val SCREEN_DIR = "/sdcard/Download"
    private val SCREEN_PATH = Regex("""^/sdcard/Download/BYDMate-carhost-\d+-[a-z0-9-]+\.png$""")

    fun allowsCommand(cmd: String): Boolean = cmd == CMD_ACTIVITIES || cmd == CMD_WINDOW

    fun allowsScreenPath(path: String): Boolean = SCREEN_PATH.matches(path)
}
