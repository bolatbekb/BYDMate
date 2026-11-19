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
        assertEquals(SkinControls(SwitchStyle.MATERIAL, CardStyle.MATERIAL, SliderStyle.MATERIAL), k.controls)
        assertEquals(BackgroundStyle.AUTHOR, k.background)
    }

    @Test fun everyOtherSkinHasTheSpecKit() {
        assertEquals(
            SkinKit(SkinFonts(SkinFont.EXO2, 0.95f), SkinCorners(CornerStyle.CUT, 0.75f),
                SkinControls(SwitchStyle.SQUARE, CardStyle.OUTLINED, SliderStyle.THIN), BackgroundStyle.GRID),
            Skins.COCKPIT.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.INTER, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1.25f),
                SkinControls(SwitchStyle.MATERIAL, CardStyle.FLAT, SliderStyle.MATERIAL), BackgroundStyle.AUTHOR),
            Skins.GRAPHITE.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.MANROPE, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1.5f),
                SkinControls(SwitchStyle.PILL, CardStyle.GLASS, SliderStyle.MATERIAL), BackgroundStyle.GLOW),
            Skins.VOLT.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.ROBOTO_CONDENSED, 1f), SkinCorners(CornerStyle.ROUNDED, 0.3f),
                SkinControls(SwitchStyle.SQUARE, CardStyle.STRIPE, SliderStyle.SEGMENTED), BackgroundStyle.SCANLINES),
            Skins.TELEMETRY.kit)
        assertEquals(
            SkinKit(SkinFonts(SkinFont.INTER, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1f),
                SkinControls(SwitchStyle.OUTLINE, CardStyle.BORDER_ONLY, SliderStyle.MATERIAL), BackgroundStyle.AUTHOR),
            Skins.OLED.kit)
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
