package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCardCalcTest {
    private val min = 60_000L
    private val day = 1_000_000_000_000L
    private val t0800 = day + 8 * 60 * min

    private fun counters(odo: Double, kwh: Double? = null, fuel: Double? = null, hev: Double? = null) =
        TripCounters(odo, kwh, fuel, hev)

    private fun open(startMs: Long, lastSeenMs: Long, km: Double, kwh: Double? = null) =
        LiveSession(
            startMs, startMs, lastSeenMs,
            counters(1000.0, kwh?.let { 100.0 }),
            counters(1000.0 + km, kwh?.let { 100.0 + it }),
        )

    private fun row(startMs: Long, endMs: Long?, km: Double?) = TripRow(startMs, endMs, km, null, null, null)

    private fun split(session: LiveSession, d: DrivePark) = session.copy(split = d)

    @Test fun drivingShowsCurrentWithSplit() {
        val s = split(
            open(t0800, t0800 + 60 * min, 12.4),
            DrivePark(driveMs = 42 * min + 30_000, parkMs = 15 * min + 59_000, driveKwh = 4.9, parkKwh = 0.6, driveL = 0.8),
        )
        val m = TripCardCalc.from(LiveTripState(s), emptyList(), day)
        assertEquals(TripCardMode.CURRENT, m.mode)
        assertEquals(12.4, m.km!!, 1e-9)
        assertEquals(42, m.driveMinutes)
        assertEquals(15, m.parkMinutes)
        assertEquals(4.9, m.driveKwh!!, 1e-9)
        assertEquals(0.8, m.driveL!!, 1e-9)
        assertEquals(0.6, m.parkKwh!!, 1e-9)
        assertEquals(0.0, m.parkL!!, 1e-9)
    }

    @Test fun justSwitchedOnShowsLastTripWithRecordedDashes() {
        val live = LiveTripState(open(t0800 + 60 * min, t0800 + 60 * min, 0.05))
        val today = listOf(TripRow(t0800, t0800 + 30 * min, 30.0, 5.0, 1.0, null))
        val m = TripCardCalc.from(live, today, day)
        assertEquals(TripCardMode.LAST, m.mode)
        assertEquals(30.0, m.km!!, 1e-9)
        assertEquals(30, m.driveMinutes)
        assertEquals(5.0, m.driveKwh!!, 1e-9)
        assertEquals(1.0, m.driveL!!, 1e-9)
        assertNull(m.parkMinutes)
        assertNull(m.parkKwh)
        assertNull(m.parkL)
    }

    @Test fun finishedEstimateWithSplitKeepsIt() {
        val fin = TripEstimate(t0800, t0800 + 20 * min, 12.4, 3.0, 0.0, null, DrivePark(driveMs = 15 * min, parkMs = 5 * min, driveKwh = 2.5, parkKwh = 0.5))
        val m = TripCardCalc.from(LiveTripState(null, listOf(fin)), emptyList(), day)
        assertEquals(TripCardMode.LAST, m.mode)
        assertEquals(15, m.driveMinutes)
        assertEquals(5, m.parkMinutes)
        assertEquals(0.5, m.parkKwh!!, 1e-9)
    }

    @Test fun finishedEstimateWithoutSplitReadsAsRecorded() {
        val fin = TripEstimate(t0800, t0800 + 20 * min, 12.4, 3.0, null, null)
        val m = TripCardCalc.from(LiveTripState(null, listOf(fin)), emptyList(), day)
        assertEquals(20, m.driveMinutes)
        assertEquals(3.0, m.driveKwh!!, 1e-9)
        assertNull(m.parkMinutes)
        assertNull(m.parkKwh)
    }

    @Test fun unconfirmedFinishedIsLast() {
        val fin = TripEstimate(t0800, t0800 + 20 * min, 12.4, null, null, null)
        val m = TripCardCalc.from(LiveTripState(null, listOf(fin)), emptyList(), day)
        assertEquals(TripCardMode.LAST, m.mode)
        assertEquals(12.4, m.km!!, 1e-9)
    }

    @Test fun latestOfRecordedAndFinishedWins() {
        val t1000 = t0800 + 120 * min
        val fin = TripEstimate(t1000, t1000 + 20 * min, 7.0, null, null, null)
        val today = listOf(row(t0800, t0800 + 30 * min, 30.0))
        val m = TripCardCalc.from(LiveTripState(null, listOf(fin)), today, day)
        assertEquals(TripCardMode.LAST, m.mode)
        assertEquals(7.0, m.km!!, 1e-9)
    }

    @Test fun yesterdaysTripIsNotLast() {
        val fin = TripEstimate(day - 50 * min, day - 20 * min, 9.0, null, null, null)
        val m = TripCardCalc.from(LiveTripState(null, listOf(fin)), emptyList(), day)
        assertEquals(TripCardMode.EMPTY, m.mode)
    }

    @Test fun nothingTodayIsEmpty() {
        val m = TripCardCalc.from(LiveTripState(), emptyList(), day)
        assertEquals(TripCardModel.EMPTY, m)
    }

    @Test fun negativeEnergyShowsZero() {
        val s = split(
            open(t0800, t0800 + 10 * min, 5.0),
            DrivePark(driveMs = 10 * min, driveKwh = -0.3, driveL = -0.1),
        )
        val m = TripCardCalc.from(LiveTripState(s), emptyList(), day)
        assertEquals(0.0, m.driveKwh!!, 1e-9)
        assertEquals(0.0, m.driveL!!, 1e-9)
        assertTrue(m.parkKwh!! >= 0.0)
    }

    @Test fun sessionFromYesterdayStillCurrent() {
        val live = LiveTripState(open(day - 10 * min, day + 20 * min, 3.0))
        assertEquals(TripCardMode.CURRENT, TripCardCalc.from(live, emptyList(), day).mode)
    }
}
