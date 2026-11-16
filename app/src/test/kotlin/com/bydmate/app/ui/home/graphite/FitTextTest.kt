package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Test

class FitTextTest {
    @Test fun longTitleShrinksToMinThenEllipsizes() {
        assertEquals(40f, fitFontSize(50f, 36f) { it <= 40f }, 0f)
        assertEquals(36f, fitFontSize(50f, 36f) { false }, 0f)
        assertEquals(50f, fitFontSize(50f, 36f) { true }, 0f)
    }
}
