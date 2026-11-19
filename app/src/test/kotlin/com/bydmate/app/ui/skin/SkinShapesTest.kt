package com.bydmate.app.ui.skin

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinShapesTest {
    @Test fun classicIsTheFoundationShape() {
        assertEquals(RoundedCornerShape(12.dp), shapeFor(SkinCorners(), 12.dp))
    }

    @Test fun scaledRounded() {
        assertEquals(RoundedCornerShape(18.dp), shapeFor(SkinCorners(CornerStyle.ROUNDED, 1.5f), 12.dp))
    }

    @Test fun cutStyleCuts() {
        assertTrue(shapeFor(SkinCorners(CornerStyle.CUT, 1f), 12.dp) is CutCornerShape)
    }

    @Test fun zeroStaysZero() {
        assertEquals(RoundedCornerShape(0.dp), shapeFor(SkinCorners(CornerStyle.ROUNDED, 2f), 0.dp))
    }

    @Test fun percentKeptWhenRounded() {
        // the percent overload follows the active skin, which is classic in unit tests
        assertEquals(RoundedCornerShape(50), com.bydmate.app.ui.skin.RoundedCornerShape(50))
    }
}
