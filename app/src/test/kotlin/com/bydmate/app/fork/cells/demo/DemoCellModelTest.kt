package com.bydmate.app.fork.cells.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCellModelTest {
    @Test fun `ocv follows the plateau of the real car`() {
        assertTrue(DemoCellModel.ocvMv(56.0) in 3271..3275)
        assertEquals(3440, DemoCellModel.ocvMv(100.0))
        val curve = (0..100).map { DemoCellModel.ocvMv(it.toDouble()) }
        assertTrue(curve.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun `the real arrays have the pack size`() {
        assertEquals(184, DemoCellModel.REAL_CELLS_MV.size)
        assertEquals(24, DemoCellModel.REAL_PROBES_C.size)
    }

    @Test fun `cells sag under 1C and the weak cell sags most`() {
        val rest = DemoCellModel.cellsMv(56.0, 0.0, 1.0)
        val load = DemoCellModel.cellsMv(56.0, 54.0, 1.0)
        val others = (0 until 184).filter { it != 91 }
        val medianDrop = others.map { rest[it] - load[it] }.sorted()[others.size / 2]
        assertEquals(59.0, medianDrop.toDouble(), 3.0)
        val gap = others.map { load[it] }.sorted()[others.size / 2] - load[91]
        assertEquals(43.0, gap.toDouble(), 3.0)
    }

    @Test fun `the weak cell at rest sits 40 mV low on the plateau and twice that on top`() {
        val plateau = DemoCellModel.cellsMv(56.0, 0.0, 1.0)
        assertTrue(plateau.max() - plateau.min() in 40..50)
        val top = DemoCellModel.cellsMv(100.0, 0.0, 1.0)
        assertTrue(top.max() > 3400 && top.max() - top.min() in 80..90)
    }

    @Test fun `probes are shifted to the wanted average`() {
        val p = DemoCellModel.probesC(8)
        assertEquals(8.0, p.average(), 0.6)
    }
}
