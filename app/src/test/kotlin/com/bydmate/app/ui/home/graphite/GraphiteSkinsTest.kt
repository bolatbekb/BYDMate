package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GraphiteSkinsTest {
    @Test fun palettesMatchSpec() {
        assertEquals(Color(0xFF5AD6A4), GrSoft.electric)
        assertEquals(Color(0xFF03050A), GrDark.bg)
        assertEquals(Color(0x335A96EB), GrEdge.cardEdge)
        assertEquals(Color(0xB8121316), GrSoft.photoShade)
    }

    /** The Graphite schemes colour only the Graphite home; «Оформление» keeps the six app skins. */
    @Test fun appSkinsWithoutGraphiteSchemes() {
        assertEquals(listOf("classic", "cockpit", "graphite", "volt", "telemetry", "oled"), Skins.ALL.map { it.id })
        assertEquals("Graphite Pro", Skins.byId("graphite").title)
    }

    @Test fun aRemovedGraphiteSkinFallsBackToClassic() {
        for (id in listOf("graphite_soft", "graphite_dark", "graphite_edge", "graphite_mocha", "graphite_hub", "graphite_one", "graphite_code")) {
            assertSame(Skins.CLASSIC, Skins.byId(id))
        }
    }
}
