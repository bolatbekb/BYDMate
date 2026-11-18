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

    // ---- merge ----
    private fun full(start: Long, end: Long, km: Double, litres: Double?, ice: Double?) =
        TripEstimate(start, end, km, kwh = 2.0, litres = litres, hevKm = ice, iceKm = ice)

    @Test fun mergeFillsOwnersCase() {
        val r = TripRow(t(8, 0), t(8, 40), 20.0, 3.0, null, null)
        val m = TripMatch.merge(listOf(r), listOf(full(t(8, 1), t(8, 39), 10.0, 0.8, 4.0)))
        assertEquals(1, m.size)
        assertEquals(20.0, m[0].km!!, 1e-9)
        assertEquals(3.0, m[0].kwh!!, 1e-9)
        assertEquals(0.8, m[0].litres!!, 1e-9)
        assertEquals(4.0, m[0].hevKm!!, 1e-9)
    }

    @Test fun mergeKeepsRecordedLitres() {
        val r = TripRow(t(8, 0), t(8, 40), 10.0, 3.0, 1.5, null)
        val m = TripMatch.merge(listOf(r), listOf(full(t(8, 0), t(8, 40), 10.0, 0.8, 4.0)))
        assertEquals(1.5, m[0].litres!!, 1e-9)
        assertEquals(4.0, m[0].hevKm!!, 1e-9)
    }

    @Test fun mergeNoOverlapUnchanged() {
        val r = rec(t(8, 0), t(8, 30))
        assertEquals(listOf(r), TripMatch.merge(listOf(r), listOf(full(t(9, 0), t(9, 30), 10.0, 0.8, 4.0))))
    }

    @Test fun mergeSplitsEstimateAcrossFragmentsByKm() {
        val a = TripRow(t(8, 0), t(8, 20), 3.0, null, null, null)
        val b = TripRow(t(8, 30), t(8, 50), 7.0, null, null, null)
        val m = TripMatch.merge(listOf(a, b), listOf(full(t(8, 0), t(8, 55), 10.0, 1.0, 9.0)))
        assertEquals(0.3, m[0].litres!!, 1e-9)
        assertEquals(2.7, m[0].hevKm!!, 1e-9)
        assertEquals(0.7, m[1].litres!!, 1e-9)
        assertEquals(6.3, m[1].hevKm!!, 1e-9)
        assertEquals(0.6, m[0].kwh!!, 1e-9)
    }

    @Test fun mergeShareCappedWhenRecordLongerThanEstimate() {
        val r = TripRow(t(8, 0), t(9, 0), 30.0, null, null, null)
        val m = TripMatch.merge(listOf(r), listOf(full(t(8, 10), t(8, 40), 10.0, 1.0, 9.0)))
        assertEquals(1.0, m[0].litres!!, 1e-9)
        assertEquals(9.0, m[0].hevKm!!, 1e-9)
    }

    @Test fun mergeMergedRowsAreNotPending() {
        val a = TripRow(t(8, 0), t(8, 20), 3.0, null, null, null)
        val b = TripRow(t(8, 30), t(8, 50), 7.0, null, null, null)
        val e = full(t(8, 0), t(8, 55), 10.0, 1.0, 9.0)
        assertTrue(TripMatch.pending(LiveTripState(null, listOf(e)), listOf(a, b), t(10, 0)).isEmpty())
    }

    @Test fun mergeRowTakesLargerOverlapEstimate() {
        val r = TripRow(t(8, 0), t(9, 0), 10.0, null, null, null)
        val a = full(t(7, 50), t(8, 10), 5.0, 0.1, 1.0)
        val b = full(t(8, 15), t(8, 55), 5.0, 0.9, 2.0)
        val m = TripMatch.merge(listOf(r), listOf(a, b))
        assertEquals(0.9, m[0].litres!!, 1e-9)
        assertEquals(2.0, m[0].hevKm!!, 1e-9)
    }

    @Test fun mergeWithoutIceKmKeepsRowHevKm() {
        val r = TripRow(t(8, 0), t(8, 40), 10.0, 3.0, null, 7.0)
        val e = TripEstimate(t(8, 0), t(8, 40), 10.0, 1.0, 0.8, hevKm = 2.0, iceKm = null)
        val m = TripMatch.merge(listOf(r), listOf(e))
        assertEquals(7.0, m[0].hevKm!!, 1e-9)
        assertEquals(0.8, m[0].litres!!, 1e-9)
    }

    @Test fun mergeOpenEndedRowMatchesByStart() {
        val r = TripRow(t(8, 10), null, 10.0, null, null, null)
        val m = TripMatch.merge(listOf(r), listOf(full(t(8, 0), t(9, 0), 10.0, 0.8, 4.0)))
        assertEquals(0.8, m[0].litres!!, 1e-9)
    }
}
