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

    private fun open(startMs: Long, lastSeenMs: Long, km: Double, kwh: Double? = null, fuel: Double? = null) =
        LiveSession(
            startMs, startMs, lastSeenMs,
            counters(1000.0, kwh?.let { 100.0 }, fuel?.let { 50.0 }),
            counters(1000.0 + km, kwh?.let { 100.0 + it }, fuel?.let { 50.0 + it }),
        )

    private fun row(startMs: Long, endMs: Long?, km: Double?) = TripRow(startMs, endMs, km, null, null, null)

    private fun split(session: LiveSession, d: DrivePark) = session.copy(split = d)

    @Test fun drivingShowsCurrentWithSplit() {
        val s = split(
            open(t0800, t0800 + 60 * min, 12.4, kwh = 5.5, fuel = 0.8),
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

    @Test fun justSwitchedOnShowsCurrentZeroWithParkMinutes() {
        val s = split(open(t0800 + 60 * min, t0800 + 70 * min, 0.0), DrivePark(driveMs = 0, parkMs = 10 * min + 5_000))
        val today = listOf(TripRow(t0800, t0800 + 30 * min, 30.0, 5.0, 1.0, null))
        val m = TripCardCalc.from(LiveTripState(s), today, day)
        assertEquals(TripCardMode.CURRENT, m.mode)
        assertEquals(0.0, m.km!!, 1e-9)
        assertEquals(0, m.driveMinutes)
        assertEquals(10, m.parkMinutes)
    }

    @Test fun logFieldsChangeOnlyWithTheShownCard() {
        val a = TripCardModel(TripCardMode.CURRENT, 0.52, 3, 4, 0.12, 0.0, 0.0, 0.0)
        val same = a.copy(km = 0.54, driveKwh = 0.14)
        assertEquals(TripCardCalc.logFields(a), TripCardCalc.logFields(same))
        assertTrue(TripCardCalc.logFields(a) != TripCardCalc.logFields(a.copy(km = 0.6)))
        assertTrue(TripCardCalc.logFields(a) != TripCardCalc.logFields(a.copy(mode = TripCardMode.LAST)))
    }

    @Test fun noOpenShowsLastRecordedTripWithDashes() {
        val live = LiveTripState(null)
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
            open(t0800, t0800 + 10 * min, 5.0, kwh = 1.0, fuel = 0.5),
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

    @Test fun missingKwhCounterReadsDash() {
        val d = DrivePark(driveMs = 10 * min, parkMs = 5 * min, driveL = 0.8, parkL = 0.1)
        val live = LiveTripState(open(t0800, t0800 + 15 * min, 5.0, kwh = null).copy(split = d))
        val m = TripCardCalc.from(live, emptyList(), day)
        assertNull(m.driveKwh)
        assertNull(m.parkKwh)
    }

    @Test fun missingFuelCounterReadsDashInLitres() {
        val d = DrivePark(driveMs = 10 * min, parkMs = 5 * min, driveKwh = 4.9, parkKwh = 0.6)
        val live = LiveTripState(open(t0800, t0800 + 15 * min, 5.0, kwh = 5.5).copy(split = d))
        val m = TripCardCalc.from(live, emptyList(), day)
        assertEquals(4.9, m.driveKwh!!, 1e-9)
        assertNull(m.driveL)
        assertNull(m.parkL)
        val cells = tripCells(m, hasFuelTank = true, primary = androidx.compose.ui.graphics.Color.White)
        assertEquals(listOf("4,9 · —", "0,6 · —"), cells.drop(2).map { it.value })
    }

    @Test fun finishedSplitWinsOverOverlappingRecordedRow() {
        val fin = TripEstimate(t0800, t0800 + 40 * min, 20.0, 3.0, null, null, DrivePark(driveMs = 30 * min, parkMs = 10 * min, driveKwh = 2.5, parkKwh = 0.5))
        val today = listOf(TripRow(t0800, t0800 + 41 * min, 20.0, 3.0, null, null))
        val m = TripCardCalc.from(LiveTripState(null, listOf(fin)), today, day)
        assertEquals(TripCardMode.LAST, m.mode)
        assertEquals(30, m.driveMinutes)
        assertEquals(10, m.parkMinutes)
        assertEquals(0.5, m.parkKwh!!, 1e-9)
    }

    @Test fun recordedRowAloneShowsTotalsAndNoPark() {
        val today = listOf(TripRow(t0800, t0800 + 41 * min, 20.0, 3.0, null, null))
        val m = TripCardCalc.from(LiveTripState(), today, day)
        assertEquals(41, m.driveMinutes)
        assertNull(m.parkMinutes)
        assertNull(m.parkKwh)
    }
}
