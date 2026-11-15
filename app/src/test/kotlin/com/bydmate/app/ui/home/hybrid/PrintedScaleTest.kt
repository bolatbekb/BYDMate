package com.bydmate.app.ui.home.hybrid

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintedScaleTest {
    private val scale = PrintedScale(0f, 0f, 1f, 2f, listOf(0.0 to 140f, 1.0 to 120f, 3.0 to 40f))

    @Test
    fun `a printed mark gives its own angle`() {
        assertEquals(120f, scale.angleOf(1.0), 0.001f)
    }

    @Test
    fun `between marks the angle is linear in that segment`() {
        assertEquals(80f, scale.angleOf(2.0), 0.001f)
        assertEquals(130f, scale.angleOf(0.5), 0.001f)
    }

    @Test
    fun `unknown and out of range values stay on the scale`() {
        assertEquals(140f, scale.angleOf(null), 0.001f)
        assertEquals(140f, scale.angleOf(-4.0), 0.001f)
        assertEquals(40f, scale.angleOf(9.0), 0.001f)
    }

    @Test
    fun `compressor scale tops out at its last printed number`() {
        assertEquals(35.4f, CompressorScale.angleOf(5.0), 0.001f)
        assertEquals(87.4f, CompressorScale.angleOf(2.0), 0.001f)
    }
}
