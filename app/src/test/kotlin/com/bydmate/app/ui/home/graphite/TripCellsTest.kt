package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TripCellsTest {
    private val t = Color.White

    @Test fun noTripsYetStillShowsFourCells() {
        val cells = tripCells(TripCardModel.EMPTY, hasFuelTank = true, primary = t)
        assertEquals(listOf("0 мин", "0 мин", "0 · 0", "0 · 0"), cells.map { it.value })
        assertEquals(
            listOf("в пути", "стоянка", "кВт·ч · л в пути", "кВт·ч · л на стоянке"), cells.map { it.label },
        )
    }

    @Test fun evWithoutTankShowsKwhOnly() {
        val cells = tripCells(TripCardModel.EMPTY, hasFuelTank = false, primary = t)
        assertEquals(listOf("0 мин", "0 мин", "0", "0"), cells.map { it.value })
        assertEquals(listOf("в пути", "стоянка", "кВт·ч в пути", "кВт·ч на стоянке"), cells.map { it.label })
    }

    @Test fun currentWithSplitRussian() {
        val m = TripCardModel(TripCardMode.CURRENT, 40.0, 42, 15, 4.9, 0.8, 0.6, 0.0)
        val cells = tripCells(m, hasFuelTank = true, primary = t)
        assertEquals(listOf("42 мин", "15 мин", "4,9 · 0,8", "0,6 · 0,0"), cells.map { it.value })
        assertEquals(
            listOf("в пути", "стоянка", "кВт·ч · л в пути", "кВт·ч · л на стоянке"), cells.map { it.label },
        )
    }

    @Test fun currentWithSplitEnglish() {
        val m = TripCardModel(TripCardMode.CURRENT, 40.0, 72, 15, 4.9, 0.8, 0.6, 0.0)
        val cells = tripCells(m, hasFuelTank = true, primary = t, lang = GrLang.EN)
        assertEquals(listOf("1 h 12 min", "15 min", "4.9 · 0.8", "0.6 · 0.0"), cells.map { it.value })
        assertEquals(
            listOf("on the road", "parked", "kWh · l on the road", "kWh · l parked"), cells.map { it.label },
        )
    }

    @Test fun noFuelTankShowsKwhOnlyLabels() {
        val m = TripCardModel(TripCardMode.CURRENT, 40.0, 42, 15, 4.9, 0.0, 0.6, 0.0)
        val cells = tripCells(m, hasFuelTank = false, primary = t, lang = GrLang.EN)
        assertEquals(listOf("4.9", "0.6"), cells.drop(2).map { it.value })
        assertEquals(listOf("kWh on the road", "kWh parked"), cells.drop(2).map { it.label })
    }

    @Test fun recordedTripHasDashesForParking() {
        val m = TripCardModel(TripCardMode.LAST, 30.0, 30, null, 5.0, 1.0, null, null)
        val cells = tripCells(m, hasFuelTank = true, primary = t)
        assertEquals(listOf("30 мин", "—", "5,0 · 1,0", "—"), cells.map { it.value })
    }

    @Test fun negativeEnergyIsShownAsZero() {
        val m = TripCardModel(TripCardMode.CURRENT, 1.0, 5, 5, -0.2, -0.1, 0.0, 0.0)
        assertEquals("0,0 · 0,0", tripCells(m, hasFuelTank = true, primary = t)[2].value)
    }
}
