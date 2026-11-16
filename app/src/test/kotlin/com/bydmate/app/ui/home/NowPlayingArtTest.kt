package com.bydmate.app.ui.home

import android.graphics.Bitmap
import android.media.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private fun md(bmp: Bitmap?) = MediaMetadata.Builder().apply {
        if (bmp != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
    }.build()

    private fun big() = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)

    @Test fun artScaledOncePerKey() {
        NowPlayingSource.artFor(null, "")
        val first = NowPlayingSource.artFor(md(big()), "t|a|b")
        assertNotNull(first)
        assertSame(first, NowPlayingSource.artFor(md(big()), "t|a|b"))
    }

    @Test fun lateCoverAppears() {
        NowPlayingSource.artFor(null, "")
        assertNull(NowPlayingSource.artFor(md(null), "t|a|b"))
        assertNotNull(NowPlayingSource.artFor(md(big()), "t|a|b"))
    }
}
