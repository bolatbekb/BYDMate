package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteFluidTest {
    @Test fun chartGrowsWithExtra() {
        assertEquals(266f, chartHeight(0f), 0f)
        assertEquals(498f, chartHeight(232f), 0f)
    }

    @Test fun coverGrowsByHalf() {
        assertEquals(230f, coverSide(false, 0f), 0f)
        assertEquals(192f, coverSide(true, 0f), 0f)
        assertEquals(346f, coverSide(false, 232f), 0f)
    }

    @Test fun coverLeavesTheTitleRoom() {
        // side column 805 - padding 2*34 - gap 30 - cover
        for (compact in listOf(false, true)) assert(805f - 68f - 30f - coverSide(compact, 232f) >= 320f)
    }

    @Test fun gapsStretch() {
        assertEquals(RangeGaps.Regular, RangeGaps.Regular.stretched(0f))
        val r = RangeGaps.Regular
        val s = r.stretched(232f)
        assertEquals(r.number + 38, s.number)
        assertEquals(r.bar + 38, s.bar)
        assertEquals(r.scale + 38, s.scale)
        assertEquals(r.divider + 38, s.divider)
        assertEquals(r.gauges + 38, s.gauges)
        assertEquals(r.footer + 38, s.footer)
        assertEquals(r.bottom + 4, s.bottom)
        assertEquals(232, (s.number + s.bar + s.scale + s.divider + s.gauges + s.footer + s.bottom) -
            (r.number + r.bar + r.scale + r.divider + r.gauges + r.footer + r.bottom) + 0)
    }
}
