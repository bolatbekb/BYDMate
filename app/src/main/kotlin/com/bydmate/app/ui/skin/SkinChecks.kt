package com.bydmate.app.ui.skin

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import com.bydmate.app.ui.theme.SkinState
import kotlin.math.floor
import androidx.compose.material3.Checkbox as M3Checkbox
import androidx.compose.material3.RadioButton as M3RadioButton

// fork: drop-ins for the M3 Checkbox and RadioButton (same names, parameters and defaults as M3
// 1.3.1). MATERIAL passes every argument straight to M3. SQUARE (Telemetry) draws a square box
// and a square ring with a square dot in M3's 20 dp box with its 2 dp padding, behind the same
// modifiers M3 uses: toggleable (Role.Checkbox) or selectable (Role.RadioButton), the 48 dp touch
// target, enabled, and M3's round ripple, 40 dp across. With `onCheckedChange` / `onClick` null
// the check is a picture, as in M3: no touch target and no click. The square faces take their
// colours from the skin palette (checkLook), not from `colors`, as the styled switches do.
//
// The branch between M3 and the square face may stay, as in Switch: a check is a leaf with no
// content, whose state (`checked`, `selected`) is the caller's, so a skin switch remounts nothing
// that remembers anything but M3's colour animation.

private val CheckRipple = ripple(bounded = false, radius = CheckRippleRadius)

// M3's check mark (Checkbox.kt): from (0.2, 0.5) down to (0.4, 0.7) and up to (0.8, 0.3) of the box
private const val MARK_LEFT_X = 0.2f
private const val MARK_LEFT_Y = 0.5f
private const val MARK_CROSS_X = 0.4f
private const val MARK_CROSS_Y = 0.7f
private const val MARK_RIGHT_X = 0.8f
private const val MARK_RIGHT_Y = 0.3f

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val look = checkLook(SkinState.current.kit.controls.check, enabled, SkinState.palette)
    if (look == null) {
        M3Checkbox(
            checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
            colors = colors, interactionSource = interactionSource,
        )
        return
    }
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = CheckRipple,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
    } else {
        Modifier
    }
    val face = remember(look, checked) { squareBox(look, checked) }
    Spacer(
        modifier.then(toggle).padding(CheckPadding)
            .wrapContentSize(Alignment.Center).requiredSize(CheckBoxSize).then(face),
    )
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val look = checkLook(SkinState.current.kit.controls.check, enabled, SkinState.palette)
    if (look == null) {
        M3RadioButton(
            selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors, interactionSource = interactionSource,
        )
        return
    }
    val select = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = CheckRipple,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
    } else {
        Modifier
    }
    val face = remember(look, selected) { squareRadio(look, selected) }
    Spacer(
        modifier.then(select).wrapContentSize(Alignment.Center)
            .padding(CheckPadding).requiredSize(CheckBoxSize).then(face),
    )
}

/** The SQUARE checkbox: [CheckLook.on] filled with the check mark when [checked], else an [CheckLook.off] square outline. */
private fun squareBox(look: CheckLook, checked: Boolean): Modifier = Modifier
    .then(if (look.alpha < 1f) Modifier.alpha(look.alpha) else Modifier)
    .drawWithCache {
        val stroke = floor(CheckStroke.toPx()).coerceAtLeast(1f)
        val half = stroke / 2f
        val ring = Stroke(stroke)
        val inner = Size(size.width - stroke, size.height - stroke)
        val w = size.width
        val mark = Path().apply {
            moveTo(w * MARK_LEFT_X, w * MARK_LEFT_Y)
            lineTo(w * MARK_CROSS_X, w * MARK_CROSS_Y)
            lineTo(w * MARK_RIGHT_X, w * MARK_RIGHT_Y)
        }
        val markStroke = Stroke(width = stroke, cap = StrokeCap.Square)
        onDrawBehind {
            if (checked) {
                drawRect(look.on)
                drawPath(mark, look.mark, style = markStroke)
            } else {
                drawRect(look.off, topLeft = Offset(half, half), size = inner, style = ring)
            }
        }
    }

/** The SQUARE radio button: a square ring, [CheckLook.on] with a square dot when [selected], else [CheckLook.off]. */
private fun squareRadio(look: CheckLook, selected: Boolean): Modifier = Modifier
    .then(if (look.alpha < 1f) Modifier.alpha(look.alpha) else Modifier)
    .drawWithCache {
        val stroke = floor(CheckStroke.toPx()).coerceAtLeast(1f)
        val half = stroke / 2f
        val ring = Stroke(stroke)
        val inner = Size(size.width - stroke, size.height - stroke)
        val dot = RadioDotSize.toPx()
        val dotAt = Offset((size.width - dot) / 2f, (size.height - dot) / 2f)
        val color = if (selected) look.on else look.off
        onDrawBehind {
            drawRect(color, topLeft = Offset(half, half), size = inner, style = ring)
            if (selected) drawRect(color, topLeft = dotAt, size = Size(dot, dot))
        }
    }
