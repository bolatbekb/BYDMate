package com.bydmate.app.ui.home.graphite

import com.bydmate.app.ui.home.nextWholeSecondDelayMs
import org.junit.Assert.assertEquals
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

    @Test fun labelWakesOnTheNextWholeSecondOfTheShownPosition() {
        assertEquals(1_001L, nextWholeSecondDelayMs(13_000, 1f))
        assertEquals(401L, nextWholeSecondDelayMs(13_600, 1f))
        assertEquals(201L, nextWholeSecondDelayMs(13_600, 2f))
        assertEquals(1_000L, nextWholeSecondDelayMs(null, 1f))
        assertEquals(1_000L, nextWholeSecondDelayMs(13_600, 0f))
        assertEquals(50L, nextWholeSecondDelayMs(13_990, 1f))
    }
}
