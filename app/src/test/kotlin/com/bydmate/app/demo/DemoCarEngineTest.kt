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

    @Test fun fuelCounterGrowsOnlyWhileEngineRuns() {
        val t0 = 1_800_000_000_000L
        // One snapshot per second over five minutes: the engine is on for 10 s, off for 10 s, ...
        val snaps = (0..300).map { DemoCar.snapshot(t0 + it * 1_000L) }
        for (i in 1 until snaps.size) {
            val prev = snaps[i - 1]
            val cur = snaps[i]
            if (on(cur.engineRpm)) {
                assertTrue(cur.fuelTotalL!! > prev.fuelTotalL!!)
            } else {
                assertEquals(prev.fuelTotalL!!, cur.fuelTotalL!!, 0.0)
            }
            assertTrue(cur.mileageHevKm!! >= prev.mileageHevKm!!)
            assertTrue(cur.mileageEvKm!! >= prev.mileageEvKm!!)
        }
        assertTrue(snaps.last().mileageEvKm!! > snaps.first().mileageEvKm!!)
        assertTrue(snaps.last().mileageHevKm!! > snaps.first().mileageHevKm!!)
    }

    @Test fun parkingPhaseStandsStillAndDrawsClimate() {
        val t0 = 1_800_000_000_000L // a multiple of the 12 min cycle
        DemoCar.snapshot(t0 + 9 * 60_000L)
        val driving = DemoCar.snapshot(t0 + 10 * 60_000L - 1_000L)
        assertEquals(4, driving.gear)
        val start = DemoCar.snapshot(t0 + 10 * 60_000L + 1_000L)
        val end = DemoCar.snapshot(t0 + 12 * 60_000L - 1_000L)
        for (s in listOf(start, end)) {
            assertEquals(1, s.gear)
            assertEquals(0, s.speed)
            assertEquals(0, s.engineRpm)
        }
        assertEquals(start.mileage!!, end.mileage!!, 0.0)
        assertEquals(start.fuelTotalL!!, end.fuelTotalL!!, 0.0)
        assertTrue(end.totalElecConsumption!! > start.totalElecConsumption!!)
        assertEquals(4, DemoCar.snapshot(t0 + 12 * 60_000L + 1_000L).gear)
    }
}
