package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.data.local.entity.TripPointEntity
import kotlinx.coroutines.runBlocking
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

    @Test fun cacheReadsEachFinishedTripOnce() = runBlocking {
        val cache = RegenCache()
        val reads = mutableListOf<Long>()
        val pts = listOf(p(0, 50.0, -36_000.0), p(10_000, 50.0, 0.0))
        val load: suspend (Long) -> List<TripPointEntity> = { id -> reads += id; pts }
        val a = TripEntity(id = 1, startTs = 0, endTs = 10)
        val b = TripEntity(id = 2, startTs = 20, endTs = 30)
        val open = TripEntity(id = 3, startTs = 40, endTs = null)
        assertEquals(0.1, cache.total(listOf(a, open), load)!!, 1e-9)
        assertEquals(0.2, cache.total(listOf(a, b, open), load)!!, 1e-9)
        assertEquals(0.2, cache.total(listOf(a, b, open), load)!!, 1e-9)
        assertEquals(listOf(1L, 2L), reads)
    }

    @Test fun cacheRemembersTripsWithoutPower() = runBlocking {
        val cache = RegenCache()
        var reads = 0
        val load: suspend (Long) -> List<TripPointEntity> = { reads++; listOf(p(0, 50.0, null)) }
        val a = TripEntity(id = 1, startTs = 0, endTs = 10)
        assertNull(cache.total(listOf(a), load))
        assertNull(cache.total(listOf(a), load))
        assertEquals(1, reads)
    }
}
