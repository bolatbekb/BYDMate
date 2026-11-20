package com.bydmate.app.ui.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinNumbersTest {
    private val mono = FontFamily.Monospace

    @Test fun numericFontPerKit() {
        assertNull(Skins.CLASSIC.kit.fonts.numeric)
        assertEquals(SkinFont.EXO2, Skins.COCKPIT.kit.fonts.numeric)
        assertEquals(SkinFont.RAJDHANI, Skins.TELEMETRY.kit.fonts.numeric)
        assertEquals(SkinFont.ROBOTO_CONDENSED, Skins.TELEMETRY.kit.fonts.text)
        assertNull(Skins.VOLT.kit.fonts.numeric)
        assertNull(Skins.GRAPHITE.kit.fonts.numeric)
        assertEquals(SkinFont.INTER, graphiteKit(GrFont.INTER).fonts.numeric)
        assertEquals(SkinFont.RAJDHANI, graphiteKit(GrFont.DILINK).fonts.numeric)
    }

    @Test fun classicChangesNothing() {
        assertNull(numberFont(null, mono, null, "12"))
        assertNull(numberFont(null, null, mono, "12"))
    }

    @Test fun onlyMonospaceIsReplaced() {
        assertNull(numberFont(SkinFont.EXO2, null, null, "12"))
        assertNull(numberFont(SkinFont.EXO2, FontFamily.Serif, mono, "12"))   // the argument wins
        assertEquals(SkinFont.EXO2, numberFont(SkinFont.EXO2, mono, null, "12"))
        assertEquals(SkinFont.EXO2, numberFont(SkinFont.EXO2, null, mono, "12"))
        assertEquals(SkinFont.EXO2, numberFont(SkinFont.EXO2, mono, FontFamily.Serif, "12"))
    }

    @Test fun aTextWithLettersIsNeverReplaced() {
        for (f in listOf(SkinFont.RAJDHANI, SkinFont.INTER, SkinFont.EXO2, SkinFont.ROBOTO_CONDENSED)) {
            assertNull(numberFont(f, mono, null, "12,5 км"))
            assertNull(numberFont(f, null, mono, "0xAbC123"))
            assertEquals(f, numberFont(f, mono, null, "12,5 %"))
        }
    }

    /** The Graphite home's rule: Rajdhani for big light numerals only, the system font otherwise. */
    @Test fun rajdhaniOnlyForBigLightNumerals() {
        val r = SkinFont.RAJDHANI
        assertEquals(r, r.forNumber(23.sp, null))
        assertEquals(r, r.forNumber(46.sp, FontWeight.Medium))
        assertEquals(r, r.forNumber(30.sp, FontWeight.Light))
        // small: the trips table, the dashboard tiles
        assertEquals(SkinFont.SYSTEM, r.forNumber(14.sp, null))
        assertEquals(SkinFont.SYSTEM, r.forNumber(22.9.sp, FontWeight.Normal))
        // bold: Rajdhani has only Medium, a bold would be synthetic
        assertEquals(SkinFont.SYSTEM, r.forNumber(30.sp, FontWeight.SemiBold))
        assertEquals(SkinFont.SYSTEM, r.forNumber(30.sp, FontWeight.Bold))
        // no size, or not in sp: not known to be big
        assertEquals(SkinFont.SYSTEM, r.forNumber(TextUnit.Unspecified, null))
        assertEquals(SkinFont.SYSTEM, r.forNumber(2.em, null))
        // the rule is Rajdhani's alone: the other fonts take every size and weight
        for (f in listOf(SkinFont.INTER, SkinFont.EXO2, SkinFont.ROBOTO_CONDENSED)) {
            assertEquals(f, f.forNumber(11.sp, FontWeight.Bold))
            assertEquals(f, f.forNumber(TextUnit.Unspecified, null))
        }
        // and only a numeric-only Monospace text gets that far
        assertNull(numberFont(r, mono, null, "12 км")?.forNumber(30.sp, null))
    }

    @Test fun rajdhaniFallbackIsTheGivenFont() {
        val r = SkinFont.RAJDHANI
        assertEquals(SkinFont.ROBOTO_CONDENSED, r.forNumber(14.sp, null, SkinFont.ROBOTO_CONDENSED))
        assertEquals(SkinFont.ROBOTO_CONDENSED, r.forNumber(30.sp, FontWeight.Bold, SkinFont.ROBOTO_CONDENSED))
        assertEquals(r, r.forNumber(30.sp, null, SkinFont.ROBOTO_CONDENSED))
        assertEquals(SkinFont.SYSTEM, r.forNumber(14.sp, null))
        assertEquals(SkinFont.INTER, SkinFont.INTER.forNumber(14.sp, null, SkinFont.ROBOTO_CONDENSED))
        assertEquals(SkinFont.SYSTEM, numberFallback(SkinFont.SYSTEM))
        assertEquals(SkinFont.ROBOTO_CONDENSED, numberFallback(SkinFont.ROBOTO_CONDENSED))
    }

    @Test fun rajdhaniFits() {
        assertTrue(rajdhaniFits(RAJDHANI_MIN_SIZE, null))
        assertTrue(rajdhaniFits(23.sp, FontWeight.Medium))
        assertFalse(rajdhaniFits(23.sp, FontWeight.W600))
        assertFalse(rajdhaniFits(22.sp, FontWeight.Normal))
        assertFalse(rajdhaniFits(TextUnit.Unspecified, null))
    }

    @Test fun isNumericOnly() {
        assertTrue(isNumericOnly(""))
        assertTrue(isNumericOnly("-12,5 / 3.0 %"))
        assertFalse(isNumericOnly("12 км"))
        assertFalse(isNumericOnly("kWh"))
    }

    /** Inter and Exo 2 have proportional digits with a tnum feature; the system font takes tnum too. */
    @Test fun tabularOnlyWhereTheFontNeedsIt() {
        assertTrue(SkinFont.INTER.needsTnum())
        assertTrue(SkinFont.EXO2.needsTnum())
        assertTrue(SkinFont.SYSTEM.needsTnum())
        assertFalse(SkinFont.ROBOTO_CONDENSED.needsTnum()) // tabular already
        assertFalse(SkinFont.RAJDHANI.needsTnum()) // no tnum feature: big numerals only
    }

    @Test fun tnumIsInTheSettingsExactlyOnce() {
        assertEquals("tnum", tnumSettings(null))
        assertEquals("tnum", tnumSettings(""))
        assertEquals("cv11, tnum", tnumSettings("cv11"))
        assertEquals("tnum, cv11", tnumSettings("tnum, cv11"))
        assertEquals("cv11, tnum", tnumSettings(tnumSettings("cv11")))
    }

    private val accent = Color(0xFF00E676)

    @Test fun glowForABigNumber() {
        val s = numberGlow(24.sp, accent, 22f)!!
        assertEquals(accent.copy(alpha = 0.40f), s.color)
        assertEquals(Offset.Zero, s.offset)
        assertEquals(22f, s.blurRadius, 0f)
        assertEquals(s, numberGlow(24.sp, accent, 22f))
        assertEquals(numberGlow(23.sp, accent, 22f)!!.color, s.color)
    }

    @Test fun noGlowForASmallNumber() {
        assertNull(numberGlow(22.sp, accent, 22f))
        assertNull(numberGlow(TextUnit.Unspecified, accent, 22f))
    }

    @Test fun glowGate() {
        assertTrue(glowsNumber(true, mono, null, "12,5"))
        assertTrue(glowsNumber(true, null, mono, "12"))
        assertFalse(glowsNumber(false, mono, null, "12"))
        assertFalse(glowsNumber(true, mono, null, "12 км"))
        assertFalse(glowsNumber(true, null, null, "12"))
        assertFalse(glowsNumber(true, FontFamily.Default, mono, "12"))
    }

    @Test fun glowOnlyForCockpitAndVolt() {
        assertTrue(Skins.COCKPIT.kit.fonts.glow)
        assertTrue(Skins.VOLT.kit.fonts.glow)
        assertFalse(Skins.CLASSIC.kit.fonts.glow)
        assertFalse(Skins.GRAPHITE.kit.fonts.glow)
        assertFalse(Skins.TELEMETRY.kit.fonts.glow)
        assertFalse(Skins.OLED.kit.fonts.glow)
        assertFalse(graphiteKit(GrFont.INTER).fonts.glow)
        assertFalse(graphiteKit(GrFont.DILINK).fonts.glow)
    }
}
