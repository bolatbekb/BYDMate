package com.bydmate.app.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.foundation.shape.RoundedCornerShape as FoundationRounded

/** fork: the buttons, the filter chip, the outlined field and the checks per style (SkinInputStyle.kt). */
class SkinInputLookTest {
    private val p = Skins.COCKPIT.palette
    private val gr = GrScheme.DARK.palette
    private val grSkin = grToSkinPalette(gr)
    private val fill = Color(0xFF4ADE80)
    private val both = listOf(true, false)
    private val styled = CardStyle.entries.filter { it != CardStyle.MATERIAL }
    private val colors = ButtonColors(fill, Color.Black, Color.Gray, Color.DarkGray)

    // ---- Classic

    @Test fun classicIsPassThrough() {
        ButtonKind.entries.forEach { kind ->
            val look = buttonLook(CardStyle.MATERIAL, kind, p, fill)
            assertEquals(kind.name, ButtonLook(), look)
            assertTrue(look.passThrough)
            assertSame("the caller's colours, the same instance", colors, colors.withLook(look, isDefault = true))
        }
        for (selected in both) for (enabled in both) {
            val look = chipLook(CardStyle.MATERIAL, selected, enabled, p, gr)
            assertEquals(ChipLook(), look)
            assertTrue(look.passThrough)
            assertFalse(look.pill)
        }
        assertTrue(fieldLook(CardStyle.MATERIAL, p).passThrough)
        for (enabled in both) assertNull("M3 draws the check", checkLook(CheckStyle.MATERIAL, enabled, p))
    }

    @Test fun classicKeepsTheCallersShape() {
        val classic = SkinCorners()
        val own = FoundationRounded(8.dp)
        assertSame(CircleShape, skinButtonShape(CircleShape, CircleShape, classic))
        assertSame(own, skinButtonShape(own, CircleShape, classic))
    }

    @Test fun aStyleWithNoBorderKeepsTheCallersBorder() {
        val visible = BorderStroke(1.dp, Color.Red)
        val clear = BorderStroke(1.dp, Color.Transparent)
        listOf(null, visible, clear).forEach { caller ->
            assertSame(caller, styledBorder(caller, default = null, styled = null, drop = false))
            assertSame(caller, styledBorder(caller, default = caller, styled = null, drop = false))
        }
    }

    @Test fun theClassicKitHasMaterialChecks() {
        assertEquals(CheckStyle.MATERIAL, Skins.CLASSIC.kit.controls.check)
        assertEquals(CheckStyle.SQUARE, Skins.TELEMETRY.kit.controls.check)
        Skins.ALL.filter { it.id != Skins.TELEMETRY.id }.forEach {
            assertEquals(it.id, CheckStyle.MATERIAL, it.kit.controls.check)
        }
    }

    // ---- shared rules

    @Test fun aFilledButtonKeepsItsFillInEveryStyle() {
        // the author colours the text for the fill (NavyDark on AccentGreen), often on the Text itself
        CardStyle.entries.forEach { style ->
            val look = buttonLook(style, ButtonKind.FILLED, p, fill)
            assertEquals(style.name, 1f, look.containerAlpha)
            assertEquals(style.name, Color.Unspecified, look.contentColor)
            val c = colors.withLook(look, isDefault = false)
            if (!look.pill) assertEquals(style.name, fill, c.containerColor)
            assertEquals(style.name, Color.Black, c.contentColor)
        }
    }

    @Test fun styledBorderReplacesOnlyAnUnsetBorder() {
        val mine = BorderStroke(2.dp, Color.Red)
        val default = BorderStroke(1.dp, Color.Gray)
        val clear = BorderStroke(1.dp, Color.Transparent)
        val style = BorderStroke(1.dp, Color.Cyan)
        assertSame("the caller's own border wins", mine, styledBorder(mine, default, style, drop = false))
        assertSame("left as M3's", style, styledBorder(default, default, style, drop = false))
        assertSame("a transparent one counts as unset", style, styledBorder(clear, default, style, drop = false))
        assertSame("none at all", style, styledBorder(null, null, style, drop = false))
        // a Graphite pill draws its own rim
        assertNull(styledBorder(default, default, null, drop = true))
        assertNull(styledBorder(clear, default, null, drop = true))
        assertSame(mine, styledBorder(mine, default, null, drop = true))
    }

