package com.bydmate.app.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.home.graphite.LocalGrLook
import com.bydmate.app.ui.theme.SkinState
import kotlin.math.roundToInt
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.TextButton as M3TextButton

// fork: drop-ins for the M3 Button, OutlinedButton and TextButton (same names, parameters and
// defaults as M3 1.3.1). MATERIAL passes every argument straight to M3. The other card styles
// (buttonLook) change the container, the border, the elevation, the content colour of M3's
// default colours, and M3's default pill shape (skinButtonShape); never the caller's text, sizes,
// padding or content. A caller's own border wins over the style's.
//
// Each wrapper makes one M3 call (Button: inside one provider), whatever the style: the style picks
// the arguments, never the call, so a skin switch keeps the button and its content.
//
// The Graphite pill (GRAPHITE's filled button, and its chips, SkinChip.kt) is drawn by the Graphite
// home's grPill on the button's own surface. M3 pads a button up to its 48 dp touch target inside
// the modifier the caller passes, so a pill drawn there would be the touch target's size, not the
// 40 dp button's. Under a pill the wrapper turns M3's padding off (LocalMinimumInteractiveComponentSize)
// and pads outside the pill instead (skinPill: SkinTouchTargetElement, the same measure as M3's):
// the same size, the same centring, the same touch area. The caller's content then sees no
// minimum size either; a button holds text and icons, never a control of its own.

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val fill = if (enabled) colors.containerColor else colors.disabledContainerColor
    val look = activeButtonLook(ButtonKind.FILLED, fill)
    val face = skinButtonShape(shape, ButtonDefaults.shape, SkinState.current.kit.corners)
    val styled = skinBorder(look.border, look.borderColor, look.borderFadeColor)
    ProvidePillFrame(look.pill) { touch ->
        M3Button(
            onClick = onClick,
            modifier = if (look.pill) modifier.skinPill(touch, fill, GraphiteLookState.scheme.palette.tabActiveEdge, face) else modifier,
            enabled = enabled,
            shape = face,
            colors = colors.withLook(look, isDefault = false),
            elevation = flatButtonElevation(look.flat, elevation),
            border = styledBorder(border, null, styled, drop = look.pill),
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val fill = if (enabled) colors.containerColor else colors.disabledContainerColor
    val look = activeButtonLook(ButtonKind.OUTLINED, fill)
    val face = skinButtonShape(shape, ButtonDefaults.outlinedShape, SkinState.current.kit.corners)
    val styled = skinBorder(look.border, look.borderColor, look.borderFadeColor)
    // M3's own border, to tell «left as it is» from a border the caller chose; only a style that replaces it asks
    val default = if (styled != null) ButtonDefaults.outlinedButtonBorder(enabled) else null
    M3OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = face,
        colors = colors.withLook(look, isDefault = false),
        elevation = flatButtonElevation(look.flat, elevation),
        border = styledBorder(border, default, styled, drop = false),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val fill = if (enabled) colors.containerColor else colors.disabledContainerColor
    val look = activeButtonLook(ButtonKind.TEXT, fill)
    val face = skinButtonShape(shape, ButtonDefaults.textShape, SkinState.current.kit.corners)
    val styled = skinBorder(look.border, look.borderColor, look.borderFadeColor)
    // the style's content colour replaces M3's default only, never a colour the caller chose
    val isDefault = look.contentColor != Color.Unspecified && colors == ButtonDefaults.textButtonColors()
    M3TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = face,
        colors = colors.withLook(look, isDefault),
        elevation = flatButtonElevation(look.flat, elevation),
        border = styledBorder(border, null, styled, drop = false),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** The active skin's look for a [kind] of button whose container is [fill]; a snapshot read of the skin. */
private fun activeButtonLook(kind: ButtonKind, fill: Color): ButtonLook {
    val style = SkinState.current.kit.controls.card
    if (style == CardStyle.MATERIAL) return ButtonLook.MATERIAL
    return buttonLook(style, kind, SkinState.palette, fill)
}

/**
 * The border of [kind]: [color] solid, or [color] fading to [fade] (TOP_HIGHLIGHT), or the Graphite
 * raised key's edge; null for NONE. Remembered per colour. Cards (SkinCard.kt), buttons and chips share it.
 */
@Composable
internal fun skinBorder(kind: CardBorderKind, color: Color, fade: Color): BorderStroke? = when (kind) {
    CardBorderKind.NONE -> null
    CardBorderKind.SOLID -> remember(color) { BorderStroke(CardBorderWidth, color) }
    CardBorderKind.TOP_HIGHLIGHT -> remember(color, fade) {
        BorderStroke(CardBorderWidth, Brush.verticalGradient(listOf(color, fade)))
    }
    CardBorderKind.GRAPHITE_EDGE -> graphiteEdgeBorder()
}

/** No elevation when [flat]; a caller's null stays null, so the M3 button keeps its own branch. */
@Composable
private fun flatButtonElevation(flat: Boolean, elevation: ButtonElevation?): ButtonElevation? =
    if (flat && elevation != null) {
        ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        )
    } else {
        elevation
    }

/**
 * One provider around a skinned button's or chip's M3 call, whatever the style. Under a Graphite
 * [pill]: the live Graphite look (grPill reads its relief) and M3's touch target padding turned
 * off, as skinPill pads outside the pill instead with the [Dp] it is handed (the value in effect
 * here). Otherwise both locals keep the values in effect.
 */
@Composable
internal fun ProvidePillFrame(pill: Boolean, content: @Composable (touch: Dp) -> Unit) {
    val touch = LocalMinimumInteractiveComponentSize.current
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides if (pill) Dp.Unspecified else touch,
        LocalGrLook provides graphiteLookFor(pill),
    ) {
        content(touch)
    }
}

/**
 * The Graphite pill under an M3 button or chip: the touch target padding M3 would add ([touch]),
 * then the pill in [fill] with the [edge] rim on the control's own bounds, cornered as [shape].
 * Call it inside [ProvidePillFrame] with the pill on.
 */
@Composable
internal fun Modifier.skinPill(touch: Dp, fill: Color, edge: Color, shape: Shape): Modifier =
    this.then(SkinTouchTargetElement(touch)).graphiteSkinPill(fill, edge, shape)

/**
 * M3 1.3.1's minimumInteractiveComponentSize with the size given ([size]), not read where the node
 * sits: under a pill the M3 node is turned off, and this one takes its place outside the pill. It
 * measures the content as it is and grows to [size] at least, the content centred; an unspecified
 * or zero size does nothing, as in M3.
 */
private data class SkinTouchTargetElement(val size: Dp) : ModifierNodeElement<SkinTouchTargetNode>() {
    override fun create(): SkinTouchTargetNode = SkinTouchTargetNode(size)

    override fun update(node: SkinTouchTargetNode) {
        node.size = size
    }
}

private class SkinTouchTargetNode(var size: Dp) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val min = if (size.isSpecified && size > 0.dp) size.roundToPx() else 0
        val width = maxOf(placeable.width, min)
        val height = maxOf(placeable.height, min)
        return layout(width, height) {
            placeable.place(((width - placeable.width) / 2f).roundToInt(), ((height - placeable.height) / 2f).roundToInt())
        }
    }
}
