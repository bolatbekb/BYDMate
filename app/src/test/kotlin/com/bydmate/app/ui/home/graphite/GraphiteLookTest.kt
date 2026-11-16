package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GraphiteLookTest {
    @Test fun schemeIdsRoundTrip() {
        for (s in GrScheme.entries) assertSame(s, GrScheme.fromId(s.id))
        assertEquals(listOf("soft", "dark", "edge", "mocha", "hub", "one", "code"), GrScheme.entries.map { it.id })
    }

    @Test fun schemeTitlesAndPalettes() {
        assertEquals(listOf("Soft", "Dark", "Edge", "Mocha", "Hub", "One", "Code"), GrScheme.entries.map { it.title })
        assertSame(GrSoft, GrScheme.SOFT.palette)
        assertSame(GrDark, GrScheme.DARK.palette)
        assertSame(GrEdge, GrScheme.EDGE.palette)
        assertSame(GrMocha, GrScheme.MOCHA.palette)
        assertSame(GrHub, GrScheme.HUB.palette)
        assertSame(GrOne, GrScheme.ONE.palette)
        assertSame(GrCode, GrScheme.CODE.palette)
    }

    @Test fun unknownSchemeIsDark() {
        assertSame(GrScheme.DARK, GrScheme.fromId(null))
        assertSame(GrScheme.DARK, GrScheme.fromId("graphite_soft"))
    }

    @Test fun materialIdsRoundTrip() {
        assertEquals(
            listOf("none", "glass", "metal", "sand_fine", "sand_coarse", "concrete"),
            GrMaterial.entries.map { it.id },
        )
        assertEquals(
            listOf("Без материала", "Стекло", "Металл", "Песок мелкий", "Песок крупный", "Бетон"),
            GrMaterial.entries.map { it.title },
        )
        for (m in GrMaterial.entries) assertSame(m, GrMaterial.fromId(m.id))
        assertSame(GrMaterial.NONE, GrMaterial.fromId(null))
        assertSame(GrMaterial.NONE, GrMaterial.fromId("wood"))
    }

    @Test fun reliefIdsRoundTrip() {
        assertEquals(listOf("flat", "convex", "concave"), GrRelief.entries.map { it.id })
        assertEquals(listOf("Плоский", "Выпуклый", "Вогнутый"), GrRelief.entries.map { it.title })
        for (r in GrRelief.entries) assertSame(r, GrRelief.fromId(r.id))
        assertSame(GrRelief.FLAT, GrRelief.fromId(null))
        assertSame(GrRelief.FLAT, GrRelief.fromId("bumpy"))
    }

    @Test fun defaultLookIsTodays() {
        assertEquals(GrLook(GrMaterial.NONE, GrRelief.FLAT, false), GrLook())
    }

    @Test fun graphiteSkinsMigrateToTheirScheme() {
        assertSame(GrScheme.SOFT, schemeFromLegacySkin("graphite_soft"))
        assertSame(GrScheme.DARK, schemeFromLegacySkin("graphite_dark"))
        assertSame(GrScheme.EDGE, schemeFromLegacySkin("graphite_edge"))
        assertSame(GrScheme.MOCHA, schemeFromLegacySkin("graphite_mocha"))
        assertSame(GrScheme.HUB, schemeFromLegacySkin("graphite_hub"))
        assertSame(GrScheme.ONE, schemeFromLegacySkin("graphite_one"))
        assertSame(GrScheme.CODE, schemeFromLegacySkin("graphite_code"))
    }

    @Test fun otherSkinsDoNotMigrate() {
        assertNull(schemeFromLegacySkin(null))
        assertNull(schemeFromLegacySkin("classic"))
        assertNull(schemeFromLegacySkin("graphite")) // Graphite Pro stays an app skin
        assertNull(schemeFromLegacySkin("graphite_neon"))
    }
}
