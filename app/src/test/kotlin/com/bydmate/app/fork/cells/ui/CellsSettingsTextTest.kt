package com.bydmate.app.fork.cells.ui

import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CheckView
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CellsSettingsTextTest {
    private fun config(p: CarProfile) = CellsConfig(p, collect = false, intervalSec = 30, view = CheckView.WHAT_TO_DO)

    @Test fun `only the car row without a car`() {
        assertEquals(listOf(BlockRow.CAR), visibleRows(config(CarProfile.NONE)))
    }

    @Test fun `all rows with Leopard 5`() {
        assertEquals(BlockRow.entries, visibleRows(config(CarProfile.LEOPARD5_318)))
    }

    @Test fun `size text has one decimal`() {
        assertEquals("0,6 МБ", sizeText(614_400, Locale("ru")))
        assertEquals("0.6 MB", sizeText(614_400, Locale.US))
    }
}