    @Test fun showsNothing() {
        assertTrue((null as BorderStroke?).showsNothing())
        assertTrue(BorderStroke(1.dp, Color.Transparent).showsNothing())
        assertFalse(BorderStroke(1.dp, Color.Red).showsNothing())
        assertFalse(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.Transparent, Color.Red))).showsNothing())
    }

    @Test fun theM3PillFollowsTheCornerStyle() {
        val cut = SkinCorners(CornerStyle.CUT, 0.75f)
        assertEquals(CutCornerShape(50), skinButtonShape(CircleShape, CircleShape, cut))
        // a rounded skin keeps the pill whatever its scale
        assertEquals(CircleShape, skinButtonShape(CircleShape, CircleShape, SkinCorners(CornerStyle.ROUNDED, 1.42f)))
        // a shape the caller chose stays (the fork RoundedCornerShape already follows the skin)
        val own = FoundationRounded(8.dp)
        assertSame(own, skinButtonShape(own, CircleShape, cut))
    }

    // ---- buttons per style

    @Test fun graphiteButtons() {
        val filled = buttonLook(CardStyle.GRAPHITE, ButtonKind.FILLED, grSkin, fill)
        assertTrue("the Graphite home's pill", filled.pill)
        assertTrue(filled.flat)
        assertFalse("a see-through button stays as M3 draws it", buttonLook(CardStyle.GRAPHITE, ButtonKind.FILLED, grSkin, Color.Transparent).pill)
        val pill = colors.withLook(filled, isDefault = false)
        assertEquals("the pill is the fill", 0f, pill.containerColor.alpha)
        assertEquals(0f, pill.disabledContainerColor.alpha)
        assertEquals(Color.Black, pill.contentColor)

        val outlined = buttonLook(CardStyle.GRAPHITE, ButtonKind.OUTLINED, grSkin, Color.Transparent)
        assertEquals("the raised key's edge", CardBorderKind.GRAPHITE_EDGE, outlined.border)
        assertFalse(outlined.pill)

        val text = buttonLook(CardStyle.GRAPHITE, ButtonKind.TEXT, grSkin, Color.Transparent)
        assertEquals(gr.textPrimary, text.contentColor)
        assertEquals("only M3's own colours take it", Color.Black, colors.withLook(text, isDefault = false).contentColor)
        assertEquals(gr.textPrimary, colors.withLook(text, isDefault = true).contentColor)
    }

    @Test fun outlineSkinsDropTheOutlinedButtonsContainer() {
        listOf(CardStyle.OUTLINED, CardStyle.BORDER_ONLY).forEach { style ->
            val look = buttonLook(style, ButtonKind.OUTLINED, p, Color.Transparent)
            assertEquals(style.name, 0f, look.containerAlpha)
            val c = colors.withLook(look, isDefault = false)
            assertEquals(0f, c.containerColor.alpha)
            assertEquals(0f, c.disabledContainerColor.alpha)
            assertEquals("the content stays", Color.Black, c.contentColor)
            assertFalse(look.pill)
        }
        assertTrue("OLED is flat, as its cards", buttonLook(CardStyle.BORDER_ONLY, ButtonKind.FILLED, p, fill).flat)
    }

    @Test fun glassAndFlatButtons() {
        val glassCard = cardLook(CardStyle.GLASS, p)
        listOf(ButtonKind.FILLED, ButtonKind.OUTLINED).forEach { kind ->
            val look = buttonLook(CardStyle.GLASS, kind, p, fill)
            assertEquals(CardBorderKind.TOP_HIGHLIGHT, look.border)
            assertEquals(glassCard.borderColor, look.borderColor)
            assertEquals(glassCard.borderFadeColor, look.borderFadeColor)
        }
        assertEquals(GLASS_ALPHA, buttonLook(CardStyle.GLASS, ButtonKind.OUTLINED, p, fill).containerAlpha)
        ButtonKind.entries.forEach { assertEquals(ButtonLook(flat = true), buttonLook(CardStyle.FLAT, it, p, fill)) }
        ButtonKind.entries.forEach { assertTrue(buttonLook(CardStyle.STRIPE, it, p, fill).passThrough) }
    }

    @Test fun onlyTheGraphiteFilledButtonIsAPill() {
        CardStyle.entries.forEach { style ->
            ButtonKind.entries.forEach { kind ->
                val pill = buttonLook(style, kind, p, fill).pill
                assertEquals("$style $kind", style == CardStyle.GRAPHITE && kind == ButtonKind.FILLED, pill)
            }
        }
    }

    // ---- chips per style

    @Test fun graphiteChipsAreTheHomesKeys() {
        val on = chipLook(CardStyle.GRAPHITE, selected = true, enabled = true, grSkin, gr)
        assertTrue(on.pill)
        assertEquals("the raised key", gr.tabActive, on.pillFill)
        assertEquals(gr.tabActiveEdge, on.pillEdge)
        assertEquals(gr.textPrimary, on.label)
        assertTrue(on.transparent)
        assertTrue(on.flat)
        val off = chipLook(CardStyle.GRAPHITE, selected = false, enabled = true, grSkin, gr)
        assertEquals("the top bar's plaque", gr.chip, off.pillFill)
        assertEquals(gr.chipEdge, off.pillEdge)
        assertEquals(gr.textSecondary, off.label)
        val dim = chipLook(CardStyle.GRAPHITE, selected = true, enabled = false, grSkin, gr)
        assertEquals(gr.tabActive.alpha * SWITCH_DISABLED_ALPHA, dim.pillFill.alpha, 0.01f)
        // Edge's plaque has no fill: still a pill, its rim alone
        val edge = GrScheme.entries.first { it.palette.edgeStyle }.palette
        assertTrue(chipLook(CardStyle.GRAPHITE, false, true, grToSkinPalette(edge), edge).pill)
    }

    @Test fun outlineSkinsOutlineTheChips() {
        listOf(CardStyle.OUTLINED, CardStyle.BORDER_ONLY).forEach { style ->
            val on = chipLook(style, selected = true, enabled = true, p, gr)
            assertTrue(on.transparent)
            assertFalse(on.pill)
            assertEquals(CardBorderKind.SOLID, on.border)
            assertEquals("the accent marks the selection", p.accentGreen, on.borderColor)
            assertEquals(p.accentGreen, on.label)
            val off = chipLook(style, selected = false, enabled = true, p, gr)
            assertEquals(p.cardBorder, off.borderColor)
            assertEquals("the caller's label", Color.Unspecified, off.label)
            val dim = chipLook(style, selected = true, enabled = false, p, gr)
            assertEquals(p.accentGreen.alpha * SWITCH_DISABLED_ALPHA, dim.borderColor.alpha, 0.01f)
        }
        assertTrue(chipLook(CardStyle.BORDER_ONLY, true, true, p, gr).flat)
        assertFalse(chipLook(CardStyle.OUTLINED, true, true, p, gr).flat)
    }

    @Test fun glassFlatAndStripeChips() {
        for (selected in both) {
            val glass = chipLook(CardStyle.GLASS, selected, true, p, gr)
            assertEquals(CardBorderKind.TOP_HIGHLIGHT, glass.border)
            assertFalse(glass.transparent)
            assertEquals(ChipLook(flat = true), chipLook(CardStyle.FLAT, selected, true, p, gr))
            assertTrue(chipLook(CardStyle.STRIPE, selected, true, p, gr).passThrough)
        }
    }

    @Test fun theSelectionNeverChangesTheChipsCall() {
        // the wrapper's single call takes a pill or not by style alone, never by state
        styled.forEach { style ->
            val pills = listOf(true, false).flatMap { s -> listOf(true, false).map { e -> chipLook(style, s, e, p, gr).pill } }
            assertEquals(style.name, 1, pills.toSet().size)
        }
    }

    // ---- fields

    @Test fun fields() {
        val graphite = fieldLook(CardStyle.GRAPHITE, grSkin)
        assertEquals("the Graphite home's sunken block", grSkin.navyMid, graphite.fill)
        assertEquals(grSkin.navyMid, fieldContainer(Color.Transparent, graphite))
        assertEquals("a caller's own container stays", Color.Red, fieldContainer(Color.Red, graphite))
        assertEquals(0f, fieldContainer(Color.Red, fieldLook(CardStyle.BORDER_ONLY, p)).alpha)
        // a colour keeps 8 bits of alpha: 0.7 comes back as 179 / 255
        assertEquals(GLASS_ALPHA, fieldContainer(Color.Red, fieldLook(CardStyle.GLASS, p)).alpha, 0.01f)
        listOf(CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.STRIPE).forEach {
            assertTrue(it.name, fieldLook(it, p).passThrough)
        }
        assertEquals(Color.Red, fieldContainer(Color.Red, FieldLook()))
    }

    // ---- checks

    @Test fun squareChecksTakeThePalette() {
        val t = Skins.TELEMETRY.palette
        val on = checkLook(CheckStyle.SQUARE, enabled = true, t)
        assertNotNull(on)
        assertEquals(t.accentGreen, on!!.on)
        assertEquals(t.textMuted, on.off)
        assertEquals(t.navyDark, on.mark)
        assertEquals(1f, on.alpha)
        assertEquals(SWITCH_DISABLED_ALPHA, checkLook(CheckStyle.SQUARE, enabled = false, t)!!.alpha)
    }

    @Test fun checksKeepM3sBox() {
        // M3 1.3.1: a 20 dp box with a 2 dp stroke, 2 dp padding, a 40 dp state layer
        assertEquals(20.dp, CheckBoxSize)
        assertEquals(2.dp, CheckStroke)
        assertEquals(2.dp, CheckPadding)
        assertEquals(20.dp, CheckRippleRadius)
        assertTrue(RadioDotSize < CheckBoxSize - CheckStroke * 2)
    }

    // ---- the Graphite pill's corner

    @Test fun pillRadius() {
        val d = Density(2f)
        assertEquals("a dp corner keeps its size", 24f, grRadiusOf(FoundationRounded(12.dp), d, GR_PILL_RADIUS), 0.001f)
        assertEquals("M3's percent pill", GR_PILL_RADIUS, grRadiusOf(CircleShape, d, GR_PILL_RADIUS), 0.001f)
        assertEquals("the card keeps its own default", 34f, grRadiusOf(CircleShape, d), 0.001f)
    }
}
