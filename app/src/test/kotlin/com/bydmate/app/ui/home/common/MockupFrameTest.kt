package com.bydmate.app.ui.home.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MockupFrameTest {
    private fun check(w: Float, h: Float, scale: Float, height: Float, extra: Float) {
        val f = fluidFrame(w, h)
        assertEquals(scale, f.scale, 0.0001f)
        assertEquals(height, f.heightPx, 0.001f)
        assertEquals(extra, f.extraPx, 0.001f)
    }

    @Test fun `the car's content area is today's frame`() = check(1280f, 604f, 1f, 1208f, 0f)

    @Test fun `a taller area gives the full extra`() = check(1280f, 720f, 1f, 1440f, 232f)

    @Test fun `a part of the extra`() = check(1280f, 680f, 1f, 1360f, 152f)

    @Test fun `above 1440 the rest letterboxes`() = check(1280f, 800f, 1f, 1440f, 232f)

    @Test fun `too short keeps the min scale`() = check(1280f, 500f, 500f / 604f, 1208f, 0f)

    @Test fun `a larger screen scales by width`() = check(1920f, 1080f, 1.5f, 1440f, 232f)
}
