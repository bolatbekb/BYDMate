package com.bydmate.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DayMileageTest {
    @Test
    fun `the first reading starts the day`() {
        assertEquals(DayStart(20261116, 17058.0), DayMileage.step(null, 20261116, 17058.0))
    }

    @Test
    fun `the same day keeps its start`() {
        val start = DayStart(20261116, 17058.0)
        assertSame(start, DayMileage.step(start, 20261116, 17204.8))
    }

    @Test
    fun `a new day starts from its own first reading`() {
        assertEquals(DayStart(20261117, 17204.8), DayMileage.step(DayStart(20261116, 17058.0), 20261117, 17204.8))
    }

    @Test
    fun `an odometer below the start restarts the day`() {
        assertEquals(DayStart(20261116, 120.0), DayMileage.step(DayStart(20261116, 17058.0), 20261116, 120.0))
    }
}
