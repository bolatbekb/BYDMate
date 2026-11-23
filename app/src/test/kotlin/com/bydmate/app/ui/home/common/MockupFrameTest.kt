package com.bydmate.app.ui.home.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockupFrameTest {
    private fun check(w: Float, h: Float, want: FluidFrame, base: Float) {
        val f = fluidFrame(w, h, base)
        assertEquals(want.scale, f.scale, 0.0001f)
        assertEquals(want.heightPx, f.heightPx, 0.001f)
        assertEquals(want.extraPx, f.extraPx, 0.001f)
    }

    @Test fun `the car's content area is today's frame`() = check(1280f, 604f, FluidFrame(1f, 1208f, 0f), 1208f)

    @Test fun `a taller area gives the full extra`() = check(1280f, 720f, FluidFrame(1f, 1440f, 232f), 1208f)

    @Test fun `a part of the extra`() = check(1280f, 680f, FluidFrame(1f, 1360f, 152f), 1208f)

    @Test fun `above 1440 the rest letterboxes`() = check(1280f, 800f, FluidFrame(1f, 1440f, 232f), 1208f)

    @Test fun `too short keeps the min scale`() = check(1280f, 500f, FluidFrame(500f / 604f, 1208f, 0f), 1208f)

    @Test fun `a larger screen scales by width`() = check(1920f, 1080f, FluidFrame(1.5f, 1440f, 232f), 1208f)

    @Test fun `base 1080 - the car is unchanged`() = check(1280f, 540f, FluidFrame(1f, 1080f, 0f), 1080f)

    @Test fun `base 1080 - a 2560x1440 screen under the bar`() = check(1280f, 656f, FluidFrame(1f, 1312f, 232f), 1080f)

    @Test fun `base 1080 - a part of the extra`() = check(1280f, 600f, FluidFrame(1f, 1200f, 120f), 1080f)

    @Test fun `base 1080 - above the max the rest letterboxes`() = check(1280f, 700f, FluidFrame(1f, 1312f, 232f), 1080f)

    @Test fun `base 1080 - too short keeps the min scale`() = check(1280f, 500f, FluidFrame(500f / 540f, 1080f, 0f), 1080f)

    @Test fun `a zero area never gives NaN`() = check(0f, 0f, FluidFrame(0f, 1080f, 0f), 1080f)

    @Test fun `a wide short area keeps the min scale`() = check(2000f, 600f, FluidFrame(600f / 604f, 1208f, 0f), 1208f)

    // first frame: Compose has no bars yet while the window reports the car's 112 / 120 px
    @Test fun `waits while compose has no bars the window already has`() =
        assertTrue(insetsPending(0, 0, 112, 120))

    @Test fun `draws once the bars match`() = assertFalse(insetsPending(112, 120, 112, 120))

    @Test fun `waits while only one bar arrived`() = assertTrue(insetsPending(112, 0, 112, 120))

    @Test fun `a screen without bars never waits`() = assertFalse(insetsPending(0, 0, 0, 0))

    @Test fun `unknown window insets never wait`() = assertFalse(insetsPending(0, 0, null, null))
}
