package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCellsTest {
    private val e = Color.Green
    private val t = Color.White
    private val empty = TripCardModel(TripCardMode.EMPTY, null, null, null, null, null, null)

    @Test fun noTripsYetStillShowsFourCells() {
        val cells = tripCells(empty, hasFuelTank = true, electric = e, primary = t)
        assertEquals(listOf("0 мин", "—", "—", "0 · 0"), cells.map { it.value })
        assertEquals(listOf("в пути", "электротяга", "средняя скорость", "кВт·ч · л"), cells.map { it.label })
    }

    @Test fun evWithoutTankShowsKwhOnly() {
        val cells = tripCells(empty, hasFuelTank = false, electric = e, primary = t)
        assertEquals("0", cells.last().value)
        assertEquals("кВт·ч", cells.last().label)
    }

    @Test fun realValues() {
        val m = TripCardModel(TripCardMode.CURRENT, 40.0, 72, 39, 68, 8.4, 2.1)
        val cells = tripCells(m, hasFuelTank = true, electric = e, primary = t)
        assertEquals(listOf("1 ч 12 мин", "68%", "39 км/ч", "8,4 · 2,1"), cells.map { it.value })
        assertEquals(e, cells[1].color)
    }

    @Test fun missingValueInCurrentReadsDash() {
        val m = TripCardModel(TripCardMode.CURRENT, 5.0, 10, 30, 50, null, 1.0)
        val cells = tripCells(m, hasFuelTank = true, electric = e, primary = t)
        assertTrue(cells[3].value.startsWith("—"))
    }
}
