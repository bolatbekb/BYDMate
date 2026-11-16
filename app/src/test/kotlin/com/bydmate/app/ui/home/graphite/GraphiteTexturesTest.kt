package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphiteTexturesTest {
    private fun alphas(t: GrTile) = t.argb.map { it ushr 24 }

    private fun check(t: GrTile, w: Int, h: Int, cap: Int) {
        assertEquals(w, t.width)
        assertEquals(h, t.height)
        assertEquals(w * h, t.argb.size)
        val a = alphas(t)
        assertTrue("alpha ${a.max()} over $cap", a.max() <= cap)
        assertTrue("an empty tile", a.any { it > 0 })
        // Only black or white specks: the scheme's own fill shows through.
        assertTrue(t.argb.all { (it and 0xFFFFFF) == 0xFFFFFF || (it and 0xFFFFFF) == 0 })
    }

    @Test fun sandFine() = check(GrTextures.sandFine(), 160, 160, GrTextures.SAND_FINE_MAX)

    @Test fun metal() = check(GrTextures.metal(), 256, 64, GrTextures.METAL_MAX)

    @Test fun concrete() = check(GrTextures.concrete(), 256, 256, GrTextures.CONCRETE_MAX)

    @Test fun capsStaySubtle() {
        assertEquals(40, GrTextures.SAND_FINE_MAX)
        assertTrue(GrTextures.METAL_MAX <= 13) // 5 %
        assertTrue(GrTextures.CONCRETE_MAX <= 24) // pores ≈ 8 %
    }

    @Test fun deterministic() {
        assertArrayEquals(GrTextures.sandFine().argb, GrTextures.sandFine().argb)
        assertArrayEquals(GrTextures.metal().argb, GrTextures.metal().argb)
        assertArrayEquals(GrTextures.concrete().argb, GrTextures.concrete().argb)
    }

    /** Brushed metal: mostly very low streaks, a few brighter ones. */
    @Test fun metalIsMostlyFaint() {
        val a = alphas(GrTextures.metal())
        val faint = a.count { it <= 4 }
        assertTrue("faint $faint", faint > a.size / 2)
        assertTrue(a.any { it >= 9 })
    }

    /** The streaks run along the rows: neighbours in a row differ less than neighbours in a column. */
    @Test fun metalStreaksAreHorizontal() {
        val t = GrTextures.metal()
        fun at(x: Int, y: Int) = t.argb[y * t.width + x].let { (it ushr 24) * if (it and 0xFFFFFF == 0) -1 else 1 }
        var along = 0L
        var across = 0L
        for (y in 0 until t.height - 1) for (x in 0 until t.width - 1) {
            along += kotlin.math.abs(at(x + 1, y) - at(x, y))
            across += kotlin.math.abs(at(x, y + 1) - at(x, y))
        }
        assertTrue("along $along, across $across", along * 3 < across)
    }

    /** Concrete has dark pores and light-and-dark mottling. */
    @Test fun concreteHasPores() {
        val t = GrTextures.concrete()
        assertTrue(t.argb.any { (it and 0xFFFFFF) == 0 && (it ushr 24) >= 16 })
        assertTrue(t.argb.any { (it and 0xFFFFFF) == 0xFFFFFF && (it ushr 24) > 0 })
    }
}
