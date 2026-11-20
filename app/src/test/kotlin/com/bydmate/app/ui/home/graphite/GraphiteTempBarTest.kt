package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteTempBarTest {
    @Test fun unknownValueFillsNothing() = assertEquals(0f, tempFillWidth(300f, null), 0f)
    @Test fun halfFillsHalf() = assertEquals(150f, tempFillWidth(300f, 0.5f), 0f)
    @Test fun belowZeroIsClamped() = assertEquals(0f, tempFillWidth(300f, -0.2f), 0f)
    @Test fun aboveOneIsClamped() = assertEquals(300f, tempFillWidth(300f, 1.4f), 0f)
}
