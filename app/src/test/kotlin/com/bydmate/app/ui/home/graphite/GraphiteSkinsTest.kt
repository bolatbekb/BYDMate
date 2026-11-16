package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
        assertEquals("Graphite Mocha", Skins.byId("graphite_mocha").title)
        assertEquals("Graphite Hub", Skins.byId("graphite_hub").title)
        assertEquals("Graphite One", Skins.byId("graphite_one").title)
        assertEquals("Graphite Code", Skins.byId("graphite_code").title)
        assertEquals(13, Skins.ALL.size)
    }

    @Test fun mapping() {
        assertEquals(GrSoft.electric, Skins.GRAPHITE_SOFT.palette.accentGreen)
        assertEquals(GrSoft.fuel, Skins.GRAPHITE_SOFT.palette.accentOrange)
        assertEquals(GrSoft.textTertiary, Skins.GRAPHITE_SOFT.palette.textMuted)
        assertEquals(GrEdge.cardTop, Skins.GRAPHITE_EDGE.palette.navBarBackground)
        assertEquals(GrDark.inset, Skins.GRAPHITE_DARK.palette.navBarBackground)
    }

    /** Every colour of a Graphite app skin is opaque, like the other skins (dialogs, surfaceVariant). */
    @Test fun graphiteSkinColoursAreOpaque() {
        for (skin in listOf(Skins.GRAPHITE_SOFT, Skins.GRAPHITE_DARK, Skins.GRAPHITE_EDGE, Skins.GRAPHITE_MOCHA, Skins.GRAPHITE_HUB, Skins.GRAPHITE_ONE, Skins.GRAPHITE_CODE)) {
            val c = skin.palette
            val all = mapOf(
                "navyDark" to c.navyDark, "navyDeep" to c.navyDeep, "navyMid" to c.navyMid,
                "cardSurface" to c.cardSurface, "cardSurfaceElevated" to c.cardSurfaceElevated, "cardBorder" to c.cardBorder,
                "accentGreen" to c.accentGreen, "accentBlue" to c.accentBlue, "accentOrange" to c.accentOrange,
                "accentTeal" to c.accentTeal, "textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary,
                "textMuted" to c.textMuted, "chartGrid" to c.chartGrid, "navBarBackground" to c.navBarBackground,
                "navIndicator" to c.navIndicator, "socGreen" to c.socGreen, "socYellow" to c.socYellow, "socRed" to c.socRed,
            )
            for ((name, color) in all) assertEquals("${skin.id}.$name", 1f, color.alpha, 0f)
        }
    }

    @Test fun opaqueColoursAreKept() {
        assertEquals(GrSoft.tabActive, Skins.GRAPHITE_SOFT.palette.cardSurfaceElevated)
        assertEquals(GrDark.tabActive, Skins.GRAPHITE_DARK.palette.cardSurfaceElevated)
        assertEquals(GrEdge.tabActive.compositeOver(GrEdge.cardTop), Skins.GRAPHITE_EDGE.palette.cardSurfaceElevated)
    }

    @Test fun homePaletteFollowsSkin() {
        assertSame(GrSoft, homePalette("graphite_soft"))
        assertSame(GrEdge, homePalette("graphite_edge"))
        assertSame(GrDark, homePalette("graphite_dark"))
        assertSame(GrDark, homePalette("classic"))
        assertSame(GrDark, homePalette("graphite"))
        assertSame(GrMocha, homePalette("graphite_mocha"))
        assertSame(GrHub, homePalette("graphite_hub"))
        assertSame(GrOne, homePalette("graphite_one"))
        assertSame(GrCode, homePalette("graphite_code"))
    }
}
