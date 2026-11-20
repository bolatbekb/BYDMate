package com.bydmate.app.ui.skin

import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.BYDMateTypography
import com.bydmate.app.ui.theme.Skins
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinThemeTest {
    @Test fun classicKitIsTheAuthorLook() {
        val k = Skins.CLASSIC.kit
        assertEquals(SkinKit(), k)
        assertEquals(SkinFont.SYSTEM, k.fonts.text)
        assertEquals(1f, k.fonts.sizeFactor)
        assertEquals(SkinCorners(CornerStyle.ROUNDED, 1f), k.corners)
        assertEquals(SkinControls(SwitchStyle.MATERIAL, CardStyle.MATERIAL, SliderStyle.MATERIAL, NavStyle.MATERIAL), k.controls)
        assertEquals(BackgroundStyle.AUTHOR, k.background)
    }

    @Test fun everyOtherSkinHasTheSpecKit() {
        assertEquals(
            SkinKit(SkinFonts(SkinFont.EXO2, 0.95f, SkinFont.EXO2), SkinCorners(CornerStyle.ROUNDED, 0f),
                SkinControls(SwitchStyle.SQUARE, CardStyle.OUTLINED, SliderStyle.THIN, NavStyle.CUT), BackgroundStyle.GRID),
            Skins.COCKPIT.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.INTER, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1f),
                SkinControls(SwitchStyle.MATERIAL, CardStyle.FLAT, SliderStyle.MATERIAL, NavStyle.MATERIAL), BackgroundStyle.AUTHOR),
            Skins.GRAPHITE.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.MANROPE, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1.5f),
                SkinControls(SwitchStyle.PILL, CardStyle.GLASS, SliderStyle.MATERIAL, NavStyle.MATERIAL), BackgroundStyle.NEON),
            Skins.VOLT.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.ROBOTO_CONDENSED, 1f, SkinFont.RAJDHANI), SkinCorners(CornerStyle.ROUNDED, 0.5f),
                SkinControls(SwitchStyle.SQUARE, CardStyle.STRIPE, SliderStyle.SEGMENTED, NavStyle.UNDERLINE, CheckStyle.SQUARE), BackgroundStyle.CARBON),
            Skins.TELEMETRY.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.INTER, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1f),
                SkinControls(SwitchStyle.OUTLINE, CardStyle.BORDER_ONLY, SliderStyle.MATERIAL, NavStyle.OUTLINE), BackgroundStyle.AUTHOR),
            Skins.OLED.kit)
    }

    @Test fun finalBackgroundsExist() {
        assertTrue(BackgroundStyle.values().toList().containsAll(listOf(BackgroundStyle.NEON, BackgroundStyle.CARBON)))
        assertEquals(BackgroundStyle.NEON, Skins.VOLT.kit.background)
        assertEquals(BackgroundStyle.CARBON, Skins.TELEMETRY.kit.background)
        assertEquals(BackgroundStyle.GRID, Skins.COCKPIT.kit.background)
    }

    @Test fun finalPalettes() {
        fun c(v: Long) = androidx.compose.ui.graphics.Color(v)
        Skins.COCKPIT.palette.let {
            assertEquals(c(0xFF05070A), it.navyDark); assertEquals(c(0xFF05070A), it.navyDeep)
            assertEquals(c(0xFF090D12), it.cardSurface); assertEquals(c(0xFF0F151C), it.cardSurfaceElevated)
            assertEquals(c(0xFF141B23), it.chartGrid); assertEquals(c(0xFF05070A), it.navBarBackground)
        }
        Skins.GRAPHITE.palette.let {
            assertEquals(c(0xFF0F1013), it.navyDark); assertEquals(c(0xFF0F1013), it.navyDeep)
            assertEquals(c(0xFF141519), it.cardSurface); assertEquals(c(0xFF24262C), it.cardBorder)
            assertEquals(c(0xFF8E939C), it.textSecondary); assertEquals(c(0xFF202227), it.chartGrid)
            assertEquals(c(0xFF0F1013), it.navBarBackground)
        }
        Skins.VOLT.palette.let {
            assertEquals(c(0xFF060A14), it.navyDark)
            assertEquals(c(0xFF0C1324), it.cardSurface); assertEquals(c(0xFF13203A), it.cardSurfaceElevated)
        }
        Skins.TELEMETRY.palette.let {
            assertEquals(c(0xFF0B0B0B), it.navyDark); assertEquals(c(0xFF0B0B0B), it.navyDeep)
            assertEquals(c(0xFF111111), it.cardSurface); assertEquals(c(0xFF2A2A2A), it.cardBorder)
            assertEquals(c(0xFFFF5A1F), it.accentGreen); assertEquals(c(0xFFFFB020), it.accentOrange)
            assertEquals(c(0xFF0B0B0B), it.navBarBackground)
        }
    }

    @Test fun systemFontKeepsTheAuthorTypography() {
        assertSame(BYDMateTypography, skinTypography(BYDMateTypography, null))
        assertEquals(null, SkinFont.SYSTEM.family())
    }

    @Test fun aFontFamilyReachesAllFifteenStylesAndKeepsSizes() {
        val fam = androidx.compose.ui.text.font.FontFamily.Serif
        val t = skinTypography(BYDMateTypography, fam)
        val b = BYDMateTypography
        val pairs = listOf(
            t.displayLarge to b.displayLarge, t.displayMedium to b.displayMedium, t.displaySmall to b.displaySmall,
            t.headlineLarge to b.headlineLarge, t.headlineMedium to b.headlineMedium, t.headlineSmall to b.headlineSmall,
            t.titleLarge to b.titleLarge, t.titleMedium to b.titleMedium, t.titleSmall to b.titleSmall,
            t.bodyLarge to b.bodyLarge, t.bodyMedium to b.bodyMedium, t.bodySmall to b.bodySmall,
            t.labelLarge to b.labelLarge, t.labelMedium to b.labelMedium, t.labelSmall to b.labelSmall,
        )
        assertEquals(15, pairs.size)
        pairs.forEach { (n, o) ->
            assertEquals(fam, n.fontFamily)
            assertEquals(o.fontSize, n.fontSize)
        }
    }

    @Test fun classicShapesAreMaterialDefaults() {
        assertEquals(Shapes(), skinShapes(SkinCorners()))
    }

    @Test fun cornerScaleNeverNegative() {
        assertEquals(0.dp, mapCornerDp(SkinCorners(scale = -2f), 12.dp))
        assertEquals(6.dp, mapCornerDp(SkinCorners(scale = 0.5f), 12.dp))
    }

    @Test fun fontsAndLicencesAreShipped() {
        listOf("skin_exo2.ttf", "skin_manrope.ttf", "skin_roboto_condensed.ttf").forEach {
            assertTrue(it, File("src/main/res/font/$it").isFile)
        }
        listOf("exo2", "manrope", "robotocondensed", "inter").forEach {
            assertTrue(it, File("src/main/assets/licenses/$it-OFL.txt").isFile)
        }
    }
}
