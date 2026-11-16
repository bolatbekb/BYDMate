package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Test

class CdMeterTextTest {

    @Test fun `rpm is grouped by thousands with a narrow space`() {
        assertEquals("1\u202F760", rpmText(1760))
        assertEquals("850", rpmText(850))
        assertEquals("12\u202F000", rpmText(12000))
    }

    @Test fun `engine off or unknown rpm is a dash`() {
        assertEquals("—", rpmText(null))
        assertEquals("—", rpmText(0))
        assertEquals("—", rpmText(-5))
    }

    @Test fun `compressor power has one decimal`() {
        assertEquals("1.2", kwText(1.23))
        assertEquals("0.0", kwText(0.0))
        assertEquals("—", kwText(null))
        assertEquals("—", kwText(Double.NaN))
        assertEquals("0.0", kwText(-0.01))
        assertEquals("0.0", kwText(-0.0))
    }

    @Test fun `axis numbers drop a zero fraction`() {
        assertEquals("10", axisText(10.0))
        assertEquals("2.5", axisText(2.5))
        assertEquals("0", axisText(0.0))
    }

    @Test fun `axis ticks are nice and cover the maximum`() {
        assertEquals(AxisTicks(3, 30.0, 12.0), axisTicks(30.0, 12.0))
        val t = axisTicks(35.0, 14.0)
        assert(t.energyTop >= 35.0 && t.fuelTop >= 14.0)
        val e = axisTicks(45.0, null)
        assert(e.energyTop >= 45.0)
    }

    private fun series(fuel: List<Double?>?) =
        ChartSeries(listOf("", ""), listOf(null, null), fuel, 30.0, 12.0)

    @Test fun `a hybrid keeps its fuel axis on a period without trips`() {
        // a period without trips has no fuel list, the car still has a tank
        assertEquals(FuelSide(rightAxis = true, coverTitle = false, line = false), fuelSide(true, series(null)))
        assertEquals(FuelSide(rightAxis = true, coverTitle = false, line = false), fuelSide(true, null))
        assertEquals(FuelSide(rightAxis = true, coverTitle = false, line = true), fuelSide(true, series(listOf(5.0, null))))
    }

    @Test fun `a car without fuel hides the right axis and covers its title`() {
        assertEquals(FuelSide(rightAxis = false, coverTitle = true, line = false), fuelSide(false, series(null)))
        assertEquals(FuelSide(rightAxis = false, coverTitle = true, line = false), fuelSide(false, null))
        assertEquals(FuelSide(rightAxis = false, coverTitle = true, line = false), fuelSide(false, series(listOf(5.0, 6.0))))
    }
}
