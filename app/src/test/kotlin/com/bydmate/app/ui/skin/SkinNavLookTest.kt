package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** fork: the bottom bar's key per NavStyle (navLook) and the GRAPHITE dock's dividers (navDividerXs). */
class SkinNavLookTest {
    private val p = Skins.COCKPIT.palette
    private val both = listOf(true, false)

    @Test fun classicIsPassThrough() {
        for (selected in both) {
            val look = navLook(NavStyle.MATERIAL, selected, p)
            assertEquals("selected=$selected", NavLook(), look)
            assertSame(NavLook.MATERIAL, look)
            assertTrue(look.passThrough)
            assertFalse("M3 draws its own indicator", look.lit)
        }
        NavStyle.entries.filter { it != NavStyle.MATERIAL }.forEach { style ->
            for (selected in both) assertFalse("$style selected=$selected", navLook(style, selected, p).passThrough)
        }
    }

    @Test fun selectionLightsTheKeyAndNeverChangesTheStyle() {
        // the wrapper picks its slot content by style; a tab switch must not change it
        NavStyle.entries.filter { it != NavStyle.MATERIAL }.forEach { style ->
            val on = navLook(style, true, p)
            val off = navLook(style, false, p)
            assertTrue("$style lit when selected", on.lit)
            assertFalse("$style dark when not", off.lit)
            assertEquals(style, on.style)
            assertEquals(style, off.style)
            assertEquals("$style keeps its indicator colours", on.fill, off.fill)
            assertEquals("$style keeps its indicator colours", on.edge, off.edge)
        }
    }

    @Test fun graphiteKeyIsTheHomeMenuKey() {
        val g = GrScheme.DARK.palette
        val palette = grToSkinPalette(g)
        val on = navLook(NavStyle.GRAPHITE, true, palette)
        val off = navLook(NavStyle.GRAPHITE, false, palette)
        // the home's MenuKey: TextPrimary when lit, TextSecondary otherwise
        assertEquals(g.textPrimary, on.content)
        assertEquals(g.textSecondary, off.content)
        // the raised key and its bar are drawn by MenuKeyFace from the live Graphite palette
        assertEquals(Color.Unspecified, on.fill)
        assertEquals(Color.Unspecified, on.edge)
    }

    @Test fun indicatorStylesTakeThePalette() {
        val cut = navLook(NavStyle.CUT, true, p)
        assertEquals(p.navIndicator, cut.fill)
        assertEquals(p.accentGreen, cut.edge)
        assertEquals(Color.Unspecified, cut.content)

        val underline = navLook(NavStyle.UNDERLINE, true, p)
        assertEquals(p.accentGreen, underline.fill)
        assertEquals(Color.Unspecified, underline.edge)

        val outline = navLook(NavStyle.OUTLINE, true, p)
        assertEquals("a ring, no plate", 0f, outline.fill.alpha)
        assertEquals(p.accentGreen, outline.edge)
    }

    @Test fun topDotAndDockColourTheKey() {
        val top = navLook(NavStyle.TOP, true, p)
        assertEquals(p.accentGreen, top.content)
        assertEquals(p.accentGreen, top.fill)
        assertEquals(p.textSecondary, navLook(NavStyle.TOP, false, p).content)

        val dot = navLook(NavStyle.DOT, true, p)
        assertEquals(p.textPrimary, dot.content)
        assertEquals(p.accentGreen, dot.fill)
        assertEquals(p.textSecondary, navLook(NavStyle.DOT, false, p).content)

        val dock = navLook(NavStyle.DOCK, true, p)
        assertEquals(p.textPrimary, dock.content)
        assertEquals("the raised plate", p.cardSurfaceElevated, dock.fill)
        assertEquals("the glowing bar", p.accentGreen, dock.edge)
        assertEquals(p.textSecondary, navLook(NavStyle.DOCK, false, p).content)
    }

    @Test fun topDotAndDockSizes() {
        assertEquals(3.dp, NavUnderlineHeight)
        assertEquals(6.dp, NavDotSize)
        assertEquals(64.dp, NavDockHeight)
        assertEquals(22.dp, NavDockRadius)
        assertEquals(8.dp, NavDockMargin)
        assertEquals(14.dp, NavDockPlateRadius)
        assertEquals(24.dp, NavDockBarWidth)
        assertEquals(0.8f, NAV_DOCK_FILL_ALPHA)
    }

