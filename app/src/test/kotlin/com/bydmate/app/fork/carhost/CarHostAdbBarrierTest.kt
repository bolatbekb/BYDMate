package com.bydmate.app.fork.carhost

import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.autoservice.AdbConnectFailure
import com.bydmate.app.data.autoservice.AdbKeyStore
import com.bydmate.app.data.autoservice.AdbOnDeviceClientImpl
import com.bydmate.app.data.autoservice.AdbProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The two guard reads and the guard screenshots pass the ADB barrier; nothing around them does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarHostAdbBarrierTest {
    private class Fake : AdbProtocol {
        val calls = mutableListOf<String>()
        override fun connect() = true
        override fun exec(cmd: String): String? { calls += cmd; return "OK" }
        override val lastConnectFailure: AdbConnectFailure? = null
        override fun isConnected() = true
        override fun disconnect() = Unit
    }

    private fun client(fake: Fake): AdbOnDeviceClientImpl {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AdbOnDeviceClientImpl(ctx, AdbKeyStore(ctx)).also { it.protocolFactory = { fake } }
    }

    private val refusedCommands = listOf(
        "dumpsys package",
        "dumpsys activity activities",
        "uiautomator dump /sdcard/Download/BYDMate-carhost-ui.xml",
        CarHostAdbBarrier.CMD_ACTIVITIES + "; reboot",
        CarHostAdbBarrier.CMD_ACTIVITIES + " && reboot",
        CarHostAdbBarrier.CMD_WINDOW + "; rm -rf /sdcard",
        " " + CarHostAdbBarrier.CMD_WINDOW,
    )

    private val refusedPaths = listOf(
        "/sdcard/Download/BYDMate-carhost-1-before.png; reboot",
        "/sdcard/Download/BYDMate-carhost-x-before.png",
        "/sdcard/Download/BYDMate-carhost-1-Before.png",
        "/sdcard/Download/../BYDMate-carhost-1-before.png",
        "/data/local/tmp/BYDMate-carhost-1-before.png",
        "/sdcard/Download/BYDMate-carhost-1-before.xml",
    )

    @Test fun `predicate accepts exactly the guard reads`() {
        assertTrue(CarHostAdbBarrier.allowsCommand(CarHostAdbBarrier.CMD_ACTIVITIES))
        assertTrue(CarHostAdbBarrier.allowsCommand(CarHostAdbBarrier.CMD_WINDOW))
        refusedCommands.forEach { assertFalse(it, CarHostAdbBarrier.allowsCommand(it)) }
    }

    @Test fun `predicate accepts guard screenshot paths only`() {
        for (p in listOf("before", "3s", "3s-retry", "after", "before-retry")) {
            assertTrue(p, CarHostAdbBarrier.allowsScreenPath("/sdcard/Download/BYDMate-carhost-12-$p.png"))
        }
        refusedPaths.forEach { assertFalse(it, CarHostAdbBarrier.allowsScreenPath(it)) }
    }

    @Test fun `client exec lets the guard reads through and refuses the rest`() = runTest {
        val fake = Fake()
        val c = client(fake)
        c.connect()
        c.exec(CarHostAdbBarrier.CMD_ACTIVITIES)
        c.exec(CarHostAdbBarrier.CMD_WINDOW)
        assertEquals(listOf(CarHostAdbBarrier.CMD_ACTIVITIES, CarHostAdbBarrier.CMD_WINDOW), fake.calls)
        for (cmd in refusedCommands) {
            try {
                c.exec(cmd)
                fail("must be refused: $cmd")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("write barrier"))
            }
        }
        assertEquals(2, fake.calls.size)
    }

    @Test fun `client screencap takes guard paths and the settings button path only`() = runTest {
        val fake = Fake()
        val c = client(fake)
        assertTrue(c.screencap("/sdcard/Download/BYDMate-carhost-3-10s.png"))
        assertTrue(c.screencap("/sdcard/Download/BYDMate-screen-20261124-101010.png"))
        for (p in refusedPaths) {
            try {
                c.screencap(p)
                fail("must be refused: $p")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("refused path"))
            }
        }
        assertEquals(2, fake.calls.size)
    }
}
