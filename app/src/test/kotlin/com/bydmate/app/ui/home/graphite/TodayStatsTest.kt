package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayStatsTest {
    private val min = 60_000L

    @Test fun minutesAndAverageSpeed() {
        val t = listOf(
            TripEntity(startTs = 0, endTs = 36 * min, distanceKm = 20.0),
            TripEntity(startTs = 100 * min, endTs = 136 * min, distanceKm = 27.3),
        )
        val s = TodayStatsCalc.from(t, 47.3)
        assertEquals(72, s.minutes)
        assertEquals(39, s.avgKmh)
    }

    @Test fun tripWithoutEndIsIgnored() {
        val t = listOf(
            TripEntity(startTs = 0, endTs = 60 * min, distanceKm = 30.0),
            TripEntity(startTs = 100 * min, endTs = null, distanceKm = 5.0),
        )
        val s = TodayStatsCalc.from(t, 35.0)
        assertEquals(60, s.minutes)
        assertEquals(30, s.avgKmh)
    }

    @Test fun noTripsGivesNoSpeed() {
        val s = TodayStatsCalc.from(emptyList(), null)
        assertEquals(0, s.minutes)
        assertNull(s.avgKmh); assertNull(s.kwh); assertNull(s.litres); assertNull(s.electricShare)
    }

    @Test fun electricShareFromTripsWithFuel() {
        val t = listOf(TripEntity(startTs = 0, endTs = 60 * min, distanceKm = 100.0, fuelLiters = 4.0, hevKm = 39.0))
        assertEquals(61, TodayStatsCalc.from(t, 100.0).electricShare)
        val noFuel = listOf(TripEntity(startTs = 0, endTs = 60 * min, distanceKm = 100.0, hevKm = 0.0))
        assertNull(TodayStatsCalc.from(noFuel, 100.0).electricShare)
    }
}
