package com.bydmate.app.fork.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class MonthlySummaryTest {
    private val m = YearMonth.of(2026, 11)
    private fun row(km: Double, calc: Double? = null) = MonthlySummary.rows(
        listOf(m), MonthSources({ 100.0 }, { 200.0 }, { mapOf(1L to 20.0, 2L to 30.0) }, { km }, { calc }),
    ).single()

    @Test fun costPerKmNullWhenNoKm() { assertNull(row(0.0).costPerKm) }
    @Test fun totalIsSumOfParts() {
        val r = row(350.0)
        assertEquals(350.0, r.total, 1e-9)
        assertEquals(1.0, r.costPerKm!!, 1e-9)
    }
    @Test fun fuelCalculatedNotInTotal() { assertEquals(350.0, row(10.0, 999.0).total, 1e-9) }
    @Test fun totalsMergeCategories() {
        val t = MonthlySummary.totals(listOf(row(10.0, 5.0), row(10.0, null)))
        assertEquals(40.0, t.byCategory[1L]!!, 1e-9)
        assertEquals(5.0, t.fuelCalculated!!, 1e-9)
        assertEquals(700.0, t.total, 1e-9)
        assertNull(MonthlySummary.totals(listOf(row(1.0))).fuelCalculated)
    }
}
