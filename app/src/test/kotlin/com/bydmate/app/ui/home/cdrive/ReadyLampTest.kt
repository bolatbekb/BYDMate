package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadyLampTest {
    @Test fun readyWhenDrive() {
        assertEquals(ReadyShown.READY, ReadyLamp().update(2, 0))
    }

    @Test fun onWhenIgnitionOnly() {
        assertEquals(ReadyShown.ON, ReadyLamp().update(1, 0))
    }

    @Test fun offWhenZeroOrNullAtStart() {
        assertEquals(ReadyShown.OFF, ReadyLamp().update(0, 0))
        assertEquals(ReadyShown.OFF, ReadyLamp().update(null, 0))
    }

    @Test fun shortDipKeepsReady() {
        val l = ReadyLamp()
        assertEquals(ReadyShown.READY, l.update(2, 0))
        assertEquals(ReadyShown.READY, l.update(0, 100))
        assertEquals(ReadyShown.READY, l.update(0, 700))
        assertEquals(ReadyShown.READY, l.update(2, 800))
    }

    @Test fun longDropShowsOff() {
        val l = ReadyLamp()
        l.update(2, 0)
        assertEquals(ReadyShown.READY, l.update(0, 100))
        assertEquals(ReadyShown.READY, l.update(0, 1_000))
        assertEquals(ReadyShown.OFF, l.update(0, 1_100))
    }

    @Test fun readyToOnIsHeldToo() {
        val l = ReadyLamp()
        l.update(2, 0)
        assertEquals(ReadyShown.READY, l.update(1, 100))
        assertEquals(ReadyShown.READY, l.update(1, 900))
        assertEquals(ReadyShown.ON, l.update(1, 1_100))
    }

    @Test fun dipTimerRestartsAfterRecovery() {
        val l = ReadyLamp()
        l.update(2, 0)
        l.update(0, 100)
        l.update(2, 600)
        assertEquals(ReadyShown.READY, l.update(0, 700))
        assertEquals(ReadyShown.READY, l.update(0, 1_500))
        assertEquals(ReadyShown.OFF, l.update(0, 1_700))
    }

    @Test fun riseIsImmediate() {
        val l = ReadyLamp()
        assertEquals(ReadyShown.ON, l.update(1, 0))
        assertEquals(ReadyShown.READY, l.update(2, 10))
    }

    @Test fun singleLowOneSecondSampleNeverShowsOffWithProductionHold() {
        val l = ReadyLamp(holdMs = 2_000)
        l.update(2, 0)
        assertEquals(ReadyShown.READY, l.update(0, 1_000)) // the one low sample
        assertEquals(ReadyShown.READY, l.update(0, 2_000)) // still low at the tick before recovery
        assertEquals(ReadyShown.READY, l.update(2, 2_000))
    }
}
