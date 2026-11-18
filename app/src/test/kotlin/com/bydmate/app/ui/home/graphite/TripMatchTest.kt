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
}
