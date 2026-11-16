package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.fork.ledger.data.RefuelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelSegmentsTest {
    /** One receipt as a test states it; [entity] fills in the rest of the row. */
    private data class Fill(
        val date: Long, val odo: Double?, val liters: Double, val full: Boolean = true, val total: Double? = null,
        val counter: Double? = null, val missed: Boolean = false, val status: String = "confirmed",
    ) {
        fun entity() = RefuelEntity(
            date = date, odometerKm = odo, liters = liters, totalCost = total, pricePerLiter = null,
            fullTank = full, missedBefore = missed, carFuelTotalL = counter, tankPercentBefore = null,
            tankPercentAfter = null, lat = null, lon = null, place = null, note = null,
            source = "manual", status = status, importKey = null,
        )
    }

    private fun segments(vararg fills: Fill) = RefuelSegments.build(fills.map(Fill::entity))

    @Test fun segmentBetweenTwoFullTanks() {
        val s = segments(
            Fill(1, 12135.0, 40.0, counter = 1000.0),
            Fill(2, 12474.0, 42.23, total = 12922.07, counter = 1041.0),
        ).single()
        assertEquals(339.0, s.distanceKm!!, 1e-9)
        assertEquals(42.23, s.litersBought, 1e-9)
        assertEquals(41.0, s.litersBurnedCar!!, 1e-9)
        assertEquals(12.46, s.per100Receipts!!, 0.01)
        assertEquals(12.09, s.per100Car!!, 0.01)
        assertEquals(3.0, s.mismatchPct!!, 0.01)
        assertEquals(38.12, s.costPerKm!!, 0.01)
        assertTrue(s.complete)
    }

    @Test fun partialFillsAreSummedIntoNextFull() {
        val s = segments(
            Fill(1, 1000.0, 40.0), Fill(2, 1200.0, 50.0, full = false), Fill(3, 1500.0, 71.9),
        ).single()
        assertEquals(121.9, s.litersBought, 1e-9)
    }

    @Test fun missedBeforeBreaksSegment() {
        assertTrue(segments(Fill(1, 1000.0, 40.0), Fill(2, 1500.0, 40.0, missed = true)).isEmpty())
    }

    @Test fun noCarCounterGivesReceiptsOnly() {
        val s = segments(Fill(1, 1000.0, 40.0), Fill(2, 1500.0, 50.0)).single()
        assertNull(s.litersBurnedCar); assertNull(s.mismatchPct); assertNull(s.per100Car)
        assertEquals(10.0, s.per100Receipts!!, 1e-9)
    }

    @Test fun decreasingOdometerMarksIncomplete() {
        val s = segments(Fill(1, 2000.0, 40.0), Fill(2, 1500.0, 50.0)).single()
        assertFalse(s.complete)
        assertNull(s.distanceKm); assertNull(s.per100Receipts); assertNull(s.costPerKm)
    }

    @Test fun negativeCounterDeltaDropsCarSide() {
        val s = segments(
            Fill(1, 1000.0, 40.0, counter = 500.0), Fill(2, 1500.0, 50.0, counter = 400.0),
        ).single()
        assertNull(s.litersBurnedCar); assertNull(s.mismatchPct); assertNull(s.per100Car)
        assertTrue(s.complete)
        assertNotNull(s.per100Receipts)
    }

    @Test fun draftsAreIgnored() {
        val s = segments(
            Fill(1, 1000.0, 40.0), Fill(2, 1200.0, 30.0, status = "draft"), Fill(3, 1500.0, 50.0),
        ).single()
        assertEquals(50.0, s.litersBought, 1e-9)
    }
}
