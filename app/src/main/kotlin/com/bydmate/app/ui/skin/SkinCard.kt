package com.bydmate.app.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.Card as M3Card

// fork: drop-ins for the M3 Card overloads (same names, parameters and defaults as M3 1.3.1).
// MATERIAL passes every argument straight to M3; the other styles change the container alpha,
// the border, the elevation or draw a stripe, never the layout. A caller's explicit border wins.

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = activeCardLook()
    if (look.passThrough) {
        M3Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)
        return
    }
    M3Card(
        modifier = modifier.then(stripeModifier(look, shape)),
        shape = shape,
        colors = colors.withContainerAlpha(look.containerAlpha),
        elevation = if (look.flat) flatElevation() else elevation,
        border = border ?: lookBorder(look),
        content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = activeCardLook()
    if (look.passThrough) {
        M3Card(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, colors = colors,
            elevation = elevation, border = border, interactionSource = interactionSource, content = content,
        )
        return
    }
    M3Card(
        onClick = onClick,
        modifier = modifier.then(stripeModifier(look, shape)),
        enabled = enabled,
        shape = shape,
        colors = colors.withContainerAlpha(look.containerAlpha),
        elevation = if (look.flat) flatElevation() else elevation,
        border = border ?: lookBorder(look),
        interactionSource = interactionSource,
        content = content,
    )
}

/** The active skin's card look; a card that is a dialog's surface keeps a solid container. */
@Composable
private fun activeCardLook(): CardLook {
    val style = SkinState.current.kit.controls.card
    if (style == CardStyle.MATERIAL) return CardLook()
    val look = cardLook(style, SkinState.palette)
    return if (inDialogWindow()) look.inDialog() else look
}

/** True inside a Compose Dialog: its root layout provides the dialog window. */
@Composable
internal fun inDialogWindow(): Boolean = LocalView.current.parent is DialogWindowProvider

private fun CardColors.withContainerAlpha(alpha: Float): CardColors =
    if (alpha == 1f) this else copy(
        containerColor = containerColor.copy(alpha = containerColor.alpha * alpha),
        disabledContainerColor = disabledContainerColor.copy(alpha = disabledContainerColor.alpha * alpha),
    )

@Composable
private fun flatElevation(): CardElevation = CardDefaults.cardElevation(
    defaultElevation = 0.dp,
    pressedElevation = 0.dp,
    focusedElevation = 0.dp,
    hoveredElevation = 0.dp,
    draggedElevation = 0.dp,
    disabledElevation = 0.dp,
)

@Composable
private fun lookBorder(look: CardLook): BorderStroke? = when (look.border) {
    CardBorderKind.NONE -> null
    CardBorderKind.SOLID -> remember(look.borderColor) { BorderStroke(CardBorderWidth, look.borderColor) }
    CardBorderKind.TOP_HIGHLIGHT -> remember(look.borderColor, look.borderFadeColor) {
        BorderStroke(CardBorderWidth, Brush.verticalGradient(listOf(look.borderColor, look.borderFadeColor)))
    }
}

/**
 * The STRIPE bar along the start edge, drawn over the card and clipped to its [shape], so the
 * layout does not move. Remembered per (colour, width, shape): a recomposition keeps the cache.
 */
@Composable
private fun stripeModifier(look: CardLook, shape: Shape): Modifier {
    if (!look.stripe) return Modifier
    return remember(look.stripeColor, look.stripeWidth, shape) { stripe(look.stripeColor, look.stripeWidth, shape) }
}

private fun stripe(color: Color, width: Dp, shape: Shape): Modifier = Modifier.drawWithCache {
    val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val w = width.toPx()
    val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - w
    val bar = Size(w, size.height)
    onDrawWithContent {
        drawContent()
        clipPath(clip) { drawRect(color, topLeft = Offset(left, 0f), size = bar) }
    }
}
