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
        // the hidden car counter still follows the prefill
        assertEquals(41.0, s.carFuelTotalL!!, 0.0)
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

    @Test fun editingKeepsDraftIdentityAndCounter() {
        val s = fresh().applyPrefill(Prefill(500.0, 33.0, PrefillSource.DRAFT))
        val base = s.copy(liters = "10", total = "25").toRefuel()!!.copy(id = 7, status = "draft", source = "detected")
        val again = LedgerEditorState.from(base).copy(total = "26").toRefuel()!!
        assertEquals(7L, again.id)
        assertEquals("detected", again.source)
        assertEquals(33.0, again.carFuelTotalL!!, 0.0)
        assertEquals(500.0, again.odometerKm!!, 0.0)
        assertEquals(26.0, again.totalCost!!, 0.0)
        assertEquals(2.6, again.pricePerLiter!!, 0.0)
    }
}
