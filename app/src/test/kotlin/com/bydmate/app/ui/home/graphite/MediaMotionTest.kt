package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMotionTest {
    @Test fun pausedKeepsPolled() {
        assertEquals(30_000L, extrapolatePosition(PolledPosition(30_000, 1_000, 200_000, playing = false), 5_000))
    }

    @Test fun playingAddsElapsed() {
        assertEquals(34_000L, extrapolatePosition(PolledPosition(30_000, 1_000, 200_000, playing = true), 5_000))
    }

    @Test fun clampedAtDuration() {
        assertEquals(200_000L, extrapolatePosition(PolledPosition(199_000, 0, 200_000, playing = true), 5_000))
    }

    @Test fun unknownDurationStillRunsButNeverNegative() {
        assertEquals(12_000L, extrapolatePosition(PolledPosition(10_000, 0, null, playing = true), 2_000))
        assertEquals(0L, extrapolatePosition(PolledPosition(-500, 0, null, playing = true), 0))
        // a clock that went backwards does not rewind the track
        assertEquals(10_000L, extrapolatePosition(PolledPosition(10_000, 5_000, null, playing = true), 1_000))
    }

    @Test fun playbackSpeedScalesElapsed() {
        assertEquals(36_000L, extrapolatePosition(PolledPosition(30_000, 1_000, 200_000, playing = true, speed = 1.5f), 5_000))
        assertEquals(32_000L, extrapolatePosition(PolledPosition(30_000, 1_000, 200_000, playing = true, speed = 0.5f), 5_000))
        assertEquals(30_000L, extrapolatePosition(PolledPosition(30_000, 1_000, 200_000, playing = false, speed = 2f), 5_000))
    }

    @Test fun seekOnlyPastTolerance() {
        assertFalse(isSeek(polledMs = 10_000, shownMs = 11_400))
        assertTrue(isSeek(polledMs = 10_000, shownMs = 11_600))
        assertTrue(isSeek(polledMs = 60_000, shownMs = 11_000))
    }

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
