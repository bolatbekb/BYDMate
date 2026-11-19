package com.bydmate.app.ui.skin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinPalette
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.Switch as M3Switch

// fork: drop-in for the M3 Switch (same name, parameters and defaults as M3 1.3.1). MATERIAL
// passes every argument straight to M3. SQUARE, PILL and OUTLINE draw their own face in the M3
// box of 52 x 32 dp, with the same toggleable (Role.Switch, 48 dp touch target, enabled) and the
// same `onCheckedChange == null` behaviour: then the switch is a picture, not a control.
// The styled faces take their colours from the skin palette (switchLook), not from `colors`.
// GRAPHITE adds the Graphite home's rims and relief over its face (GraphiteSkinParts.kt).

internal const val THUMB_MOVE_MS = 100 // as M3
internal const val PRESS_HALO_ALPHA = 0.12f
internal val PressHaloRadius = 20.dp // the M3 state layer, 40 dp across

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val style = SkinState.current.kit.controls.switch
    val look = switchLook(style, checked, enabled, SkinState.palette)
    // a thumb icon needs the M3 thumb; the styled faces have no room for one. The branch may
    // stay: the switch is a leaf whose state is `checked`, the caller's, and its thumbContent
    // always takes this branch, so a skin switch remounts nothing that remembers anything
    if (look == null || thumbContent != null) {
        M3Switch(
            checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, thumbContent = thumbContent,
            enabled = enabled, colors = colors, interactionSource = interactionSource,
        )
        return
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = source,
                indication = null,
            )
    } else {
        Modifier
    }
    val pressed = source.collectIsPressedAsState()
    val face = modifier.then(toggle).wrapContentSize(Alignment.Center).requiredSize(SwitchWidth, SwitchHeight)
    if (style == SwitchStyle.GRAPHITE) {
        GraphiteSwitchFace(look = look, checked = checked, pressed = pressed, modifier = face)
    } else {
        SwitchFace(look = look, checked = checked, pressed = pressed, modifier = face)
    }
}

/**
 * A switch of [style] drawn in [palette], not interactive: the skin picker shows other skins'
 * switches with it. The live Switch draws through the same code, so the two always match.
 * MATERIAL is the M3 switch in the app's own colours (bydSwitchColors, from [palette]).
 */
@Composable
internal fun SkinSwitchFace(style: SwitchStyle, palette: SkinPalette, checked: Boolean, modifier: Modifier = Modifier) {
    val look = switchLook(style, checked, enabled = true, palette)
    if (look == null) {
        M3Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.navyMid,
                checkedTrackColor = palette.accentGreen,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = palette.navyMid,
                uncheckedTrackColor = palette.textMuted,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
        return
    }
    val face = modifier.requiredSize(SwitchWidth, SwitchHeight)
    if (style == SwitchStyle.GRAPHITE) {
        GraphiteSwitchFace(look = look, checked = checked, pressed = null, modifier = face)
    } else {
        SwitchFace(look = look, checked = checked, pressed = null, modifier = face)
    }
}

/** The styled face: the track, the thumb sliding to [checked], a halo while [pressed]. */
@Composable
private fun SwitchFace(look: SwitchLook, checked: Boolean, pressed: State<Boolean>?, modifier: Modifier) {
    val fraction = animateFloatAsState(if (checked) 1f else 0f, tween(THUMB_MOVE_MS), label = "skinSwitchThumb")
    Spacer(
        modifier
            .then(if (look.alpha < 1f) Modifier.alpha(look.alpha) else Modifier)
            .drawWithCache {
                val trackH = look.trackHeight.toPx()
                val trackTop = (size.height - trackH) / 2f
                val trackRadius = CornerRadius(look.trackCorner.toPx().coerceAtMost(trackH / 2f))
                val trackStrokePx = look.trackStroke.toPx()
                val trackStroke = Stroke(trackStrokePx)
                val half = trackStrokePx / 2f
                val trackOutlineRadius = CornerRadius((trackRadius.x - half).coerceAtLeast(0f))
                val thumb = look.thumbSize.toPx()
                val thumbTop = (size.height - thumb) / 2f
                val inset = look.thumbInset.toPx()
                val thumbRadius = CornerRadius(look.thumbCorner.toPx())
                val ringPx = look.thumbRing.toPx()
                val ringStroke = Stroke(ringPx)
                val ringRadius = CornerRadius((thumbRadius.x - ringPx / 2f).coerceAtLeast(0f))
                val halo = PressHaloRadius.toPx()
                val rtl = layoutDirection == LayoutDirection.Rtl
                onDrawBehind {
                    if (look.trackFilled) {
                        drawRoundRect(look.stateColor, Offset(0f, trackTop), Size(size.width, trackH), trackRadius)
                    } else {
                        drawRoundRect(
                            look.stateColor, Offset(half, trackTop + half),
                            Size(size.width - trackStrokePx, trackH - trackStrokePx), trackOutlineRadius, trackStroke,
                        )
                    }
                    val x = switchThumbX(size.width, thumb, inset, fraction.value, rtl)
                    if (pressed?.value == true) {
                        drawCircle(
                            look.thumbColor.copy(alpha = PRESS_HALO_ALPHA), halo,
                            Offset(x + thumb / 2f, size.height / 2f),
                        )
                    }
                    if (look.thumbFill.alpha > 0f) {
                        drawRoundRect(look.thumbFill, Offset(x, thumbTop), Size(thumb, thumb), thumbRadius)
                    }
                    if (ringPx > 0f) {
                        drawRoundRect(
                            look.thumbColor, Offset(x + ringPx / 2f, thumbTop + ringPx / 2f),
                            Size(thumb - ringPx, thumb - ringPx), ringRadius, ringStroke,
                        )
                    }
                }
            },
    )
}
