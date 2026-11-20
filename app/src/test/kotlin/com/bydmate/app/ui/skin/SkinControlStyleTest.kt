package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinControlStyleTest {
    private val p = Skins.VOLT.palette

    @Test fun classicCardLookIsPassThrough() {
        val look = cardLook(CardStyle.MATERIAL, p)
        assertEquals(CardLook(), look)
        assertSame("one shared value, no allocation per card", CardLook.MATERIAL, look)
        assertSame(CardLook.MATERIAL, cardLook(CardStyle.STRIPE, p, nested = true))
        assertTrue(look.passThrough)
        assertEquals(1f, look.containerAlpha)
        assertEquals(CardBorderKind.NONE, look.border)
        assertFalse(look.stripe)
        assertFalse(look.brackets)
        assertFalse(look.flat)
        // every other style changes something
        CardStyle.entries.filter { it != CardStyle.MATERIAL }.forEach {
            assertFalse(it.name, cardLook(it, p).passThrough)
        }
    }

    @Test fun stripeDrawsABarAndKeepsLayout() {
        val look = cardLook(CardStyle.STRIPE, p)
        assertTrue(look.stripe)
        assertEquals(p.accentGreen, look.stripeColor)
        assertEquals(3.dp, look.stripeWidth)
        assertTrue("on the top edge", look.stripeTop)
        assertEquals(CardBorderKind.SOLID, look.border)
        assertEquals(p.cardBorder, look.borderColor)
        assertEquals(0.dp, look.padding)
        assertEquals(1f, look.containerAlpha)
        // no style adds layout padding
        CardStyle.entries.forEach { assertEquals(it.name, 0.dp, cardLook(it, p).padding) }
    }

    @Test fun bracketsAreFlatMarksWithoutBorder() {
        val look = cardLook(CardStyle.BRACKETS, p)
        assertTrue(look.flat)
        assertTrue(look.brackets)
        assertEquals(p.accentGreen, look.bracketColor)
        assertEquals(CardBorderKind.NONE, look.border)
        assertEquals(14.dp, CardBracketLength)
        assertEquals(2.dp, CardBracketThickness)
        assertFalse(cardLook(CardStyle.BRACKETS, p, nested = true).brackets)
        assertTrue(dialogHasBorder(CardStyle.BRACKETS))
        assertEquals(DividerLook.AS_GIVEN, dividerLook(CardStyle.BRACKETS))
    }

    @Test fun glassBorderIsTheAccent() {
        val glass = cardLook(CardStyle.GLASS, p)
        assertEquals(p.accentGreen.copy(alpha = 0.40f), glass.borderColor)
        assertEquals(p.accentGreen.copy(alpha = 0.15f), glass.borderFadeColor)
    }

    @Test fun borderOnlyIsTransparent() {
        val look = cardLook(CardStyle.BORDER_ONLY, p)
        assertEquals(0f, look.containerAlpha)
        assertEquals(CardBorderKind.SOLID, look.border)
        assertEquals(p.cardBorder, look.borderColor)
        assertFalse(look.stripe)
    }

    @Test fun outlinedFlatAndGlass() {
        val outlined = cardLook(CardStyle.OUTLINED, p)
        assertEquals(CardBorderKind.SOLID, outlined.border)
        assertEquals(p.cardBorder, outlined.borderColor)
        assertEquals(1f, outlined.containerAlpha)

        val flat = cardLook(CardStyle.FLAT, p)
        assertTrue(flat.flat)
        assertEquals(CardBorderKind.NONE, flat.border)
        assertEquals(1f, flat.containerAlpha)

        val glass = cardLook(CardStyle.GLASS, p)
        assertEquals(0.7f, glass.containerAlpha)
        assertEquals(CardBorderKind.TOP_HIGHLIGHT, glass.border)
        // brighter at the top: what shows is the colour's luminance times its alpha
        val top = glass.borderColor.luminance() * glass.borderColor.alpha
        val bottom = glass.borderFadeColor.luminance() * glass.borderFadeColor.alpha
        assertTrue("the highlight is brighter at the top ($top vs $bottom)", top > bottom)
    }

    @Test fun aDialogCardStaysSolid() {
        CardStyle.entries.forEach {
            val look = cardLook(it, p).inDialog()
            // solid: the M3 container, or the opaque Graphite card under the see-through one
            if (look.graphite) assertEquals(it.name, 0f, look.containerAlpha) else assertEquals(it.name, 1f, look.containerAlpha)
            assertEquals(it.name, cardLook(it, p).border, look.border)
        }
    }

    @Test fun aNestedCardDoesNotRepeatTheOuterOne() {
        val g = grToSkinPalette(GrScheme.DARK.palette)
        // GRAPHITE: the caller's opaque container and the raised key's edge; no grCard (shadow,
        // material, relief, glare) a second time
        val graphite = cardLook(CardStyle.GRAPHITE, g, nested = true)
        assertFalse(graphite.graphite)
        assertEquals(1f, graphite.containerAlpha)
        assertTrue("no second shadow", graphite.flat)
        assertEquals(CardBorderKind.GRAPHITE_EDGE, graphite.border)
        assertFalse(graphite.stripe)
        assertFalse(graphite.passThrough)
        // STRIPE: the bar only on the outer card
        val stripe = cardLook(CardStyle.STRIPE, p, nested = true)
        assertFalse(stripe.stripe)
        assertTrue("the plain M3 card", stripe.passThrough)
        // GLASS: solid, with the glass rim
        val glass = cardLook(CardStyle.GLASS, p, nested = true)
        assertEquals(1f, glass.containerAlpha)
        assertEquals(cardLook(CardStyle.GLASS, p).border, glass.border)
        assertEquals(cardLook(CardStyle.GLASS, p).borderColor, glass.borderColor)
        // the others nest as they are
        listOf(CardStyle.MATERIAL, CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.BORDER_ONLY).forEach {
            assertEquals(it.name, cardLook(it, p), cardLook(it, p, nested = true))
        }
        // no nested look draws a stripe or a Graphite card, or adds padding
        CardStyle.entries.forEach {
            val look = cardLook(it, p, nested = true)
            assertFalse(it.name, look.stripe)
            assertFalse(it.name, look.graphite)
            assertEquals(it.name, 0.dp, look.padding)
        }
    }

    @Test fun dialogBorderAndDividerFollowTheCardStyle() {
        val bordered = CardStyle.entries.filter { dialogHasBorder(it) }.toSet()
        assertEquals(setOf(CardStyle.OUTLINED, CardStyle.BORDER_ONLY, CardStyle.STRIPE, CardStyle.BRACKETS), bordered)

        assertEquals(DividerLook.FADED_ENDS, dividerLook(CardStyle.GLASS))
        assertEquals(DividerLook.CARD_BORDER, dividerLook(CardStyle.BORDER_ONLY))
        listOf(CardStyle.MATERIAL, CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.STRIPE, CardStyle.BRACKETS).forEach {
            assertEquals(it.name, DividerLook.AS_GIVEN, dividerLook(it))
        }
    }

    @Test fun `volt pill switch off shows its track in textMuted`() {
        assertEquals(p.textMuted, switchLook(SwitchStyle.PILL, checked = false, enabled = true, p)!!.stateColor)
        assertEquals(p.accentGreen, switchLook(SwitchStyle.PILL, checked = true, enabled = true, p)!!.stateColor)
    }

    @Test fun `other styles keep the cardBorder off track`() {
        listOf(SwitchStyle.SQUARE, SwitchStyle.OUTLINE).forEach {
            assertEquals(p.cardBorder, switchLook(it, checked = false, enabled = true, p)!!.stateColor)
        }
    }

    private fun assertSwitchFace(style: SwitchStyle, checked: Boolean, enabled: Boolean) {
        val look = switchLook(style, checked, enabled, p)!!
        val case = "$style checked=$checked enabled=$enabled"
        val off = if (style == SwitchStyle.PILL) p.textMuted else p.cardBorder
        assertEquals(case, if (checked) p.accentGreen else off, look.stateColor)
        assertEquals(case, if (enabled) 1f else 0.38f, look.alpha)
        // the face fits the M3 switch box: 52 x 32 dp
        assertTrue(case, look.trackHeight <= 32.dp)
        assertTrue(case, look.thumbSize + look.thumbInset * 2 <= 52.dp)
        assertTrue(case, look.thumbSize <= 32.dp)
    }

    @Test fun switchLookOnOffDisabled() {
        assertNull("MATERIAL is drawn by M3", switchLook(SwitchStyle.MATERIAL, true, true, p))
        listOf(SwitchStyle.SQUARE, SwitchStyle.PILL, SwitchStyle.OUTLINE).forEach { style ->
            for (checked in listOf(true, false)) for (enabled in listOf(true, false)) assertSwitchFace(style, checked, enabled)
            // on and off differ only in colour, never in geometry
            val on = switchLook(style, true, true, p)!!
            val off = switchLook(style, false, true, p)!!
            assertEquals(style.name, on.thumbSize, off.thumbSize)
            assertEquals(style.name, on.trackHeight, off.trackHeight)
            assertTrue(style.name, on.thumbColor != off.thumbColor)
        }
    }

    @Test fun squareIsRectangularWithTwoDpCorners() {
        val on = switchLook(SwitchStyle.SQUARE, true, true, p)!!
        assertTrue(on.trackFilled)
        assertEquals(32.dp, on.trackHeight)
        assertEquals(2.dp, on.trackCorner)
        assertEquals(2.dp, on.thumbCorner)
        assertEquals(0.dp, on.thumbRing)
        assertEquals(on.thumbFill, on.thumbColor)
    }

    @Test fun pillHasAThinTrackAndARingedThumb() {
        val on = switchLook(SwitchStyle.PILL, true, true, p)!!
        assertTrue(on.trackFilled)
        assertEquals(4.dp, on.trackHeight)
        assertEquals(20.dp, on.thumbSize)
        assertEquals(10.dp, on.thumbCorner) // a circle
        assertTrue(on.thumbRing > 0.dp)
        assertEquals("the accent ring", p.accentGreen, on.thumbColor)
    }

    @Test fun outlineHasABorderTrackAndARingThumb() {
        val on = switchLook(SwitchStyle.OUTLINE, true, true, p)!!
        assertFalse(on.trackFilled)
        assertTrue(on.trackStroke > 0.dp)
        assertEquals(32.dp, on.trackHeight)
        assertEquals(Color.Transparent, on.thumbFill)
        assertTrue(on.thumbRing > 0.dp)
        assertEquals(p.accentGreen, on.thumbColor)
    }

    @Test fun thumbTravelsAcrossTheTrackAndMirrorsInRtl() {
        // 52 dp track, 24 dp thumb, 4 dp inset, in px at density 1
        assertEquals(4f, switchThumbX(52f, 24f, 4f, fraction = 0f, rtl = false))
        assertEquals(24f, switchThumbX(52f, 24f, 4f, fraction = 1f, rtl = false))
        assertEquals(24f, switchThumbX(52f, 24f, 4f, fraction = 0f, rtl = true))
        assertEquals(4f, switchThumbX(52f, 24f, 4f, fraction = 1f, rtl = true))
    }

    @Test fun sliderFractionAndSegments() {
        assertEquals(0f, sliderFraction(10f, 10f..20f))
        assertEquals(0.5f, sliderFraction(15f, 10f..20f))
        assertEquals(1f, sliderFraction(25f, 10f..20f))
        assertEquals(0f, sliderFraction(5f, 5f..5f))
        assertEquals(12, SLIDER_SEGMENTS)
        assertEquals(0, litSegments(0f))
        assertEquals(6, litSegments(0.5f))
        assertEquals(12, litSegments(1f))
        assertEquals(12, litSegments(1.5f))
    }
}
