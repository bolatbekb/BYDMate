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

    @Test fun skinsRegistered() {
        assertEquals("Graphite Soft", Skins.byId("graphite_soft").title)
        assertEquals("Graphite Dark", Skins.byId("graphite_dark").title)
        assertEquals("Graphite Edge", Skins.byId("graphite_edge").title)
        assertEquals("Graphite Pro", Skins.byId("graphite").title)
        assertEquals(9, Skins.ALL.size)
    }

    @Test fun mapping() {
        assertEquals(GrSoft.electric, Skins.GRAPHITE_SOFT.palette.accentGreen)
        assertEquals(GrSoft.fuel, Skins.GRAPHITE_SOFT.palette.accentOrange)
        assertEquals(GrSoft.textTertiary, Skins.GRAPHITE_SOFT.palette.textMuted)
        assertEquals(GrEdge.cardTop, Skins.GRAPHITE_EDGE.palette.navBarBackground)
        assertEquals(GrDark.inset, Skins.GRAPHITE_DARK.palette.navBarBackground)
    }

    @Test fun homePaletteFollowsSkin() {
        assertSame(GrSoft, homePalette("graphite_soft"))
        assertSame(GrEdge, homePalette("graphite_edge"))
        assertSame(GrDark, homePalette("graphite_dark"))
        assertSame(GrDark, homePalette("classic"))
        assertSame(GrDark, homePalette("graphite"))
    }
}
