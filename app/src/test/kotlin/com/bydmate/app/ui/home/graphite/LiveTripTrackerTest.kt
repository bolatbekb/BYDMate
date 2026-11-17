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
        tr.onReading(reading(17012.4, 2652.1, 300.6, 5004.0), t0 + 150_000) // 12.4 km: under 300 km/h
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
        tr.onReading(reading(17001.9, 2652.0), t0 + 10_000)
        var changed = false
        var lastBeforeClose = 0L
        for (i in 2..20) {
            val now = t0 + i * 10_000L
            changed = tr.onReading(reading(17001.9, 2652.0, power = 0), now) || changed
            if (tr.state.open != null) lastBeforeClose = now
        }
        assertTrue(changed)
        assertNull(tr.state.open)
        // The odometer last "grew" against the t0 reading, which leaves the 60 s window at t0 + 60 s.
        assertEquals(t0 + 60_000, tr.state.finished.last().endMs)
        assertTrue(lastBeforeClose > t0 + 60_000)
        assertEquals(1.9, tr.state.finished.last().km, 1e-9)
    }

    @Test
    fun parkedSessionDoesNotReplaceFinished() {
        val prev = TripEstimate(1L, 2L, 12.4, null, null, null)
        val tr = LiveTripTracker(LiveTripState(finished = listOf(prev)), t0)
        tr.onReading(reading(17000.0), t0)
        for (i in 1..20) tr.onReading(reading(17000.0, power = 0), t0 + i * 10_000L)
        assertNull(tr.state.open)
        assertEquals(listOf(prev), tr.state.finished)
    }

    @Test
    fun gapFinishesSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        tr.onReading(reading(17002.0), t0 + 10_000)
        val next = t0 + 10_000 + LiveTripTracker.GAP_MS + 1
        assertTrue(tr.onReading(reading(17002.0), next))
        assertEquals(2.0, tr.state.finished.last().km, 1e-9)
        assertEquals(next, tr.state.open!!.startMs)
    }

    @Test
    fun counterBelowBaseStartsNewSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        tr.onReading(reading(17001.9), t0 + 10_000)
        tr.onReading(reading(17000.5), t0 + 20_000)
        assertEquals(17000.5, tr.state.open!!.base.odometerKm!!, 1e-9)
        assertEquals(1.9, tr.state.finished.last().km, 1e-9)
    }

    @Test
    fun regenDipDoesNotSplitSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0, 2650.0), t0)
        tr.onReading(reading(17001.0, 2651.0), t0 + 10_000)
        tr.onReading(reading(17002.0, 2650.8), t0 + 20_000)
        assertEquals(t0, tr.state.open!!.startMs)
        assertEquals(0.8, tr.state.open!!.estimate().kwh!!, 1e-9)
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
        assertEquals(s.open!!.copy(lastReadingMs = t0), tr.state.open)
        tr.onReading(reading(17012.0, power = 0), t0 + 10_000)
        assertNotNull(tr.state.open)
    }

    @Test
    fun resumeAfterGapFinishes() {
        val s = resumable(t0 - 360_000)
        val tr = LiveTripTracker(s, t0)
        assertNull(tr.state.open)
        assertEquals(s.open!!.lastOnMs, tr.state.finished.last().endMs)
    }

    @Test
    fun finishedExpiresAfter48h() {
        val prev = TripEstimate(1L, t0, 12.4, null, null, null)
        val tr = LiveTripTracker(LiveTripState(finished = listOf(prev)), t0 + 1)
        assertEquals(listOf(prev), tr.state.finished)
        tr.onReading(reading(17000.0, power = 0), t0 + LiveTripTracker.FINISHED_TTL_MS + 1)
        assertTrue(tr.state.finished.isEmpty())
        assertTrue(LiveTripTracker(LiveTripState(finished = listOf(prev)), t0 + LiveTripTracker.FINISHED_TTL_MS + 1).state.finished.isEmpty())
    }

    private val sample = LiveTripState(
        open = LiveSession(
            1L, 2L, 3L, TripCounters(17000.0, null, 300.5, null), TripCounters(17012.4, 2652.1, null, 4.0),
        ),
        finished = listOf(TripEstimate(5L, 6L, 12.4, null, 0.6, null)),
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

    @Test
    fun odometerScaleJumpStartsNewSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        tr.onReading(reading(17001.0), t0 + 10_000)
        tr.onReading(reading(170010.0), t0 + 20_000)
        assertEquals(1.0, tr.state.finished.last().km, 1e-9)
        assertEquals(170010.0, tr.state.open!!.base.odometerKm!!, 1e-9)
    }

    @Test
    fun slowReadingAfterLongDriveIsNotAJump() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        // 20 km in 4.5 min = 267 km/h < 300 km/h limit (a longer gap would split by GAP_MS anyway)
        tr.onReading(reading(17020.0), t0 + 270_000)
        assertTrue(tr.state.finished.isEmpty())
        assertEquals(t0, tr.state.open!!.startMs)
    }

    @Test
    fun implausibleKwhIsNull() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0, 2650.0), t0)
        tr.onReading(reading(17001.0, 56950.0), t0 + 10_000)
        assertNull(tr.state.open!!.estimate().kwh)
        assertEquals(1.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun implausibleFinishedIsDropped() {
        val prev = TripEstimate(1L, 2L, 12.4, null, null, null)
        val open = LiveSession(
            t0 - 600_000, t0, t0, TripCounters(17000.0, null, null, null), TripCounters(18600.0, null, null, null),
        )
        val tr = LiveTripTracker(LiveTripState(open, listOf(prev)), t0 + 400_000)
        assertNull(tr.state.open)
        assertEquals(listOf(prev), tr.state.finished)
    }

    @Test
    fun twoDrivesBothPending() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        var now = t0
        fun drive(from: Double) {
            tr.onReading(reading(from), now)
            now += 10_000
            tr.onReading(reading(from + 1.5), now)
            repeat(20) { now += 10_000; tr.onReading(reading(from + 1.5, power = 0), now) }
            now += 10_000
        }
        drive(17000.0)
        drive(17005.0)
        assertEquals(2, tr.state.finished.size)
        assertEquals(2, TripMatch.pending(tr.state, emptyList(), now).size)
    }

    @Test
    fun finishedListCapsAt8() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        var now = t0
        repeat(10) { i ->
            tr.onReading(reading(17000.0 + i * 5), now)
            now += 10_000
            tr.onReading(reading(17001.5 + i * 5), now)
            repeat(20) { now += 10_000; tr.onReading(reading(17001.5 + i * 5, power = 0), now) }
            now += 10_000
        }
        assertEquals(8, tr.state.finished.size)
        assertEquals(1.5, tr.state.finished.last().km, 1e-9)
    }

    @Test
    fun codecReadsL1() {
        val s = LiveTripCodec.decode("L1|-|5,6,12.4,,0.6,|E")
        assertEquals(listOf(TripEstimate(5L, 6L, 12.4, null, 0.6, null)), s.finished)
        assertNull(s.open)
    }

    @Test
    fun resumeDoesNotMoveEndWithoutSignsOfRunning() {
        val s = resumable(t0 - 60_000)
        val tr = LiveTripTracker(s, t0)
        for (i in 1..12) tr.onReading(reading(17012.0, power = 0), t0 + i * 10_000L)
        assertNull(tr.state.open)
        assertEquals(s.open!!.lastOnMs, tr.state.finished.last().endMs)
    }

    @Test
    fun singleTenthStepCountsAsMoved() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onReading(reading(17000.0), t0)
        tr.onReading(reading(17000.1), t0 + 10_000)
        for (i in 2..20) tr.onReading(reading(17000.1, power = 0), t0 + i * 10_000L)
        assertEquals(1, tr.state.finished.size)
    }

    @Test
    fun countersOfReadsStartupZeroAsNull() {
        val c = TripCounters.of(reading(0.5, 0.0, 0.0, 0.0))
        assertNull(c.odometerKm)
        assertNull(c.elecKwh)
        assertNull(c.fuelL)
        assertNull(c.hevKm)
    }
}
