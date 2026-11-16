package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotoneSplineTest {

    private fun checkNoOvershoot(ys: DoubleArray) {
        val xs = DoubleArray(ys.size) { it.toDouble() }
        val m = MonotoneSpline.tangents(xs, ys)
        for (k in xs.indices) {
            assertEquals("passes through point $k", ys[k], MonotoneSpline.at(xs, ys, m, xs[k]), 1e-9)
        }
        for (k in 0 until xs.size - 1) {
            val lo = minOf(ys[k], ys[k + 1])
            val hi = maxOf(ys[k], ys[k + 1])
            for (j in 1 until 50) {
                val x = xs[k] + j / 50.0
                val y = MonotoneSpline.at(xs, ys, m, x)
                assertTrue("segment $k at $x: $y outside [$lo, $hi]", y >= lo - 1e-9 && y <= hi + 1e-9)
            }
        }
    }

    @Test fun `steps and spikes stay within their neighbours`() {
        checkNoOvershoot(doubleArrayOf(18.0, 19.0, 15.0, 15.0, 30.0, 0.0, 0.0, 22.0, 21.9, 40.0))
    }

    @Test fun `monotone data stays monotone`() {
        checkNoOvershoot(doubleArrayOf(0.0, 0.1, 0.2, 10.0, 10.1, 30.0, 30.0, 31.0))
    }

    @Test fun `random data never overshoots`() {
        val r = java.util.Random(7)
        repeat(200) { checkNoOvershoot(DoubleArray(2 + r.nextInt(30)) { r.nextDouble() * 50.0 }) }
    }

    @Test fun `uneven x spacing never overshoots`() {
        val xs = doubleArrayOf(0.0, 0.3, 2.0, 2.2, 5.0)
        val ys = doubleArrayOf(5.0, 20.0, 3.0, 25.0, 25.0)
        val m = MonotoneSpline.tangents(xs, ys)
        for (k in 0 until xs.size - 1) {
            for (j in 0..40) {
                val x = xs[k] + (xs[k + 1] - xs[k]) * j / 40.0
                val y = MonotoneSpline.at(xs, ys, m, x)
                assertTrue(y >= minOf(ys[k], ys[k + 1]) - 1e-9 && y <= maxOf(ys[k], ys[k + 1]) + 1e-9)
            }
        }
    }

    @Test fun `two points give a straight line`() {
        val xs = doubleArrayOf(0.0, 1.0)
        val ys = doubleArrayOf(10.0, 20.0)
        val m = MonotoneSpline.tangents(xs, ys)
        assertEquals(15.0, MonotoneSpline.at(xs, ys, m, 0.5), 1e-9)
    }

    @Test fun `runs split at nulls`() {
        assertEquals(
            listOf(0..1, 3..3, 5..7),
            MonotoneSpline.runs(listOf(1.0, 2.0, null, 4.0, null, 5.0, 6.0, 7.0)),
        )
        assertEquals(emptyList<IntRange>(), MonotoneSpline.runs(listOf(null, null)))
    }
}
