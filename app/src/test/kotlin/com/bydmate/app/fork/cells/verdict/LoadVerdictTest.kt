package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.MeasureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadVerdictTest {
    private fun load(spread: Int, id: Long = 1, lowest: Int = 91) =
        m(spread, id = id, ts = NOON - id * 60_000, kind = MeasureKind.LOAD, period = null, rest = null, lowest = lowest, currentA = 80.0)

    @Test fun `load is always preliminary`() {
        val ok = CellVerdict.evaluateLoad(load(50), emptyList())
        assertEquals(Level.OK, ok.level)
        assertTrue(ok.preliminary)
        val w = CellVerdict.evaluateLoad(load(51), emptyList())
        assertEquals(Level.WATCH, w.level)
        assertTrue(w.preliminary)
        assertEquals(Level.WATCH, CellVerdict.evaluate(load(51), hist()).level)
    }

    @Test fun `load service needs the same cell twice at 200 mV`() {
        val s = CellVerdict.evaluateLoad(load(200), listOf(load(210, id = 2)))
        assertEquals(Level.SERVICE, s.level)
        assertTrue(s.preliminary)
        val w = CellVerdict.evaluateLoad(load(200), listOf(load(210, id = 2, lowest = 30)))
        assertEquals(Level.WATCH, w.level)
    }

    @Test fun `cold load doubles norm and service and caps at watch`() {
        val ok = CellVerdict.evaluateLoad(m(90, kind = MeasureKind.LOAD, period = null, probe = 5), emptyList())
        assertEquals(Level.OK, ok.level)
        assertEquals(100, ok.limits.normMv)
        assertEquals(400, ok.limits.serviceMv)
        assertTrue(ok.limits.cold)
        val prev = m(450, id = 2, kind = MeasureKind.LOAD, period = null, probe = 5)
        val w = CellVerdict.evaluateLoad(m(450, kind = MeasureKind.LOAD, period = null, probe = 5), listOf(prev))
        assertEquals(Level.WATCH, w.level)
        assertTrue(Reason.COLD in w.reasons)
    }
}
