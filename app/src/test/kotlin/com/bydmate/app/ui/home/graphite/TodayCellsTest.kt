package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayCellsTest {
    private val e = Color.Green
    private val t = Color.White

    @Test fun noTripsYetStillShowsFourCells() {
        val cells = todayCells(null, hasFuelTank = true, electric = e, primary = t)
        assertEquals(listOf("0 мин", "—", "—", "0 · 0"), cells.map { it.value })
        assertEquals(listOf("в пути", "электротяга", "средняя скорость", "кВт·ч · л"), cells.map { it.label })
    }

    @Test fun evWithoutTankShowsKwhOnly() {
        val cells = todayCells(null, hasFuelTank = false, electric = e, primary = t)
        assertEquals("0", cells.last().value)
        assertEquals("кВт·ч", cells.last().label)
    }

    @Test fun realValues() {
        val s = TodayStats(minutes = 72, avgKmh = 39, electricShare = 68, kwh = 8.4, litres = 2.1)
        val cells = todayCells(s, hasFuelTank = true, electric = e, primary = t)
        assertEquals(listOf("1 ч 12 мин", "68%", "39 км/ч", "8,4 · 2,1"), cells.map { it.value })
        assertEquals(e, cells[1].color)
    }
}
