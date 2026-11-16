package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegenTest {
    private fun p(ts: Long, speed: Double?, w: Double?) =
        TripPointEntity(tripId = 1, timestamp = ts, lat = 0.0, lon = 0.0, speedKmh = speed, batteryPowerW = w)

    @Test fun sumsNegativePowerWhileMoving() {
        val r = Regen.kwh(listOf(p(0, 50.0, -36_000.0), p(10_000, 50.0, 0.0)))
        assertEquals(0.1, r!!, 1e-9)
    }

    @Test fun gapsAreCappedAndStandstillIgnored() {
        val pts = listOf(
            p(0, 50.0, -36_000.0),       // gap 60 s -> capped to 10 s = 0.1 kWh
            p(60_000, 0.0, -36_000.0),   // standing still -> nothing
            p(70_000, 50.0, 5_000.0),    // positive power -> nothing
            p(80_000, 50.0, 0.0),
        )
        assertEquals(0.1, Regen.kwh(pts)!!, 1e-9)
    }

    @Test fun unsortedPointsAreSorted() {
        val r = Regen.kwh(listOf(p(10_000, 50.0, 0.0), p(0, 50.0, -36_000.0)))
        assertEquals(0.1, r!!, 1e-9)
    }

    @Test fun noPowerDataIsNull() {
        assertNull(Regen.kwh(listOf(p(0, 50.0, null), p(1000, 50.0, null))))
        assertNull(Regen.kwh(emptyList()))
    }
}
