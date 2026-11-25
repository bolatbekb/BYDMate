package com.bydmate.app.fork.cells.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CellsSettingsTextTest {

    @Test fun `the four data rows show, no car row`() {
        // Model choice moved to «Мой автомобиль»; the Service block no longer has a CAR row.
        assertEquals(listOf(BlockRow.COLLECT, BlockRow.INTERVAL, BlockRow.VIEW, BlockRow.STATS), visibleRows())
    }

    @Test fun `size text has one decimal`() {
        assertEquals("0,6 МБ", sizeText(614_400, Locale("ru")))
        assertEquals("0.6 MB", sizeText(614_400, Locale.US))
    }
}
