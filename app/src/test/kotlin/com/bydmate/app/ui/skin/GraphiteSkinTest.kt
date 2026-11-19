package com.bydmate.app.ui.skin

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.graphite.GrDiLink
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.home.graphite.highlight
import com.bydmate.app.ui.theme.SkinPalette
import com.bydmate.app.ui.theme.SkinState
import com.bydmate.app.ui.theme.Skins
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphiteSkinTest {
    private val ctx = mockk<Context>(relaxed = true)

    @After fun resetSingletons() {
        SkinState.select(ctx, Skins.CLASSIC)
        GraphiteLookState.selectScheme(ctx, GrScheme.DARK)
        GraphiteLookState.selectFont(ctx, GrFont.INTER)
    }

    private fun SkinPalette.all() = listOf(
        navyDark, navyDeep, navyMid, cardSurface, cardSurfaceElevated, cardBorder,
        accentGreen, accentBlue, accentOrange, accentTeal, textPrimary, textSecondary, textMuted,
        chartGrid, navBarBackground, navIndicator, socGreen, socYellow, socRed,
    )

    @Test fun everyGrSchemeMapsToAFullPalette() {
        GrScheme.entries.forEach { s ->
            val p = grToSkinPalette(s.palette)
            assertEquals(s.name, 19, p.all().size)
            // the author's screens fill with these tokens: every one is a solid colour
            p.all().forEachIndexed { i, c -> assertEquals("${s.name} token $i", 1f, c.alpha) }
            val text = p.textPrimary.luminance()
            val card = p.cardSurface.luminance()
            assertTrue("${s.name}: text $text over card $card", text > card + 0.3f)
            assertTrue("${s.name}: secondary text is darker than primary", p.textSecondary.luminance() < text)
            assertTrue("${s.name}: borders show on the card", p.cardBorder != p.cardSurface)
        }
    }

    @Test fun theMappingTakesNamedGraphiteFields() {
        GrScheme.entries.forEach { s ->
            val g = s.palette
            val p = grToSkinPalette(g)
            assertEquals(s.name, g.bg, p.navyDark)
            assertEquals(s.name, g.bgGlow, p.navyDeep)
            assertEquals(s.name, g.inset.compositeOver(g.cardTop), p.navyMid)
            assertEquals(s.name, g.cardTop, p.cardSurface)
            assertEquals(s.name, g.tabActive.compositeOver(g.cardTop), p.cardSurfaceElevated)
            assertEquals(s.name, g.track.compositeOver(g.cardTop), p.cardBorder)
            assertEquals(s.name, g.highlight, p.accentGreen)
            assertEquals(s.name, g.cold, p.accentBlue)
            assertEquals(s.name, g.fuel, p.accentOrange)
            assertEquals(s.name, lerp(g.electric, g.cold, 0.5f), p.accentTeal)
            assertEquals(s.name, g.textPrimary, p.textPrimary)
            assertEquals(s.name, g.textSecondary, p.textSecondary)
            assertEquals(s.name, g.textTertiary, p.textMuted)
            assertEquals(s.name, g.divider.compositeOver(g.cardTop), p.chartGrid)
            assertEquals(s.name, g.bg, p.navBarBackground)
            assertEquals(s.name, g.tabActive.compositeOver(g.bg), p.navIndicator)
        }
        // DiLink's own accent (#45A2FF) is the app's primary accent, not its electric teal
        assertEquals(GrDiLink.accent, grToSkinPalette(GrDiLink).accentGreen)
    }

    @Test fun trafficLightsKeepTheirMeaning() {
        val fixed = Skins.CLASSIC.palette
        GrScheme.entries.forEach { s ->
            val p = grToSkinPalette(s.palette)
            assertEquals(s.name, fixed.socGreen, p.socGreen)
            assertEquals(s.name, fixed.socYellow, p.socYellow)
            assertEquals(s.name, fixed.socRed, p.socRed)
        }
    }

    @Test fun interFontKit() {
        val k = graphiteKit(GrFont.INTER)
        assertEquals(SkinFonts(SkinFont.INTER, 0.97f), k.fonts)
        assertEquals(CornerStyle.ROUNDED, k.corners.style)
        assertEquals(SkinControls(SwitchStyle.GRAPHITE, CardStyle.GRAPHITE, SliderStyle.THIN), k.controls)
        assertEquals(BackgroundStyle.GRAPHITE, k.background)
        // an author 12 dp card corner becomes the Graphite card's 34 mockup px = 17 dp
        assertEquals(17f, mapCornerDp(k.corners, 12.dp).value, 0.01f)
    }

    @Test fun dilinkFontKeepsSystemFont() {
        val k = graphiteKit(GrFont.DILINK)
        assertEquals(SkinFonts(SkinFont.SYSTEM, 1f), k.fonts)
        assertEquals(graphiteKit(GrFont.INTER).copy(fonts = k.fonts), k)
    }

    @Test fun graphiteSkinIsListedSecond() {
        assertSame(Skins.CLASSIC, Skins.ALL[0])
        val g = Skins.ALL[1]
        assertSame(Skins.GRAPHITE_HOME, g)
        assertEquals("graphite_home", g.id)
        assertEquals("Graphite", g.title)
        assertSame(g, Skins.byId("graphite_home"))
        assertEquals(1, Skins.ALL.count { it.id == g.id })
    }

    @Test fun theSkinFollowsTheGraphiteLookLive() {
        SkinState.select(ctx, Skins.GRAPHITE_HOME)
        GrScheme.entries.forEach { s ->
            GraphiteLookState.selectScheme(ctx, s)
            assertEquals(s.name, grToSkinPalette(s.palette), SkinState.palette)
            assertEquals(s.name, grToSkinPalette(s.palette), SkinState.current.palette)
        }
        GraphiteLookState.selectFont(ctx, GrFont.DILINK)
        assertEquals(graphiteKit(GrFont.DILINK), SkinState.current.kit)
        GraphiteLookState.selectFont(ctx, GrFont.INTER)
        assertEquals(graphiteKit(GrFont.INTER), SkinState.current.kit)
        assertEquals("graphite_home", SkinState.current.id)
        // another skin ignores the Graphite look
        SkinState.select(ctx, Skins.VOLT)
        GraphiteLookState.selectScheme(ctx, GrScheme.MOCHA)
        assertSame(Skins.VOLT, SkinState.current)
        assertSame(Skins.VOLT.palette, SkinState.palette)
    }

    @Test fun isGraphiteIsSetOnceForEveryGraphiteSkin() {
        assertTrue(Skins.GRAPHITE_HOME.isGraphite)
        GrScheme.entries.forEach { s -> GrFont.entries.forEach { f -> assertTrue("$s $f", graphiteSkin(s, f).isGraphite) } }
        Skins.ALL.filter { it !== Skins.GRAPHITE_HOME }.forEach { assertFalse(it.id, it.isGraphite) }
        // a copy keeps it: it follows the id
        assertTrue(Skins.GRAPHITE_HOME.copy(title = "x").isGraphite)
        assertFalse(Skins.GRAPHITE_HOME.copy(id = "classic").isGraphite)
    }

    @Test fun livePalettesAndKitsAreBuiltOnce() {
        GrScheme.entries.forEach { s ->
            GrFont.entries.forEach { f ->
                val a = graphiteSkin(s, f)
                assertSame("$s $f", a, graphiteSkin(s, f))
                assertSame("$s $f", graphitePalette(s), a.palette)
                assertEquals("$s $f", "graphite_home", a.id)
            }
        }
        assertSame(graphitePalette(GrScheme.HUB), graphitePalette(GrScheme.HUB))
    }

    @Test fun aCardCornerBecomesTheGraphiteRadius() {
        val d = Density(2f)
        // 17 dp is 34 mockup px (1 mockup px = 0.5 dp)
        assertEquals(34f, grRadiusOf(RoundedCornerShape(17.dp), d), 0.001f)
        assertEquals(24f, grRadiusOf(RoundedCornerShape(12.dp), d), 0.001f)
        assertEquals(0f, grRadiusOf(RoundedCornerShape(0.dp), d), 0.001f)
        // a size-dependent corner (percent) takes the Graphite card's own radius
        assertEquals(34f, grRadiusOf(RoundedCornerShape(50), d), 0.001f)
        assertEquals("square stays square", 0f, grRadiusOf(RectangleShape, d), 0.001f)
    }

    @Test fun graphiteControlLooks() {
        val p = grToSkinPalette(GrScheme.DARK.palette)
        val card = cardLook(CardStyle.GRAPHITE, p)
        assertTrue(card.graphite)
        assertTrue("grCard draws its own shadow", card.flat)
        assertEquals("grCard draws the fill", 0f, card.containerAlpha)
        assertEquals(CardBorderKind.NONE, card.border)
        assertFalse(card.stripe)
        assertEquals(0.dp, card.padding)
        assertEquals("the Graphite fill is solid, also in a dialog", card, card.inDialog())
        assertFalse("grCard draws its own contour", dialogHasBorder(CardStyle.GRAPHITE))
        assertEquals(DividerLook.AS_GIVEN, dividerLook(CardStyle.GRAPHITE))

        for (checked in listOf(true, false)) for (enabled in listOf(true, false)) {
            val look = switchLook(SwitchStyle.GRAPHITE, checked, enabled, p)!!
            val case = "checked=$checked enabled=$enabled"
            assertEquals(case, if (enabled) 1f else 0.38f, look.alpha)
            assertTrue(case, look.trackFilled)
            assertEquals(case, 32.dp, look.trackHeight)
            assertEquals(case, look.thumbFill, look.thumbColor)
            assertTrue(case, look.thumbSize + look.thumbInset * 2 <= 52.dp)
        }
        val on = switchLook(SwitchStyle.GRAPHITE, true, true, p)!!
        val off = switchLook(SwitchStyle.GRAPHITE, false, true, p)!!
        assertEquals("a sunken groove (Inset)", p.navyMid, off.stateColor)
        assertEquals("the accent thumb when on", p.accentGreen, on.thumbFill)
        assertEquals(p.textMuted, off.thumbFill)
        assertTrue("the groove lights when on", on.stateColor != off.stateColor)
        assertEquals(on.thumbSize, off.thumbSize)
    }
}
