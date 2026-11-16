package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoMatrixTest {
    @Test
    fun identity() {
        val m = photoMatrix(PhotoLook(1f, 1f, 1f), Color.White)
        val expected = FloatArray(20).also { for (i in 0..3) it[i * 5 + i] = 1f }
        for (i in 0 until 20) assertEquals("cell $i", expected[i], m[i], 1e-4f)
    }

    @Test
    fun tintMultipliesChannels() {
        val m = photoMatrix(PhotoLook(1f, 1f, 1f), Color(0xFF808080))
        for (i in 0..2) assertEquals(0.502f, m[i * 5 + i], 1e-3f)
        assertEquals(1f, m[18], 1e-4f)
    }
}
