package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import com.bydmate.app.ui.home.graphite.GrPalette
import com.bydmate.app.ui.home.graphite.highlight
import com.bydmate.app.ui.theme.SkinPalette

// fork: the looks of the buttons and chips per card style that buttonLook and chipLook
// (SkinInputStyle.kt) hand out, one function each.

/** Cockpit's buttons. */
internal fun bracketsButton(kind: ButtonKind, palette: SkinPalette): ButtonLook = when (kind) {
    ButtonKind.FILLED -> ButtonLook(flat = true)
    ButtonKind.OUTLINED -> ButtonLook(containerAlpha = 0f, border = CardBorderKind.SOLID, borderColor = palette.cardBorder)
    ButtonKind.TEXT -> ButtonLook()
}

/** The Graphite home's buttons. */
internal fun graphiteButton(kind: ButtonKind, palette: SkinPalette, fill: Color): ButtonLook = when (kind) {
    ButtonKind.FILLED -> ButtonLook(pill = fill.alpha > 0f, flat = true)
    ButtonKind.OUTLINED -> ButtonLook(border = CardBorderKind.GRAPHITE_EDGE, flat = true)
    ButtonKind.TEXT -> ButtonLook(contentColor = palette.textPrimary, flat = true)
}

/** Volt's buttons: the filled one a glowing gradient. */
internal fun glassButton(kind: ButtonKind, palette: SkinPalette, fill: Color): ButtonLook =
    if (kind == ButtonKind.TEXT) {
        ButtonLook.MATERIAL
    } else if (kind == ButtonKind.FILLED && fill.alpha > 0f) {
        ButtonLook(
            flat = true,
            gradientStart = palette.accentBlue,
            gradientEnd = palette.accentGreen,
            glow = palette.accentGreen.copy(alpha = BUTTON_GLOW_ALPHA),
            glowSpread = BUTTON_GLOW_SPREAD,
        )
    } else {
        val glass = cardLook(CardStyle.GLASS, palette)
        ButtonLook(
            containerAlpha = if (kind == ButtonKind.OUTLINED) GLASS_ALPHA else 1f,
            border = CardBorderKind.TOP_HIGHLIGHT,
            borderColor = glass.borderColor,
            borderFadeColor = glass.borderFadeColor,
        )
    }

/** Graphite Pro's chip: a raised segment (selected) or plain text. */
internal fun raisedChip(selected: Boolean, enabled: Boolean, palette: SkinPalette): ChipLook = if (selected) {
    val fill = palette.cardBorder
    ChipLook(fill = if (enabled) fill else fill.dimmed(), label = palette.textPrimary, noBorder = true, raised = true)
} else {
    ChipLook(transparent = true, label = palette.textSecondary, noBorder = true, flat = true)
}

/** Telemetry's chip: solid accent when selected, cardSurfaceElevated otherwise. */
internal fun solidChip(selected: Boolean, enabled: Boolean, palette: SkinPalette): ChipLook {
    val fill = if (selected) palette.accentGreen else palette.cardSurfaceElevated
    return ChipLook(
        fill = if (enabled) fill else fill.dimmed(),
        label = if (selected) palette.navyDark else palette.textSecondary,
        noBorder = true,
    )
}

/** The Graphite home's chip (see chipLook). */
internal fun graphiteChip(selected: Boolean, enabled: Boolean, gr: GrPalette): ChipLook {
    val fill = if (selected) gr.highlight.copy(alpha = 0.16f) else gr.chip
    return ChipLook(
        pillFill = if (enabled) fill else fill.dimmed(),
        pillEdge = if (selected) gr.highlight.copy(alpha = 0.5f) else gr.chipEdge,
        transparent = true,
        label = if (selected) gr.highlight else gr.textSecondary,
        flat = true,
    )
}

/** Cockpit's and OLED's chip: an outline only. */
internal fun outlineChip(selected: Boolean, enabled: Boolean, palette: SkinPalette, flat: Boolean): ChipLook {
    val edge = if (selected) palette.accentGreen else palette.cardBorder
    return ChipLook(
        transparent = true,
        label = if (selected) palette.accentGreen else Color.Unspecified,
        border = CardBorderKind.SOLID,
        borderColor = if (enabled) edge else edge.dimmed(),
        flat = flat,
    )
}

/** Volt's chip: solid accent with a glow when selected, a see-through plate otherwise. */
internal fun glassChip(selected: Boolean, enabled: Boolean, palette: SkinPalette): ChipLook = if (selected) {
    val fill = palette.accentGreen
    ChipLook(
        fill = if (enabled) fill else fill.dimmed(),
        label = palette.navyDark,
        noBorder = true,
        glow = if (enabled) palette.accentGreen.copy(alpha = CHIP_GLOW_ALPHA) else Color.Unspecified,
        glowSpread = CHIP_GLOW_SPREAD,
    )
} else {
    val fill = palette.cardSurfaceElevated.copy(alpha = 0.8f)
    val edge = Color.White.copy(alpha = 0.08f)
    ChipLook(
        fill = if (enabled) fill else fill.dimmed(),
        label = palette.textSecondary,
        border = CardBorderKind.SOLID,
        borderColor = if (enabled) edge else edge.dimmed(),
        glowSpread = CHIP_GLOW_SPREAD,
    )
}
