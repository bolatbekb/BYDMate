package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFractionTest {
    @Test fun unknownIsEmpty() {
        assertEquals(0f, mediaFraction(null, 1000), 0f)
        assertEquals(0f, mediaFraction(500, null), 0f)
        assertEquals(0f, mediaFraction(500, 0), 0f)
    }

    @Test fun clampedFraction() {
        assertEquals(0.25f, mediaFraction(250, 1000), 0f)
        assertEquals(1f, mediaFraction(2000, 1000), 0f)
        assertEquals(0f, mediaFraction(-5, 1000), 0f)
    }
}
