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

    /**
     * The Graphite schemes are not app skins: «Оформление» lists the app skins, one of them
     * («Graphite», second) follows the Graphite home's scheme instead of listing each one.
     */
    @Test fun appSkinsWithoutGraphiteSchemes() {
        assertEquals(listOf("classic", "graphite_home", "cockpit", "graphite", "volt", "telemetry", "oled"), Skins.ALL.map { it.id })
        assertEquals("Graphite Pro", Skins.byId("graphite").title)
        assertEquals("Graphite", Skins.byId("graphite_home").title)
    }

    @Test fun aRemovedGraphiteSkinFallsBackToClassic() {
        for (id in listOf("graphite_soft", "graphite_dark", "graphite_edge", "graphite_mocha", "graphite_hub", "graphite_one", "graphite_code")) {
            assertSame(Skins.CLASSIC, Skins.byId(id))
        }
    }
}
