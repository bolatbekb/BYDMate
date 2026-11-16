package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpectrumDimTest {
    private val columns = 0 until SPECTRUM_COLUMNS

    @Test fun sameTimeAndColumnGiveTheSameDim() {
        for (c in columns) {
            assertEquals(spectrumDim(12.345, c, playing = true), spectrumDim(12.345, c, playing = true), 0f)
        }
    }

    @Test fun playingStaysWithinFifteenPercent() {
        var t = 0.0
        while (t < 60.0) {
            for (c in columns) {
                val d = spectrumDim(t, c, playing = true)
                assertTrue("t=$t c=$c d=$d", d in 0f..0.15f)
            }
            t += 0.07
        }
    }

    @Test fun eachColumnSwingsAtLeastEightPercent() {
        for (c in columns) {
            var lo = 1f
            var hi = 0f
            var t = 0.0
            while (t < 40.0) {
                val d = spectrumDim(t, c, playing = true)
                lo = minOf(lo, d)
                hi = maxOf(hi, d)
                t += 1.0 / 30
            }
            assertTrue("column $c swings ${hi - lo}", hi - lo >= 0.08f * 0.9f)
        }
    }

    @Test fun columnsAreNotInStep() {
        val a = spectrumDim(3.0, 10, playing = true)
        val differs = columns.any { abs(spectrumDim(3.0, it, playing = true) - a) > 0.02f }
        assertTrue(differs)
    }

    @Test fun smoothBetweenFrames() {
        val dt = 1.0 / 30
        for (c in columns) {
            var t = 0.0
            while (t < 20.0) {
                val step = abs(spectrumDim(t + dt, c, playing = true) - spectrumDim(t, c, playing = true))
                assertTrue("t=$t c=$c step=$step", step <= 0.01f)
                t += 0.25
            }
        }
    }

    @Test fun pausedIsAStaticTwentyPercent() {
        for (c in columns) {
            assertEquals(0.2f, spectrumDim(0.0, c, playing = false), 0f)
            assertEquals(0.2f, spectrumDim(97.5, c, playing = false), 0f)
        }
    }

    @Test fun blendEndsArePausedAndPlaying() {
        for (c in columns) {
            assertEquals(0.2f, spectrumBlendDim(4.2, c, 0f), 1e-6f)
            assertEquals(spectrumDim(4.2, c, playing = true), spectrumBlendDim(4.2, c, 1f), 1e-6f)
        }
    }

    @Test fun blendStaysBetweenTheEnds() {
        for (c in columns) {
            var b = 0f
            while (b <= 1f) {
                val d = spectrumBlendDim(7.7, c, b)
                assertTrue("c=$c b=$b d=$d", d in 0f..0.2f)
                b += 0.05f
            }
        }
    }
}
