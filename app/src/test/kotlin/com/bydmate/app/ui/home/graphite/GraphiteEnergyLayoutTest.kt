package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphiteEnergyLayoutTest {
    private val eps = 1e-3f

    @Test fun pairIsCentredInTheSlotWithoutOverlap() {
        val b = barPair(slotLeft = 100f, slot = 94f, n = 7, hasFuel = true)
        assertEquals(b.kwhW, b.fuelW, eps)
        assertEquals(4f, b.fuelX - (b.kwhX + b.kwhW), eps) // 4 px gap, kWh left, litres right
        val left = b.kwhX - 100f
        val right = 100f + 94f - (b.fuelX + b.fuelW)
        assertEquals(left, right, eps)
        assertTrue(left >= 0f)
    }

    @Test fun singleCentredBarWithoutFuel() {
        val b = barPair(slotLeft = 0f, slot = 94f, n = 7, hasFuel = false)
        assertEquals(0f, b.fuelW, eps)
        assertEquals(39.48f, b.kwhW, eps) // min(40, 94 × 0.42)
        assertEquals((94f - b.kwhW) / 2, b.kwhX, eps)
    }

    @Test fun pairWidthsForWeekDayMonth() {
        assertEquals(18f, barPair(0f, 94f, 7, true).kwhW, eps) // min(18, 18.8)
        assertEquals(11f, barPair(0f, 55f, 12, true).kwhW, eps) // 55 × 0.2
        assertEquals(5.28f, barPair(0f, 22f, 30, true).kwhW, eps) // 22 × 0.24
        assertEquals(3f, barPair(0f, 10f, 30, true).kwhW, eps) // never thinner than 3
    }

    @Test fun singleBarWidthsForDayMonth() {
        assertEquals(23.1f, barPair(0f, 55f, 12, false).kwhW, eps) // 55 × 0.42
        assertEquals(11f, barPair(0f, 22f, 30, false).kwhW, eps) // 22 × 0.5
    }
}
