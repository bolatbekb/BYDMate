package com.bydmate.app.fork.ledger.ui

import com.bydmate.app.fork.ledger.domain.Prefill
import com.bydmate.app.fork.ledger.domain.PrefillSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerEditorStateTest {
    private val now = 1_800_000_000_000L
    private fun fresh() = LedgerEditorState.new(now)

    @Test fun prefillFillsEmptyOdometer() {
        val s = fresh().applyPrefill(Prefill(15123.0, 40.5, PrefillSource.LIVE))
        assertEquals("15123", s.odometer)
        assertEquals(40.5, s.carFuelTotalL!!, 0.0)
        assertFalse(s.odometerEditedByUser)
    }

    @Test fun manualOdometerSurvivesPrefill() {
        val s = fresh().onOdometerTyped("15000").applyPrefill(Prefill(15123.0, 41.0, PrefillSource.SNAPSHOT))
        assertEquals("15000", s.odometer)
        assertTrue(s.odometerEditedByUser)
        // 123 km away from the prefilled odometer: the car counter belongs to another moment
        assertNull(s.carFuelTotalL)
    }

    @Test fun twoOfThreeFieldsMakeRefuel() {
        val r = fresh().copy(liters = "40", price = "2,5").toRefuel()
        assertNotNull(r)
        assertEquals(40.0, r!!.liters, 0.0)
        assertEquals(100.0, r.totalCost!!, 0.0)
        assertEquals(2.5, r.pricePerLiter!!, 0.0)
        assertEquals("manual", r.source)
        assertEquals("confirmed", r.status)
        assertEquals(now, r.date)
    }

    @Test fun oneFieldIsNotEnough() {
        assertNull(fresh().copy(liters = "40").toRefuel())
    }

    @Test fun nonPositiveValuesAreRejected() {
        val s = fresh().copy(liters = "-5", total = "0", price = "2", odometer = "0")
        assertEquals(
            setOf(RefuelField.LITERS, RefuelField.TOTAL, RefuelField.ODOMETER),
            s.errors(),
        )
        assertNull(s.toRefuel())
    }

    @Test fun editingKeepsIdentityAndCounter() {
        val s = fresh().applyPrefill(Prefill(500.0, 33.0, PrefillSource.DRAFT))
        val base = s.copy(liters = "10", total = "25").toRefuel()!!.copy(id = 7, source = "detected")
        val again = LedgerEditorState.from(base).copy(total = "26").toRefuel()!!
        assertEquals(7L, again.id)
        assertEquals("detected", again.source)
        assertEquals(33.0, again.carFuelTotalL!!, 0.0)
        assertEquals(500.0, again.odometerKm!!, 0.0)
        assertEquals(26.0, again.totalCost!!, 0.0)
        assertEquals(2.6, again.pricePerLiter!!, 0.0)
    }

    private fun draft() = fresh().copy(liters = "1", total = "1").toRefuel()!!.copy(
        id = 9, odometerKm = 500.0, liters = 25.0, totalCost = null, pricePerLiter = null, fullTank = false,
        carFuelTotalL = 40.0, tankPercentBefore = 20, tankPercentAfter = 70, source = "detected", status = "draft",
    )

    @Test fun draftEstimateIsAHintNotLiters() {
        val s = LedgerEditorState.from(draft())
        assertEquals("", s.liters)
        assertEquals(25.0, s.sensorLiters!!, 0.0)
        // a total alone must not be priced against the sensor estimate
        assertNull(s.copy(total = "60").toRefuel())
        val r = s.copy(total = "60", liters = "24").toRefuel()!!
        assertEquals(24.0, r.liters, 0.0)
        assertEquals(2.5, r.pricePerLiter!!, 0.0)
    }

    @Test fun inconsistentTripleIsRejected() {
        val s = fresh().copy(liters = "40", price = "2.5", total = "120")
        assertEquals(setOf(RefuelField.MISMATCH), s.errors())
        assertNull(s.toRefuel())
    }

    @Test fun tripleWithinRoundingIsAccepted() {
        val r = fresh().copy(liters = "33.33", price = "3", total = "100").toRefuel()!!
        assertEquals(100.0, r.totalCost!!, 0.0)
        assertEquals(33.33, r.liters, 0.0)
    }

    @Test fun retypedOdometerDropsCounter() {
        val s = fresh().applyPrefill(Prefill(15123.0, 40.5, PrefillSource.LIVE)).onOdometerTyped("15100")
        assertNull(s.carFuelTotalL)
        assertNull(s.copy(liters = "40", total = "100").toRefuel()!!.carFuelTotalL)
    }

    @Test fun retypeWithinOneKmKeepsCounter() {
        val s = fresh().applyPrefill(Prefill(15123.0, 40.5, PrefillSource.LIVE))
        assertEquals(40.5, s.onOdometerTyped("15123,8").carFuelTotalL!!, 0.0)
        // cleared and typed back: the counter returns
        assertEquals(40.5, s.onOdometerTyped("").onOdometerTyped("15123").carFuelTotalL!!, 0.0)
    }

    @Test fun storedEntryRetypedOdometerDropsCounter() {
        val s = LedgerEditorState.from(draft())
        assertEquals(40.0, s.carFuelTotalL!!, 0.0)
        assertNull(s.onOdometerTyped("530").carFuelTotalL)
    }

    @Test fun newRefuelOpensTheOpenDraft() {
        val d = draft()
        val confirmed = d.copy(id = 3, status = "confirmed", source = "manual")
        val s = LedgerEditorState.forNewRefuel(listOf(confirmed, d), now)
        assertEquals(d, s.base)
        assertEquals(25.0, s.sensorLiters!!, 0.0)
        val blank = LedgerEditorState.forNewRefuel(listOf(confirmed), now)
        assertNull(blank.base)
        assertEquals(now, blank.dateMs)
    }
}
