package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripCardCalcTest {
    private val min = 60_000L
    private val day = 1_000_000_000_000L
    private val t0800 = day + 8 * 60 * min

    private fun counters(odo: Double, kwh: Double? = null, fuel: Double? = null, hev: Double? = null) =
        TripCounters(odo, kwh, fuel, hev)

    private fun open(startMs: Long, lastOnMs: Long, km: Double, kwh: Double? = null) =
        LiveSession(
            startMs, lastOnMs, lastOnMs,
            counters(1000.0, kwh?.let { 100.0 }),
            counters(1000.0 + km, kwh?.let { 100.0 + it }),
        )

    private fun row(startMs: Long, endMs: Long?, km: Double?) = TripRow(startMs, endMs, km, null, null, null)

    @Test fun drivingShowsCurrent() {
        val live = LiveTripState(open(t0800, t0800 + 31 * min, 12.4))
        val m = TripCardCalc.from(live, emptyList(), day)
        assertEquals(TripCardMode.CURRENT, m.mode)
        assertEquals(12.4, m.km!!, 1e-9)
        assertEquals(31, m.minutes)
        assertEquals(24, m.avgKmh)
    }

    @Test fun justSwitchedOnShowsLastTrip() {
        val live = LiveTripState(open(t0800 + 60 * min, t0800 + 60 * min, 0.05))
        val today = listOf(row(t0800, t0800 + 30 * min, 30.0))
        val m = TripCardCalc.from(live, today, day)
        assertEquals(TripCardMode.LAST, m.mode)
        assertEquals(30.0, m.km!!, 1e-9)
        assertEquals(30, m.minutes)
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
        assertEquals(TripCardModel(TripCardMode.EMPTY, null, null, null, null, null, null), m)
    }

    @Test fun evWithoutFuelHidesLitresAndShare() {
        val live = LiveTripState(open(t0800, t0800 + 30 * min, 10.0, kwh = 2.0))
        val m = TripCardCalc.from(live, emptyList(), day)
        assertNull(m.electricShare)
        assertNull(m.litres)
        assertEquals(2.0, m.kwh!!, 1e-9)
    }

    @Test fun negativeEnergyShowsZero() {
        val s = LiveSession(
            t0800, t0800 + 10 * min, t0800 + 10 * min,
            counters(1000.0, 100.0, 50.0), counters(1005.0, 99.7, 49.9),
        )
        val m = TripCardCalc.from(LiveTripState(s), emptyList(), day)
        assertEquals(0.0, m.kwh!!, 1e-9)
        assertEquals(0.0, m.litres!!, 1e-9)
    }

    @Test fun sessionFromYesterdayStillCurrent() {
        val live = LiveTripState(open(day - 10 * min, day + 20 * min, 3.0))
        assertEquals(TripCardMode.CURRENT, TripCardCalc.from(live, emptyList(), day).mode)
    }
}
