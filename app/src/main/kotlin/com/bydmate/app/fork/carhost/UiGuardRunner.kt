package com.bydmate.app.fork.carhost

import com.bydmate.app.data.autoservice.AdbOnDeviceClient
import kotlinx.coroutines.CancellationException

/**
 * Takes [UiSnapshot]s through the on-device ADB. A failed command leaves its fields null/empty,
 * it never throws. The uiautomator XML is read and deleted at once; screenshots stay.
 */
class UiGuardRunner(private val adb: AdbOnDeviceClient) {

    suspend fun snapshot(tag: String, n: Int): UiSnapshot {
        val act = run("dumpsys activity activities | grep -E \"topResumedActivity|mResumedActivity\"")
        val win = run("dumpsys window | grep -E \"mCurrentFocus\"")
        val xml = run("uiautomator dump $UI_XML >/dev/null 2>&1; cat $UI_XML; rm -f $UI_XML")
        try {
            adb.screencap("$DIR/BYDMate-carhost-$n-$tag.png")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // no screenshot: the verdict does not depend on it
        }
        val (pkg, activity, focus) = UiGuard.parseTop(act, win)
        val (ids, texts, nodes) = UiGuard.parseUiDump(xml)
        return UiSnapshot(pkg, activity, focus, ids, texts, nodes)
    }

    private suspend fun run(cmd: String): String = try {
        adb.exec(cmd) ?: ""
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ""
    }

    private companion object {
        const val DIR = "/sdcard/Download"
        const val UI_XML = "$DIR/BYDMate-carhost-ui.xml"
    }
}
