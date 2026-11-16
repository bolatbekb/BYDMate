package com.bydmate.app.ui.home.graphite

import com.bydmate.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphiteLinksTest {
    @Test fun cellLabelNamesKnownNetworkTypes() {
        assertEquals("LTE", GraphiteLinks.cellLabel(13))
        assertEquals("5G", GraphiteLinks.cellLabel(20))
        listOf(3, 8, 9, 10, 15).forEach { assertEquals("3G", GraphiteLinks.cellLabel(it)) }
    }

    @Test fun cellLabelUnknownIsNull() {
        assertNull(GraphiteLinks.cellLabel(0))
        assertNull(GraphiteLinks.cellLabel(1))
    }
}

class GraphiteMenuTest {
    @Test fun menuItemMapsRoutes() {
        assertEquals(R.drawable.gr_ic_car to "Машина", menuItem("tech_panel", "x"))
        assertEquals(R.drawable.gr_ic_route to "Поездки", menuItem("trips", "x"))
        assertEquals(R.drawable.gr_ic_plug to "Зарядки", menuItem("charges", "x"))
        assertEquals(R.drawable.gr_ic_flow to "Автоматизации", menuItem("automation", "x"))
        assertEquals(R.drawable.gr_ic_sliders to "Настройки", menuItem("settings", "x"))
    }

    @Test fun unknownRouteKeepsLabelWithSliders() {
        assertEquals(R.drawable.gr_ic_sliders to "Журнал", menuItem("ledger", "Журнал"))
    }
}
