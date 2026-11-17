package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTripTrackerTest {

    private fun reading(
        odo: Double?, elec: Double? = null, fuel: Double? = null, hev: Double? = null, power: Int = 2,
    ): DiParsData = diParsData(mileage = odo, totalElecConsumption = elec, powerState = power)
        .copy(fuelTotalL = fuel, mileageHevKm = hev?.toInt())

    private val t0 = 1_000_000L

    @Test
    fun firstOnReadingOpensSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        assertTrue(tr.onReading(reading(17000.0, 2650.0), t0))
        assertEquals(t0, tr.state.open!!.startMs)
    }

    @Test
    fun deltasGrowWithCounters() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0, 2650.0, 300.0, 5000.0), t0)
        tr.onReading(reading(17012.4, 2652.1, 300.6, 5004.0), t0 + 10_000)
        val e = tr.state.open!!.estimate()
        assertEquals(12.4, e.km, 1e-9)
        assertEquals(2.1, e.kwh!!, 1e-9)
        assertEquals(0.6, e.litres!!, 1e-9)
        assertEquals(4.0, e.hevKm!!, 1e-9)
    }

    @Test
    fun lateCounterTakesFirstNonNullAsBase() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0, null), t0)
        tr.onReading(reading(17001.0, 2650.0), t0 + 10_000)
        tr.onReading(reading(17002.0, 2651.0), t0 + 20_000)
        val e = tr.state.open!!.estimate()
        assertEquals(1.0, e.kwh!!, 1e-9)
        assertNull(e.litres)
    }

    @Test
    fun offEdgeFinishesSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0, 2650.0), t0)
        tr.onReading(reading(17012.4, 2652.0), t0 + 10_000)
        var lastOn = t0 + 10_000
        var changed = false
        for (i in 2..20) {
            val now = t0 + i * 10_000L
            changed = tr.onReading(reading(17012.4, 2652.0, power = 0), now) || changed
            if (tr.state.open != null) lastOn = now
        }
        assertTrue(changed)
        assertNull(tr.state.open)
        assertEquals(lastOn, tr.state.finished!!.endMs)
        assertEquals(12.4, tr.state.finished!!.km, 1e-9)
    }

    @Test
    fun parkedSessionDoesNotReplaceFinished() {
        val prev = TripEstimate(1L, 2L, 12.4, null, null, null)
        val tr = LiveTripTracker(LiveTripState(finished = prev), t0)
        tr.onReading(reading(17000.0), t0)
        for (i in 1..20) tr.onReading(reading(17000.0, power = 0), t0 + i * 10_000L)
        assertNull(tr.state.open)
        assertEquals(prev, tr.state.finished)
    }

    @Test
    fun gapFinishesSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        tr.onReading(reading(17005.0), t0 + 10_000)
        val next = t0 + 10_000 + LiveTripTracker.GAP_MS + 1
        assertTrue(tr.onReading(reading(17005.0), next))
        assertEquals(5.0, tr.state.finished!!.km, 1e-9)
        assertEquals(next, tr.state.open!!.startMs)
    }

    @Test
    fun counterBelowBaseStartsNewSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        tr.onReading(reading(17010.0), t0 + 10_000)
        tr.onReading(reading(17000.5), t0 + 20_000)
        assertEquals(17000.5, tr.state.open!!.base.odometerKm!!, 1e-9)
        assertEquals(10.0, tr.state.finished!!.km, 1e-9)
    }

    private fun resumable(lastReading: Long) = LiveTripState(
        open = LiveSession(
            startMs = lastReading - 600_000, lastOnMs = lastReading, lastReadingMs = lastReading,
            base = TripCounters(17000.0, null, null, null), latest = TripCounters(17012.0, null, null, null),
        ),
    )

    @Test
    fun resumeWithinGapContinues() {
        val s = resumable(t0 - 240_000)
        val tr = LiveTripTracker(s, t0)
        assertEquals(s.open, tr.state.open)
        tr.onReading(reading(17012.0, power = 0), t0 + 10_000)
        assertNotNull(tr.state.open)
    }

    @Test
    fun resumeAfterGapFinishes() {
        val s = resumable(t0 - 360_000)
        val tr = LiveTripTracker(s, t0)
        assertNull(tr.state.open)
        assertEquals(s.open!!.lastOnMs, tr.state.finished!!.endMs)
    }

    @Test
    fun finishedExpiresAfter48h() {
        val prev = TripEstimate(1L, t0, 12.4, null, null, null)
        val tr = LiveTripTracker(LiveTripState(finished = prev), t0 + 1)
        assertEquals(prev, tr.state.finished)
        tr.onReading(reading(17000.0, power = 0), t0 + LiveTripTracker.FINISHED_TTL_MS + 1)
        assertNull(tr.state.finished)
        assertNull(LiveTripTracker(LiveTripState(finished = prev), t0 + LiveTripTracker.FINISHED_TTL_MS + 1).state.finished)
    }

    private val sample = LiveTripState(
        open = LiveSession(
            1L, 2L, 3L, TripCounters(17000.0, null, 300.5, null), TripCounters(17012.4, 2652.1, null, 4.0),
        ),
        finished = TripEstimate(5L, 6L, 12.4, null, 0.6, null),
    )

    @Test
    fun codecRoundTrip() {
        assertEquals(sample, LiveTripCodec.decode(LiveTripCodec.encode(sample)))
        assertEquals(LiveTripState(), LiveTripCodec.decode(LiveTripCodec.encode(LiveTripState())))
    }

    @Test
    fun codecSurvivesGarbage() {
        val empty = LiveTripState()
        assertEquals(empty, LiveTripCodec.decode(null))
        assertEquals(empty, LiveTripCodec.decode(""))
        assertEquals(empty, LiveTripCodec.decode("x;y"))
        assertEquals(empty, LiveTripCodec.decode(LiveTripCodec.encode(sample).dropLast(3)))
    }
}
