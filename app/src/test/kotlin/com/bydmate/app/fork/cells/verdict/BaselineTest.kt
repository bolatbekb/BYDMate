package com.bydmate.app.fork.cells.verdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineTest {
    private fun pts(vararg ages: Pair<Int, Int>) = ages.mapIndexed { i, (days, spread) -> sp(10L + i, NOON - days * DAY, spread) }

    @Test fun `base is the median of the oldest five to ten between 14 and 90 days`() {
        val p = pts(80 to 5, 70 to 6, 60 to 7, 50 to 8, 30 to 9, 5 to 99, 100 to 99)
        assertEquals(7, Baseline.of(m(10), ZoneRow.PLATEAU, p))
    }

    @Test fun `growth above 30 mV is watch`() {
        val p = pts(80 to 5, 70 to 6, 60 to 7, 50 to 8, 30 to 9)
        assertEquals(7, Baseline.of(m(38), ZoneRow.PLATEAU, p))
        val hi = CellVerdict.evaluate(m(38), hist(*p.toTypedArray()), java.time.ZoneOffset.UTC)
        assertEquals(listOf(Reason.SPREAD_ABOVE_NORM, Reason.BASE_GROWTH), hi.reasons)
        val lo = CellVerdict.evaluate(m(37), hist(*p.toTypedArray()), java.time.ZoneOffset.UTC)
        assertEquals(listOf(Reason.SPREAD_ABOVE_NORM), lo.reasons)
    }

    @Test fun `no base with fewer than five or only recent points`() {
        assertNull(Baseline.of(m(10), ZoneRow.PLATEAU, pts(80 to 5, 70 to 6, 60 to 7, 50 to 8)))
        assertNull(Baseline.of(m(10), ZoneRow.PLATEAU, pts(10 to 5, 10 to 6, 10 to 7, 10 to 8, 10 to 9)))
    }

    @Test fun `cold preliminary and other rows are ignored`() {
        val p = pts(80 to 5, 70 to 6, 60 to 7, 50 to 8, 30 to 9).mapIndexed { i, x -> if (i == 0) x.copy(cold = true) else x }
        assertNull(Baseline.of(m(10), ZoneRow.PLATEAU, p))
        assertNull(Baseline.of(m(10), ZoneRow.TOP, pts(80 to 5, 70 to 6, 60 to 7, 50 to 8, 30 to 9)))
    }
}