    @Test fun dotStaysInsideTheBarAtEveryTextScale() {
        val r = NavDotSize.value / 2
        val gap = NavBottomGapDp()
        // the bar is max(64, 16 sp x scale + 40) dp: 64 at 1.0, 1.15 and 1.3, then taller
        for (h in listOf(16f * 1.0f + 40, 16f * 1.15f + 40, 16f * 1.3f + 40, 64f, 72f).map { maxOf(64f, it) }) {
            val y = navDotCentreY(h, r, gap)
            assertEquals("bar $h", h - gap, y, 0.001f)
            assertTrue("bar $h top", y - r >= 0f)
            assertTrue("bar $h bottom", y + r <= h)
        }
        assertEquals("a gap under the radius is lifted", 61f, navDotCentreY(64f, 3f, 1f), 0.001f)
        assertEquals("a bar shorter than the dot puts its centre at the radius", 3f, navDotCentreY(4f, 3f, 6f), 0.001f)
    }

    @Test fun dotClearsTheLabelAtEveryTextScale() {
        val r = NavDotSize.value / 2
        for (scale in listOf(1.0f, 1.15f, 1.3f)) {
            val line = 16f * scale
            val dotTop = navDotCentreY(64f, r, NavDotBottomGap.value) - r
            // the descenders end inside the line box (about 1 dp above its bottom at 1.0, in proportion)
            val glyphBottom = navLabelBottom(64f, line) - line * 0.06f
            assertTrue("text $scale: dot top $dotTop, glyphs end $glyphBottom", dotTop >= glyphBottom)
        }
    }

    @Test fun dockBarClearsTheLabelAtEveryTextScale() {
        // M3's label: 40..56 dp at 1.0, 37.6..58.4 dp at 1.3 in the 64 dp row
        assertEquals(56f, navLabelBottom(64f, 16f), 0.001f)
        assertEquals(58.4f, navLabelBottom(64f, 16f * 1.3f), 0.001f)
        val top = navDockBarTop(NavDockHeight.value, NavDockPlateInsetY.value, NavDockBarGap.value, NavUnderlineHeight.value)
        for (scale in listOf(1.0f, 1.15f, 1.3f)) {
            val label = navLabelBottom(NavDockHeight.value, 16f * scale)
            assertTrue("text $scale: bar top $top, label bottom $label", top >= label)
            assertTrue("the bar stays inside the plate", top + NavUnderlineHeight.value <= NavDockHeight.value - NavDockPlateInsetY.value)
        }
    }

    private fun NavBottomGapDp() = NavDotBottomGap.value

    @Test fun indicatorBoxIsTheM3One() {
        // M3 1.3.1: a 64 x 32 dp indicator around the 24 dp icon
        assertEquals(64.dp, 24.dp + NavIndicatorPadX * 2)
        assertEquals(32.dp, 24.dp + NavIndicatorPadY * 2)
        assertTrue(NavCutCorner < 16.dp)
        assertEquals(3.dp, NavUnderlineHeight)
        assertEquals(1.dp, NavEdgeWidth)
    }

    @Test fun skinsUseTheirNavStyles() {
        assertEquals(NavStyle.MATERIAL, Skins.CLASSIC.kit.controls.nav)
        assertEquals(NavStyle.TOP, Skins.COCKPIT.kit.controls.nav)
        assertEquals(NavStyle.DOT, Skins.GRAPHITE.kit.controls.nav)
        assertEquals(NavStyle.DOCK, Skins.VOLT.kit.controls.nav)
        assertEquals(NavStyle.UNDERLINE, Skins.TELEMETRY.kit.controls.nav)
        assertEquals(NavStyle.OUTLINE, Skins.OLED.kit.controls.nav)
        GrFont.entries.forEach { assertEquals(it.name, NavStyle.GRAPHITE, graphiteKit(it).controls.nav) }
        assertEquals(NavStyle.GRAPHITE, Skins.GRAPHITE_HOME.kit.controls.nav)
    }

    private fun key(left: Float, lit: Boolean = false) = NavKeySpan(left, left + 100f, lit)

    @Test fun dividersSitInTheGapsBetweenDarkKeys() {
        // three 100 px keys with 8 px gaps, given out of order
        val keys = listOf(key(216f), key(0f), key(108f))
        assertEquals(listOf(104f, 212f), navDividerXs(keys))
    }

    @Test fun noDividerBesideALitKey() {
        assertEquals(emptyList<Float>(), navDividerXs(listOf(key(0f), key(108f, lit = true), key(216f))))
        assertEquals(listOf(212f), navDividerXs(listOf(key(0f, lit = true), key(108f), key(216f))))
        assertEquals(listOf(104f), navDividerXs(listOf(key(0f), key(108f), key(216f, lit = true))))
        // the pressed key and the selected one, as on the Graphite home
        assertEquals(listOf(428f), navDividerXs(listOf(key(0f, true), key(108f), key(216f, true), key(324f), key(432f))))
    }

    @Test fun noKeysNoDividers() {
        assertEquals(emptyList<Float>(), navDividerXs(emptyList()))
        assertEquals(emptyList<Float>(), navDividerXs(listOf(key(0f))))
    }
}
