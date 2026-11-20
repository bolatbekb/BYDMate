package com.bydmate.app.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.FilterChip as M3FilterChip

// fork: drop-in for the M3 FilterChip (same name, parameters and defaults as M3 1.3.1). MATERIAL
// passes every argument straight to M3. The other card styles (chipLook) change the container
// and label colours, the border and the elevation, never the caller's label, icons or sizes; the
// shape is the caller's (the fork RoundedCornerShape) or M3's from the skin's theme shapes. A
// caller's own visible border wins; the author's chips mostly pass a transparent one, which counts
// as unset, so a style's outline shows.
//
// GRAPHITE lays the Graphite home's pill under a see-through container: the raised key when
// selected, the top bar's plaque otherwise (SkinButtons.kt: ProvidePillFrame and skinPill, which
// keep M3's 48 dp touch target outside the pill).
//
// One M3 call inside one provider, whatever the style: a skin switch keeps the chip and its label.

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val style = SkinState.current.kit.controls.card
    val look = if (style == CardStyle.MATERIAL) {
        ChipLook.MATERIAL
    } else {
        chipLook(style, selected, enabled, SkinState.palette, graphitePaletteFor(style == CardStyle.GRAPHITE))
    }
    val styled = skinBorder(look.border, look.borderColor, look.borderFadeColor)
    // M3's own border, to tell «left as it is» from a border the caller chose; only a style that replaces it asks
    val default = if (styled != null || look.framed || look.noBorder) FilterChipDefaults.filterChipBorder(enabled, selected) else null
    val chipColors = if (look.passThrough) colors else remember(colors, look, selected) { colors.withLook(look, selected) }
    ProvidePillFrame(look.pill, look.framed) { touch ->
        M3FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = when {
                look.pill -> modifier.skinPill(touch, look.pillFill, look.pillEdge, shape)
                look.framed -> modifier.skinGlow(touch, look.glow, look.glowSpread, shape)
                else -> modifier
            },
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = chipColors,
            elevation = when {
                elevation == null -> null
                look.raised -> chipElevation(RaisedChipElevation)
                look.flat -> chipElevation(0.dp)
                else -> elevation
            },
            border = styledBorder(border, default, styled, drop = look.pill || look.noBorder),
            interactionSource = interactionSource,
        )
    }
}

/**
 * The caller's chip colours in [look], for the [selected] state: see-through containers in every
 * state when [ChipLook.transparent], and the look's label colour on the label and both icons of
 * this state. Unspecified keeps the caller's colour (M3's copy takes it as «unchanged»).
 */
private fun SelectableChipColors.withLook(look: ChipLook, selected: Boolean): SelectableChipColors {
    val clear = when {
        look.fill.isSpecified -> look.fill
        look.transparent -> Color.Transparent
        else -> Color.Unspecified
    }
    val label = if (selected) Color.Unspecified else look.label
    val selectedLabel = if (selected) look.label else Color.Unspecified
    return copy(
        containerColor = clear,
        labelColor = label,
        leadingIconColor = label,
        trailingIconColor = label,
        disabledContainerColor = clear,
        selectedContainerColor = clear,
        disabledSelectedContainerColor = clear,
        selectedLabelColor = selectedLabel,
        selectedLeadingIconColor = selectedLabel,
        selectedTrailingIconColor = selectedLabel,
    )
}

@Composable
private fun chipElevation(level: Dp): SelectableChipElevation = FilterChipDefaults.filterChipElevation(
    elevation = level,
    pressedElevation = level,
    focusedElevation = level,
    hoveredElevation = level,
    draggedElevation = level,
    disabledElevation = level,
)

/** The raised segment's shadow (Graphite Pro's selected chip). */
private val RaisedChipElevation = 1.dp
