package com.bydmate.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCarEngineTest {
    private fun on(rpm: Int?) = (rpm ?: 0) > 0

    @Test fun engineAlternatesEveryTenSeconds() {
        val t0 = 1_800_000_000_000L
        for (k in 0 until 6) {
            val a = DemoCar.snapshot(t0 + k * 10_000L)
            val b = DemoCar.snapshot(t0 + (k + 1) * 10_000L)
            assertNotEquals(on(a.engineRpm), on(b.engineRpm))
        }
    }

    @Test fun runningEngineIsHevAndInRange() {
        val t0 = 1_800_000_000_000L
        for (k in 0 until 20) {
            val s = DemoCar.snapshot(t0 + k * 3_000L)
            if (on(s.engineRpm)) {
                assertTrue(s.engineRpm!! in 1_150..1_450)
                assertEquals(3, s.workMode)
            } else {
                assertEquals(0, s.engineRpm)
                assertEquals(1, s.workMode)
            }
        }
    }
}
