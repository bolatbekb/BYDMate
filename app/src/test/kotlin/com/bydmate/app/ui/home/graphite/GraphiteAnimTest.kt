package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteAnimTest {
    private val eps = 1e-4f

    @Test fun growTotalIsStaggeredAndCapped() {
        assertEquals(400, growTotalMs(1))
        assertEquals(472, growTotalMs(7)) // 400 + 6 × 12
        assertEquals(532, growTotalMs(12))
        assertEquals(700, growTotalMs(30)) // 400 + 29 × 12 = 748 → cap
    }

    @Test fun barGrowthStartsPerBucket() {
        assertEquals(0f, barGrowth(0f, 0, 7), eps)
        assertEquals(0.5f, barGrowth(200f, 0, 7), eps)
        assertEquals(0f, barGrowth(72f, 6, 7), eps) // bucket 6 starts at 72 ms
        assertEquals(1f, barGrowth(472f, 6, 7), eps) // and ends with the total
        assertEquals(1f, barGrowth(700f, 29, 30), eps) // capped: the last one still finishes
    }

    @Test fun rpmFractionClamps() {
        assertEquals(0f, rpmFraction(null), eps)
        assertEquals(0f, rpmFraction(0), eps)
        assertEquals(0.2f, rpmFraction(1200), eps)
        assertEquals(1f, rpmFraction(9000), eps)
    }
}
