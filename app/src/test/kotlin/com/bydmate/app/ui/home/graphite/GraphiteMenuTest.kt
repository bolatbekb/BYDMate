package com.bydmate.app.ui.home.graphite

import com.bydmate.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteMenuTest {
    @Test fun menuItemMapsRoutes() {
        assertEquals(R.drawable.gr_ic_home to "Главная", menuItem("dashboard", "x"))
        assertEquals(R.drawable.gr_ic_car to "Машина", menuItem("tech_panel", "x"))
        assertEquals(R.drawable.gr_ic_route to "Поездки", menuItem("trips", "x"))
        assertEquals(R.drawable.gr_ic_plug to "Зарядки", menuItem("charges", "x"))
        assertEquals(R.drawable.gr_ic_flow to "Автоматизации", menuItem("automation", "x"))
        assertEquals(R.drawable.gr_ic_sliders to "Настройки", menuItem("settings", "x"))
    }

    @Test fun homeKeyInEveryLanguage() {
        val homes = GrLang.entries.map { menuItem("dashboard", "x", it) }
        assertEquals(setOf(R.drawable.gr_ic_home), homes.map { it.first }.toSet())
        assertEquals(listOf("Home", "Início", "Główna", "Галоўная"),
            listOf(GrLang.EN, GrLang.PT, GrLang.PL, GrLang.BE).map { menuItem("dashboard", "x", it).second })
    }

    @Test fun unknownRouteKeepsLabelWithSliders() {
        assertEquals(R.drawable.gr_ic_sliders to "Журнал", menuItem("ledger", "Журнал"))
    }
}
