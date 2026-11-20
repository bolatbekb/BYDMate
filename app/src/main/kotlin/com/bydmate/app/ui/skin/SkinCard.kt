package com.bydmate.app.ui.skin

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.bydmate.app.ui.home.graphite.LocalGrLook
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.Card as M3Card

// fork: drop-ins for the M3 Card overloads (same names, parameters and defaults as M3 1.3.1).
// MATERIAL passes every argument straight to M3; the other styles change the container alpha,
// the border, the elevation or draw a stripe, never the layout. A caller's explicit border wins.
// GRAPHITE lays the Graphite home's card (grCard, the live look) under a see-through container.
//
// Each overload makes one M3Card call inside one provider, whatever the style: the style picks
// the arguments, never the call. A skin switch then keeps the card's content and everything it
// remembers (the settings page's scroll, the skin picker's row). For CardLook.MATERIAL (and a
// nested STRIPE card) every argument below comes out as the caller's own: lookModifier adds
// Modifier, alpha 1 keeps the colours, the elevation stays and the border is the caller's.
//
// A card provides its depth + 1 to its content (LocalSkinCardDepth), so a card inside a skinned
// card draws the nested look (cardLook's `nested`).

/** How many skinned Cards of this window enclose this point: 0 outside every card. */
internal val LocalSkinCardDepth = staticCompositionLocalOf { 0 }

/**
 * The window [LocalSkinCardDepth] counts in. A Dialog's composition inherits the locals of the
 * place that opens it, so a dialog opened inside a card would see that card's depth; there the
 * dialog's own surface card is the outermost card of its window again, depth 0.
 */
private val LocalSkinCardWindow = staticCompositionLocalOf<View?> { null }

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val window = LocalView.current
    val depth = skinCardDepth(window)
    val look = activeCardLook(depth, window)
    ProvideSkinCard(look, window, depth) {
        M3Card(
            modifier = modifier.then(lookModifier(look, shape)),
            shape = shape,
            colors = colors.withContainerAlpha(look.containerAlpha),
            elevation = if (look.flat) flatElevation() else elevation,
            border = border ?: lookBorder(look),
            content = content,
        )
    }
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
    val window = LocalView.current
    val depth = skinCardDepth(window)
    val look = activeCardLook(depth, window)
    ProvideSkinCard(look, window, depth) {
        M3Card(
            onClick = onClick,
            modifier = modifier.then(lookModifier(look, shape)),
            enabled = enabled,
            shape = shape,
            colors = colors.withContainerAlpha(look.containerAlpha),
            elevation = if (look.flat) flatElevation() else elevation,
            border = border ?: lookBorder(look),
            interactionSource = interactionSource,
            content = content,
        )
    }
}

/** The depth of this point in skinned Cards of [window]; a card of another window counts as none. */
@Composable
private fun skinCardDepth(window: View): Int =
    if (LocalSkinCardWindow.current === window) LocalSkinCardDepth.current else 0

/**
 * One provider, whatever the style: the Graphite look for a GRAPHITE card (the one in effect
 * otherwise), and depth + 1 for the content.
 */
@Composable
private fun ProvideSkinCard(look: CardLook, window: View, depth: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGrLook provides graphiteLookFor(look.graphite),
        LocalSkinCardWindow provides window,
        LocalSkinCardDepth provides depth + 1,
        content = content,
    )
}

/**
 * The active skin's card look at [depth] in [window]: a card inside another skinned card takes
 * the nested look; an outermost card that is a dialog's surface keeps a solid container (inside a
 * Compose Dialog, the root view's parent provides the dialog window). A snapshot read of the skin.
 */
private fun activeCardLook(depth: Int, window: View): CardLook {
    val style = SkinState.current.kit.controls.card
    if (style == CardStyle.MATERIAL) return CardLook.MATERIAL
    val look = cardLook(style, SkinState.palette, nested = depth > 0)
    return if (depth == 0 && window.parent is DialogWindowProvider) look.inDialog() else look
}

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

/** The border of [look], as the buttons and chips draw theirs (skinBorder, SkinButtons.kt). */
@Composable
private fun lookBorder(look: CardLook): BorderStroke? = skinBorder(look.border, look.borderColor, look.borderFadeColor)

/**
 * What [look] draws besides the M3 card, `Modifier` when nothing:
 * - STRIPE: the bar along the top edge (the start edge when not [CardLook.stripeTop]), drawn over
 *   the card and clipped to its [shape], so the layout does not move;
 * - BRACKETS: four L-marks on the card's corners, over its own bounds, clipped by nothing;
 * - GRAPHITE: the Graphite home's card under the container, inside ProvideSkinCard (the live look).
 * Each is remembered per its colours (and width, shape), so a recomposition keeps the cache.
 */
@Composable
private fun lookModifier(look: CardLook, shape: Shape): Modifier {
    val stripe = if (look.stripe) {
        remember(look.stripeColor, look.stripeWidth, look.stripeTop, shape) {
            stripe(look.stripeColor, look.stripeWidth, look.stripeTop, shape)
        }
    } else {
        Modifier
    }
    val brackets = if (look.brackets) {
        remember(look.bracketColor) { brackets(look.bracketColor) }
    } else {
        Modifier
    }
    return stripe.then(brackets).then(if (look.graphite) Modifier.graphiteSkinCard(shape) else Modifier)
}
