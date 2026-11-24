package com.bydmate.app.fork.carhost

import com.bydmate.app.data.autoservice.AdbOnDeviceClient
import kotlinx.coroutines.CancellationException

/**
 * Takes [UiSnapshot]s through the on-device ADB: the two whitelisted read-only dumpsys lines
 * ([CarHostAdbBarrier]) and a screenshot that stays in Download. No uiautomator: it can pause
 * accessibility services (BYDMate's own HUD reader among them). A failed command leaves its
 * fields null; it never throws. The texts come from the caller (the target's notifications).
 */
class UiGuardRunner(private val adb: AdbOnDeviceClient) {

    suspend fun snapshot(tag: String, n: Int, texts: List<String>): UiSnapshot {
        val connected = try {
            adb.connect().isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!connected) return UiSnapshot(null, null, null, texts)
        val act = run(CarHostAdbBarrier.CMD_ACTIVITIES)
        val win = run(CarHostAdbBarrier.CMD_WINDOW)
        try {
            adb.screencap("${CarHostAdbBarrier.SCREEN_DIR}/BYDMate-carhost-$n-$tag.png")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // no screenshot: the verdict does not depend on it
        }
        val (pkg, activity, focus) = UiGuard.parseTop(act, win)
        return UiSnapshot(pkg, activity, focus, texts)
    }

    private suspend fun run(cmd: String): String = try {
        adb.exec(cmd) ?: ""
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ""
    }
}

/** S0 with one retry: a read that found nothing in front is taken again after [wait]. */
internal suspend fun readBefore(take: suspend (String) -> UiSnapshot, wait: suspend () -> Unit): UiSnapshot {
    val first = take("before")
    if (!UiGuard.empty(first)) return first
    wait()
    return take("before-retry")
}
