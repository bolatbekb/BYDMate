package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMotionTest {
    @Test fun equalizerStaysInRange() {
        for (bar in 0 until 4) {
            var t = 0f
            while (t < 30f) {
                val v = eqLevel(bar, t)
                assertTrue("bar $bar at $t: $v", v in 0.25f..1f)
                t += 0.083f
            }
        }
    }

    @Test fun equalizerBarsDiffer() {
        assertTrue(eqLevel(0, 1.3f) != eqLevel(1, 1.3f))
    }
}
