package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripMatchTest {
    private fun t(h: Int, m: Int, s: Int = 0) = ((h * 60L + m) * 60L + s) * 1000L
    private fun rec(start: Long, end: Long?, km: Double? = 10.0) =
        TripRow(start, end, km, kwh = 1.0, litres = null, hevKm = null)
    private fun est(start: Long, end: Long, km: Double = 10.0) =
        TripEstimate(start, end, km, kwh = 1.0, litres = null, hevKm = null)

    @Test fun previousTripDoesNotConfirmNewSession() {
        assertFalse(TripMatch.confirmed(est(t(8, 32), t(9, 10)), listOf(rec(t(8, 0), t(8, 30)))))
    }

    @Test fun overlapConfirms() {
        assertTrue(TripMatch.confirmed(est(t(8, 5), t(8, 39)), listOf(rec(t(8, 0), t(8, 40)))))
    }

    @Test fun sessionStartingLateIsConfirmedByOverlap() {
        assertTrue(TripMatch.confirmed(est(t(8, 20), t(8, 58)), listOf(rec(t(8, 0), t(9, 0)))))
    }

    @Test fun touchingWithin30sIsNotOverlap() {
        assertFalse(TripMatch.confirmed(est(t(8, 30), t(9, 0)), listOf(rec(t(8, 0), t(8, 30, 20)))))
    }

    @Test fun zeroKmRecordedTripDoesNotConfirm() {
        assertFalse(TripMatch.confirmed(est(t(8, 5), t(8, 39)), listOf(rec(t(8, 0), t(8, 40), km = 0.0))))
    }

    @Test fun openEndRecordedTripConfirmsByStart() {
        assertTrue(TripMatch.confirmed(est(t(8, 0), t(9, 0)), listOf(rec(t(8, 10), null))))
    }

    @Test fun pendingSkipsUnmovedAndConfirmed() {
        val recorded = listOf(rec(t(8, 0), t(8, 40)))
        val open = LiveSession(
            anchorMs = t(10, 0), startMs = t(10, 0), lastSeenMs = t(10, 5),
            base = TripCounters(100.0, null, null, null), latest = TripCounters(100.05, null, null, null),
        )
        val fin = est(t(8, 5), t(8, 39))
        assertTrue(TripMatch.pending(LiveTripState(open, listOf(fin)), recorded, t(10, 6)).isEmpty())
        val moved = open.copy(latest = TripCounters(103.2, null, null, null))
        val p = TripMatch.pending(LiveTripState(moved, listOf(fin)), recorded, t(10, 6))
        assertEquals(1, p.size)
        assertEquals(t(10, 6), p[0].endMs)
        assertEquals(3.2, p[0].km, 1e-9)
    }

    // ---- chart rows: live trip ours, finished as recorded ----
    private fun open(startMs: Long, lastMs: Long, odo0: Double, odo1: Double) = LiveSession(
        anchorMs = startMs, startMs = startMs, lastSeenMs = lastMs,
        base = TripCounters(odo0, null, null, null), latest = TripCounters(odo1, null, null, null),
    )

    @Test fun liveEstimateHidesOverlappingRecord() {
        val r = TripRow(t(8, 0), t(8, 40), 12.0, 3.0, null, null)
        val live = LiveTripState(open(t(8, 0), t(8, 38), 100.0, 110.0), emptyList())
        val rows = TripMatch.chartRows(listOf(r), live, t(8, 40))
        assertEquals(1, rows.size)
        assertEquals(10.0, rows[0].km!!, 1e-9)
    }

    @Test fun afterFinishRecordCountsAndEstimateDoesNot() {
        val r = TripRow(t(8, 0), t(8, 40), 12.0, 3.0, 0.5, 2.0)
        val fin = est(t(8, 0), t(8, 38), 10.0)
        val rows = TripMatch.chartRows(listOf(r), LiveTripState(null, listOf(fin)), t(9, 0))
        assertEquals(listOf(r), rows)
    }

    @Test fun finishedEstimateWithoutRecordIsCounted() {
        val fin = est(t(8, 0), t(8, 38), 10.0)
        val rows = TripMatch.chartRows(emptyList(), LiveTripState(null, listOf(fin)), t(9, 0))
        assertEquals(1, rows.size)
        assertEquals(10.0, rows[0].km!!, 1e-9)
    }

    @Test fun recordNotOverlappingOpenEstimateIsCounted() {
        val r = TripRow(t(7, 0), t(7, 30), 8.0, 2.0, null, null)
        val live = LiveTripState(open(t(8, 0), t(8, 38), 100.0, 110.0), emptyList())
        val rows = TripMatch.chartRows(listOf(r), live, t(8, 40))
        assertEquals(2, rows.size)
        assertEquals(18.0, rows.sumOf { it.km!! }, 1e-9)
    }

    @Test fun openEndedRecordInsideOpenEstimateIsHidden() {
        val r = TripRow(t(8, 10), null, 5.0, null, null, null)
        val live = LiveTripState(open(t(8, 0), t(8, 38), 100.0, 110.0), emptyList())
        assertEquals(1, TripMatch.chartRows(listOf(r), live, t(8, 40)).size)
        assertEquals(1, TripMatch.hiddenByLive(listOf(r), live, t(8, 40)).size)
    }

    @Test fun hideEventIsLoggedOncePerPairAcrossPublishes() {
        TripLog.resetHidesForTest()
        val live = LiveTripState(open(t(8, 0), t(8, 38), 100.0, 110.0), emptyList())
        val first = TripLogEvents.hideDecisions(listOf(TripRow(t(8, 0), null, 3.0, null, null, null)), live, t(8, 40))
        assertEquals(1, TripLog.newHides(first).size)
        val later = LiveTripState(open(t(8, 0), t(8, 50), 100.0, 125.0), emptyList())
        val again = TripLogEvents.hideDecisions(listOf(TripRow(t(8, 0), null, 9.0, null, null, null)), later, t(8, 52))
        assertEquals(0, TripLog.newHides(again).size)
    }
}
