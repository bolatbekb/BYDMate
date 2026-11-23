package com.bydmate.app.fork.cells.helper

import android.os.Parcel
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.GB_STREAM_FID
import com.bydmate.app.fork.cells.stream.hex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The daemon tap: one listener on the GB device for the stream fid only, vendor calls with the
 * tap lock free, and the 2-minute auto-unsubscribe when the app stops calling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CellTapDaemonTest {

    /** Elapsed time the daemon reads; the tests move it by hand. */
    private var now = 0L
    private val lockFree = mutableListOf<Boolean>()
    private val device = FakeGbDevice(onVendorCall = { lockFree += lockIsFree() })

    @Before fun setUp() {
        CellTapDaemon.resetForTest()
        CellTapDaemon.autoTick = false
        CellTapDaemon.clock = { now }
        CellTapDaemon.deviceFactory = { device }
    }

    @After fun tearDown() = CellTapDaemon.resetForTest()

    @Test fun `listen registers one listener on the GB device for the stream fid only`() {
        var asked: String? = null
        CellTapDaemon.deviceFactory = { asked = it; device }

        val reply = CellTapDaemon.listen(true)

        assertEquals(CellTx.REG_OK, reply.registration)
        assertEquals("android.hardware.bydauto.gb.BYDAutoGBDevice", asked)
        assertEquals(1, device.registerCalls)
        assertEquals(listOf(listOf(GB_STREAM_FID)), device.registeredIds)
        assertTrue(device.listeners.single() is CellGbListener)
        val s = CellTapDaemon.snapshot()
        assertTrue(s.listening)
        assertEquals(CellTx.REG_OK, s.registration)
    }

    @Test fun `vendor calls run with the tap lock free`() {
        CellTapDaemon.deviceFactory = { lockFree += lockIsFree(); device }

        CellTapDaemon.listen(true)
        CellTapDaemon.listen(false)

        // getInstance, registerListener, unregisterListener.
        assertEquals(listOf(true, true, true), lockFree)
    }

    @Test fun `listen twice keeps one registration`() {
        CellTapDaemon.listen(true)
        val again = CellTapDaemon.listen(true)

        assertEquals(1, device.registerCalls)
        assertEquals(CellTx.REG_OK, again.registration)
        assertEquals(1, device.listeners.size)
    }

    @Test fun `watchdog unregisters after two minutes without app contact`() {
        now = 0; CellTapDaemon.listen(true)
        now = 119_999; CellTapDaemon.tick()
        assertEquals(0, device.unregisterCalls)

        now = 120_001; CellTapDaemon.tick()
        assertEquals(1, device.unregisterCalls)
        assertTrue(device.listeners.isEmpty())
        assertFalse(CellTapDaemon.snapshot().listening)
    }

    @Test fun `snapshot counts as contact`() {
        now = 0; CellTapDaemon.listen(true)
        now = 100_000; CellTapDaemon.snapshot()
        now = 200_000; CellTapDaemon.tick()
        assertEquals(0, device.unregisterCalls)
        assertTrue(CellTapDaemon.snapshot().listening)
    }

    @Test fun `listen off unregisters`() {
        CellTapDaemon.listen(true)
        val off = CellTapDaemon.listen(false)

        assertEquals(CellTx.REG_OFF, off.registration)
        assertEquals(1, device.unregisterCalls)
        assertTrue(device.listeners.isEmpty())
        assertFalse(CellTapDaemon.snapshot().listening)
        // Nothing left to undo: a second off makes no vendor call.
        CellTapDaemon.listen(false)
        assertEquals(1, device.unregisterCalls)
    }

    @Test fun `events reach the assembler`() {
        now = 5_000
        CellTapDaemon.listen(true)
        device.fire(GB_STREAM_FID, hex("0000043D000200045D6D37CE0CCE0CCE0C46"))

        val s = CellTapDaemon.snapshot()
        assertEquals(3278, s.cellMv[165])
        assertEquals(1L, s.frames)
        assertEquals(5_000L, s.listeningSinceMs)
    }

    @Test fun `a failing device factory reports REG_FAILED and never throws`() {
        CellTapDaemon.deviceFactory = { throw ClassNotFoundException("android.hardware.bydauto.gb.BYDAutoGBDevice") }

        val reply = CellTapDaemon.listen(true)

        assertEquals(CellTx.REG_FAILED, reply.registration)
        assertTrue(reply.detail, reply.detail.contains("ClassNotFoundException"))
        val s = CellTapDaemon.snapshot()
        assertFalse(s.listening)
        assertEquals(CellTx.REG_FAILED, s.registration)
    }

    @Test fun `a missing listener class reports REG_FAILED and never throws`() {
        CellTapDaemon.listenerFactory = { throw NoClassDefFoundError("android/hardware/bydauto/gb/AbsBYDAutoGBListener") }

        val reply = CellTapDaemon.listen(true)

        assertEquals(CellTx.REG_FAILED, reply.registration)
        assertTrue(reply.detail, reply.detail.contains("NoClassDefFoundError"))
        assertEquals(0, device.registerCalls)
    }

    @Test fun `a fid outside the feature map is REG_NOT_IN_MAP and not listening`() {
        val refusing = FakeGbDevice(known = emptySet())
        CellTapDaemon.deviceFactory = { refusing }

        val reply = CellTapDaemon.listen(true)

        assertEquals(CellTx.REG_NOT_IN_MAP, reply.registration)
        assertFalse(CellTapDaemon.snapshot().listening)
        assertEquals(CellTx.REG_NOT_IN_MAP, CellTapDaemon.snapshot().registration)
    }

    @Test fun `unknown fork code answers STATUS_UNKNOWN_CODE`() {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        assertTrue(CellTapDaemon.onTransact(0x00F0C00F, data, reply))
        reply.setDataPosition(0)
        assertEquals(CellTx.STATUS_UNKNOWN_CODE, reply.readInt())
        data.recycle(); reply.recycle()
    }

    @Test fun `the watchdog thread runs while listening`() {
        CellTapDaemon.autoTick = true
        CellTapDaemon.listen(true)

        assertTrue(Thread.getAllStackTraces().keys.any { it.name == "cells-watchdog" && it.isDaemon && it.isAlive })
    }

    /** True only when a second thread can take the tap lock right now. */
    private fun lockIsFree(): Boolean {
        var acquired = false
        val probe = Thread { synchronized(CellTapDaemon.lock) { acquired = true } }
        probe.start()
        probe.join(LOCK_PROBE_MS)
        return acquired
    }

    private companion object {
        const val LOCK_PROBE_MS = 1000L
    }
}
