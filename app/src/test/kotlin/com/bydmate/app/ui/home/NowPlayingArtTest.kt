package com.bydmate.app.ui.home

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NowPlayingArtTest {
    @Test fun downscalesKeepingAspect() {
        val out = scaleArt(Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888))
        assertEquals(264, out.width)
        assertEquals(132, out.height)
    }

    @Test fun neverUpscales() {
        val src = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        assertSame(src, scaleArt(src))
    }
}
