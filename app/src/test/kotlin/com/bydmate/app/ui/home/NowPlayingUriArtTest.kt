package com.bydmate.app.ui.home

import android.graphics.Bitmap
import android.media.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NowPlayingUriArtTest {
    private fun bmp() = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

    @Test fun bitmapWinsOverUri() {
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://cdn.example/a.jpg")
            .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, bmp())
            .build()
        val pick = pickArt(md)
        assertEquals(MediaMetadata.METADATA_KEY_DISPLAY_ICON, pick.bitmapKey)
        assertNull(pick.uri)
    }

    @Test fun bitmapOrderAlbumArtFirst() {
        val md = MediaMetadata.Builder()
            .putBitmap(MediaMetadata.METADATA_KEY_ART, bmp())
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp())
            .build()
        assertEquals(MediaMetadata.METADATA_KEY_ALBUM_ART, pickArt(md).bitmapKey)
    }

    @Test fun uriOrderAlbumArtUriFirst() {
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, "content://icon")
            .putString(MediaMetadata.METADATA_KEY_ART_URI, "content://art")
            .build()
        val pick = pickArt(md)
        assertNull(pick.bitmapKey)
        assertEquals("content://art", pick.uri)
        assertEquals(MediaMetadata.METADATA_KEY_ART_URI, pick.uriKey)
    }

    @Test fun schemeRouting() {
        assertEquals(UriKind.LOCAL, uriKind("content://media/external/audio/albumart/12"))
        assertEquals(UriKind.LOCAL, uriKind("android.resource://com.player/drawable/cover"))
        assertEquals(UriKind.LOCAL, uriKind("file:///sdcard/Music/cover.jpg"))
        assertEquals(UriKind.HTTP, uriKind("https://cdn.example/a.jpg?token=secret"))
        assertEquals(UriKind.HTTP, uriKind("HTTP://cdn.example/a.jpg"))
        assertEquals(UriKind.UNSUPPORTED, uriKind("ftp://x/y.jpg"))
        assertEquals(UriKind.UNSUPPORTED, uriKind("not a uri"))
    }

    @Test fun logLineHasSchemesOnly() {
        val md = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "https://cdn.example/a.jpg?token=secret123")
            .putString(MediaMetadata.METADATA_KEY_ART_URI, "content://media/external/42")
            .build()
        val line = artLogLine("com.player", pickArt(md), "uri failed: http 403")
        assertTrue(line, line.contains("com.player"))
        assertTrue(line, line.contains("https"))
        assertTrue(line, line.contains("uri failed: http 403"))
        assertFalse(line, line.contains("cdn.example"))
        assertFalse(line, line.contains("token"))
        assertFalse(line, line.contains("secret123"))
        assertFalse(line, line.contains("media/external"))
    }

    @Test fun failedUriIsNotRetriedForTheSameKey() {
        val s = UriArtState()
        assertEquals(UriArtState.Step.START, s.request("k1"))
        assertEquals(UriArtState.Step.PENDING, s.request("k1"))
        s.done("k1", null)
        assertEquals(UriArtState.Step.FAILED, s.request("k1"))
        assertEquals(UriArtState.Step.FAILED, s.request("k1"))
        // a new track tries again
        assertEquals(UriArtState.Step.START, s.request("k2"))
    }

    @Test fun loadedUriIsKeptForItsKeyOnly() {
        val s = UriArtState()
        val b = bmp()
        s.request("k1")
        s.done("k1", b)
        assertEquals(UriArtState.Step.READY, s.request("k1"))
        assertSame(b, s.bitmap("k1"))
        // a late result for an old key is dropped
        s.request("k2")
        s.done("k1", bmp())
        assertEquals(UriArtState.Step.PENDING, s.request("k2"))
        assertNull(s.bitmap("k1"))
    }

    @Test fun abandonedLoadStartsAgain() {
        val s = UriArtState()
        assertEquals(UriArtState.Step.START, s.request("k1"))
        s.forget("k1")
        assertEquals(UriArtState.Step.START, s.request("k1"))
    }
}
