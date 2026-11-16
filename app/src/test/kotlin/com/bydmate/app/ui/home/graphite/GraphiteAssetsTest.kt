package com.bydmate.app.ui.home.graphite

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GraphiteAssetsTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private val icons = listOf(
        "car", "route", "plug", "flow", "sliders", "battery", "fuel", "engine", "power", "gauge",
        "bolt", "leaf", "lock", "tree", "seat", "therm", "snow", "flame", "fan", "note", "bt",
        "wifi", "signal", "phone", "play", "pause", "prev", "next", "shuffle", "repeat", "heart",
        "volume", "wheel", "speed", "clock",
    )
    private val photos = listOf("hill", "desert", "front", "side_night", "lights")

    private fun photoId(n: String) =
        ctx.resources.getIdentifier("gr_photo_$n", "drawable", ctx.packageName)

    /** Robolectric's BitmapFactory does not decode WebP, so read the size from the RIFF header. */
    private fun webpSize(b: ByteArray): Pair<Int, Int> {
        fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        fun u24(o: Int) = u16(o) or ((b[o + 2].toInt() and 0xFF) shl 16)
        assertEquals("RIFF", String(b, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(b, 8, 4, Charsets.US_ASCII))
        return when (val fourCc = String(b, 12, 4, Charsets.US_ASCII)) {
            "VP8 " -> (u16(26) and 0x3FFF) to (u16(28) and 0x3FFF)
            "VP8L" -> {
                val bits = u16(21) or (u16(23) shl 16)
                ((bits and 0x3FFF) + 1) to (((bits shr 14) and 0x3FFF) + 1)
            }
            "VP8X" -> (u24(24) + 1) to (u24(27) + 1)
            else -> error("unknown WebP chunk $fourCc")
        }
    }

    @Test
    fun iconsInflate() {
        assertEquals(35, icons.size)
        val density = ctx.resources.displayMetrics.density
        for (n in icons) {
            val id = ctx.resources.getIdentifier("gr_ic_$n", "drawable", ctx.packageName)
            assertTrue("missing gr_ic_$n", id != 0)
            val d = AppCompatResources.getDrawable(ctx, id)
            assertTrue("gr_ic_$n is not a VectorDrawable", d is VectorDrawable)
            assertEquals("gr_ic_$n width", Math.round(24 * density), d!!.intrinsicWidth)
            assertEquals("gr_ic_$n height", Math.round(24 * density), d.intrinsicHeight)
        }
    }

    @Test
    fun photosAre920x440() {
        for (n in photos) {
            val id = photoId(n)
            assertTrue("missing gr_photo_$n", id != 0)
            val (w, h) = ctx.resources.openRawResource(id).use { webpSize(it.readBytes()) }
            assertEquals("gr_photo_$n width", 920, w)
            assertEquals("gr_photo_$n height", 440, h)
        }
    }

    @Test
    fun photosAreLight() {
        for (n in photos) {
            val id = photoId(n)
            assertTrue("missing gr_photo_$n", id != 0)
            val size = ctx.resources.openRawResource(id).use { it.available() }
            assertTrue("gr_photo_$n is $size bytes", size in 1..60_000)
        }
    }

    @Test
    fun interFontExists() {
        assertTrue(R.font.gr_inter != 0)
        assertNotNull(ctx.resources.openRawResource(R.font.gr_inter))
    }
}
