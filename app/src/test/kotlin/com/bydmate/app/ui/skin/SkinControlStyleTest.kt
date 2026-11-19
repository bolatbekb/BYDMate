package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinControlStyleTest {
    private val p = Skins.VOLT.palette

    @Test fun classicCardLookIsPassThrough() {
        val look = cardLook(CardStyle.MATERIAL, p)
        assertEquals(CardLook(), look)
        assertTrue(look.passThrough)
        assertEquals(1f, look.containerAlpha)
        assertEquals(CardBorderKind.NONE, look.border)
        assertFalse(look.stripe)
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
        assertEquals(0.dp, look.padding)
        assertEquals(1f, look.containerAlpha)
        // no style adds layout padding
        CardStyle.entries.forEach { assertEquals(it.name, 0.dp, cardLook(it, p).padding) }
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
            assertEquals(it.name, 1f, look.containerAlpha)
            assertEquals(it.name, cardLook(it, p).border, look.border)
        }
    }

    @Test fun dialogBorderAndDividerFollowTheCardStyle() {
        val bordered = CardStyle.entries.filter { dialogHasBorder(it) }.toSet()
        assertEquals(setOf(CardStyle.OUTLINED, CardStyle.BORDER_ONLY, CardStyle.STRIPE), bordered)

        assertEquals(DividerLook.FADED_ENDS, dividerLook(CardStyle.GLASS))
        assertEquals(DividerLook.CARD_BORDER, dividerLook(CardStyle.BORDER_ONLY))
        listOf(CardStyle.MATERIAL, CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.STRIPE).forEach {
            assertEquals(it.name, DividerLook.AS_GIVEN, dividerLook(it))
        }
    }

    @Test fun switchLookOnOffDisabled() {
        assertNull("MATERIAL is drawn by M3", switchLook(SwitchStyle.MATERIAL, true, true, p))
        listOf(SwitchStyle.SQUARE, SwitchStyle.PILL, SwitchStyle.OUTLINE).forEach { style ->
            for (checked in listOf(true, false)) for (enabled in listOf(true, false)) {
                val look = switchLook(style, checked, enabled, p)!!
                val case = "$style checked=$checked enabled=$enabled"
                assertEquals(case, if (checked) p.accentGreen else p.cardBorder, look.stateColor)
                assertEquals(case, if (enabled) 1f else 0.38f, look.alpha)
                // the face fits the M3 switch box: 52 x 32 dp
                assertTrue(case, look.trackHeight <= 32.dp)
                assertTrue(case, look.thumbSize + look.thumbInset * 2 <= 52.dp)
                assertTrue(case, look.thumbSize <= 32.dp)
            }
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
