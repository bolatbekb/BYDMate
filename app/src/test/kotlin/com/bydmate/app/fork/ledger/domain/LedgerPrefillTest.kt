package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.data.local.dao.CounterSnapshotDao
import com.bydmate.app.data.local.entity.CounterSnapshotEntity
import com.bydmate.app.fork.ledger.data.RefuelEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerPrefillTest {
    private val hour = 3_600_000L
    private val snaps = mockk<CounterSnapshotDao>()
    private val prefill = LedgerPrefill(snaps)
    private val now = 1_000_000_000_000L

    private fun draft(date: Long) = RefuelEntity(
        date = date, odometerKm = 500.0, liters = 0.0, totalCost = null, pricePerLiter = null,
        fullTank = false, carFuelTotalL = 40.0, tankPercentBefore = 30, tankPercentAfter = 80,
        lat = null, lon = null, place = null, note = null,
        source = "detected", status = "draft", importKey = null,
    )

    private fun snap(ts: Long) = CounterSnapshotEntity(
        ts = ts, fuelTotal = 33.0, mileageHev = null, mileageEv = null, odometer = 321.0, reason = "periodic",
    )

    @Test fun freshEntryUsesLive() = runBlocking {
        val p = prefill.forEntry(now - hour, now, car(mileage = 1000.0, fuelTotalL = 55.5), null)
        assertEquals(PrefillSource.LIVE, p.source)
        assertEquals(1000.0, p.odometerKm)
        assertEquals(55.5, p.carFuelTotalL)
    }

    @Test fun lateEntryDoesNotUseLiveCounter() = runBlocking {
        coEvery { snaps.nearest(any(), any()) } returns null
        val p = prefill.forEntry(now - 7 * 24 * hour, now, car(mileage = 1000.0, fuelTotalL = 55.5), null)
        assertEquals(PrefillSource.NONE, p.source)
        assertNull(p.odometerKm)
        assertNull(p.carFuelTotalL)
    }

    @Test fun lateEntryUsesNearbyDraft() = runBlocking {
        val entry = now - 7 * 24 * hour
        val p = prefill.forEntry(entry, now, car(mileage = 1000.0), draft(entry + 5 * hour))
        assertEquals(PrefillSource.DRAFT, p.source)
        assertEquals(500.0, p.odometerKm)
        assertEquals(40.0, p.carFuelTotalL)
    }

    @Test fun lateEntryFallsBackToSnapshot() = runBlocking {
        val entry = now - 7 * 24 * hour
        coEvery { snaps.nearest(entry, hour) } returns snap(entry)
        val p = prefill.forEntry(entry, now, car(mileage = 1000.0), draft(entry + 30 * hour))
        assertEquals(PrefillSource.SNAPSHOT, p.source)
        assertEquals(321.0, p.odometerKm)
        assertEquals(33.0, p.carFuelTotalL)
    }

    @Test fun noLiveDataFallsThrough() = runBlocking {
        coEvery { snaps.nearest(any(), any()) } returns snap(now)
        assertEquals(PrefillSource.SNAPSHOT, prefill.forEntry(now, now, null, null).source)
        assertEquals(PrefillSource.SNAPSHOT, prefill.forEntry(now, now, car(mileage = null), null).source)
    }

    @Test fun nearbyDraftBeatsLive() = runBlocking {
        // refuelled, drove home for 20 min, then tapped + Refuel: the station values win
        val p = prefill.forEntry(now, now, car(mileage = 1012.0, fuelTotalL = 41.0), draft(now - 20 * 60_000L))
        assertEquals(PrefillSource.DRAFT, p.source)
        assertEquals(500.0, p.odometerKm)
        assertEquals(40.0, p.carFuelTotalL)
    }

    @Test fun draftOlderThanTwoHoursLosesToLive() = runBlocking {
        val p = prefill.forEntry(now, now, car(mileage = 1012.0, fuelTotalL = 41.0), draft(now - 3 * hour))
        assertEquals(PrefillSource.LIVE, p.source)
    }

    @Test fun snapshotWindowIsOneHour() = runBlocking {
        val entry = now - 7 * 24 * hour
        // only a wider window would find the 5 h old snapshot
        coEvery { snaps.nearest(any(), any()) } returns snap(entry - 5 * hour)
        coEvery { snaps.nearest(entry, hour) } returns null
        val p = prefill.forEntry(entry, now, null, null)
        assertEquals(PrefillSource.NONE, p.source)
    }
}
