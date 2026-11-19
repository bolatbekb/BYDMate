package com.bydmate.app.ui.skin

import androidx.compose.ui.text.font.FontFamily
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
        assertEquals(SkinFont.ROBOTO_CONDENSED, Skins.TELEMETRY.kit.fonts.numeric)
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

    @Test fun isNumericOnly() {
        assertTrue(isNumericOnly(""))
        assertTrue(isNumericOnly("-12,5 / 3.0 %"))
        assertFalse(isNumericOnly("12 км"))
        assertFalse(isNumericOnly("kWh"))
    }

    @Test fun tabularOnlyWhereTheFontNeedsIt() {
        assertTrue(SkinFont.INTER.needsTnum())
        assertFalse(SkinFont.SYSTEM.needsTnum())
        assertFalse(SkinFont.EXO2.needsTnum())
        assertFalse(SkinFont.RAJDHANI.needsTnum())
    }

    @Test fun tnumIsInTheSettingsExactlyOnce() {
        assertEquals("tnum", tnumSettings(null))
        assertEquals("tnum", tnumSettings(""))
        assertEquals("cv11, tnum", tnumSettings("cv11"))
        assertEquals("tnum, cv11", tnumSettings("tnum, cv11"))
        assertEquals("cv11, tnum", tnumSettings(tnumSettings("cv11")))
    }
}
