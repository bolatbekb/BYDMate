package com.bydmate.app.ui.home.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MockupFrameTest {
    private fun check(w: Float, h: Float, want: FluidFrame, base: Float = 1208f) {
        val f = fluidFrame(w, h, base)
        assertEquals(want.scale, f.scale, 0.0001f)
        assertEquals(want.heightPx, f.heightPx, 0.001f)
        assertEquals(want.extraPx, f.extraPx, 0.001f)
    }

    @Test fun `the car's content area is today's frame`() = check(1280f, 604f, FluidFrame(1f, 1208f, 0f))

    @Test fun `a taller area gives the full extra`() = check(1280f, 720f, FluidFrame(1f, 1440f, 232f))

    @Test fun `a part of the extra`() = check(1280f, 680f, FluidFrame(1f, 1360f, 152f))

    @Test fun `above 1440 the rest letterboxes`() = check(1280f, 800f, FluidFrame(1f, 1440f, 232f))

    @Test fun `too short keeps the min scale`() = check(1280f, 500f, FluidFrame(500f / 604f, 1208f, 0f))

    @Test fun `a larger screen scales by width`() = check(1920f, 1080f, FluidFrame(1.5f, 1440f, 232f))

    @Test fun `base 1080 - the car is unchanged`() = check(1280f, 540f, FluidFrame(1f, 1080f, 0f), 1080f)

    @Test fun `base 1080 - a 2560x1440 screen under the bar`() = check(1280f, 656f, FluidFrame(1f, 1312f, 232f), 1080f)

    @Test fun `base 1080 - a part of the extra`() = check(1280f, 600f, FluidFrame(1f, 1200f, 120f), 1080f)

    @Test fun `base 1080 - above the max the rest letterboxes`() = check(1280f, 700f, FluidFrame(1f, 1312f, 232f), 1080f)

    @Test fun `base 1080 - too short keeps the min scale`() = check(1280f, 500f, FluidFrame(500f / 540f, 1080f, 0f), 1080f)

    @Test fun `a wide short area keeps the min scale`() = check(2000f, 600f, FluidFrame(600f / 604f, 1208f, 0f))
}
