package com.bydmate.app.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.bydmate.app.ui.theme.SkinPalette

// fork: what the TOP, DOT and DOCK bottom bars draw (the bar, the selected key's mark and the dot),
// called by SkinNavigationBar.kt. All of it is drawn in drawWithCache: built when the size or
// the look changes, never per frame; the glows are gradients.

/**
 * The bar around the M3 one for TOP, DOT and DOCK (empty for the rest): TOP and DOT a 1 dp border
 * along the top edge (accent at 15 %, chartGrid), drawn over the bar's surface; DOCK the floating
 * card: [insets] below it as the GRAPHITE dock does, 8 dp from the bottom and the sides, the
 * container at 80 % with a 1 dp accent edge and a soft shadow, clipped to its 22 dp corners.
 */
internal fun barModifier(style: NavStyle, palette: SkinPalette, container: Color, insets: WindowInsets): Modifier = when (style) {
    NavStyle.TOP -> Modifier.topBorder(palette.accentGreen.copy(alpha = NAV_LINE_ALPHA))
    NavStyle.DOT -> Modifier.topBorder(palette.chartGrid)
    NavStyle.DOCK -> {
        val shape = RoundedCornerShape(NavDockRadius)
        Modifier
            .windowInsetsPadding(insets)
            .padding(start = NavDockMargin, end = NavDockMargin, bottom = NavDockMargin)
            .shadow(NavDockMargin, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(container.copy(alpha = NAV_DOCK_FILL_ALPHA), shape)
            .border(NavEdgeWidth, palette.accentGreen.copy(alpha = NAV_LINE_ALPHA), shape)
            .clip(shape)
    }
    else -> Modifier
}

private fun Modifier.topBorder(color: Color): Modifier = drawWithCache {
    val h = NavEdgeWidth.toPx()
    onDrawWithContent {
        drawContent()
        drawRect(color, Offset.Zero, Size(size.width, h))
    }
}

/**
 * DOCK's height: the 64 dp card, the 8 dp under it and the bottom inset, whatever height the caller
 * asks for (the caller's own height then follows inside it).
 */
internal fun Modifier.dockHeight(insets: WindowInsets): Modifier = layout { measurable, constraints ->
    val h = (NavDockHeight + NavDockMargin).roundToPx() + insets.getBottom(this)
    val placeable = measurable.measure(Constraints(constraints.minWidth, constraints.maxWidth, h, h))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * What the selected key of TOP and DOCK draws in its slot, behind the ripple (none when it is not
 * lit or in any other style): DOT a dot near the slot's bottom; TOP a 3 dp accent line on the top edge inset 14 dp each side with a
 * glow below it; DOCK a raised plate with a 24 x 3 dp accent bar near its bottom and a glow. The
 * glows are gradients.
 */
internal fun keyMark(look: NavLook): Modifier = when {
    !look.lit -> Modifier
    look.style == NavStyle.TOP -> Modifier.topLine(look.fill)
    look.style == NavStyle.DOCK -> Modifier.dockPlate(look.fill, look.edge)
    look.style == NavStyle.DOT -> Modifier.dotAtBottom(look.fill)
    else -> Modifier
}

private fun Modifier.topLine(color: Color): Modifier = drawWithCache {
    val inset = NavTopLineInset.toPx()
    val line = NavUnderlineHeight.toPx()
    val width = size.width - 2 * inset
    val glow = Brush.verticalGradient(listOf(color.copy(alpha = NAV_GLOW_ALPHA), Color.Transparent), 0f, line + NavTopGlow.toPx())
    onDrawBehind {
        translate(inset, 0f) {
            drawRect(glow, Offset.Zero, Size(width, line + NavTopGlow.toPx()))
            drawRect(color, Offset.Zero, Size(width, line))
        }
    }
}

private fun Modifier.dockPlate(plate: Color, accent: Color): Modifier = drawWithCache {
    val x = NavDockPlateInsetX.toPx()
    val y = NavDockPlateInsetY.toPx()
    val box = Size(size.width - 2 * x, size.height - 2 * y)
    val r = NavDockPlateRadius.toPx()
    val barW = NavDockBarWidth.toPx()
    val barH = NavUnderlineHeight.toPx()
    val barTop = y + box.height - NavDockBarGap.toPx() - barH
    val barLeft = (size.width - barW) / 2
    val centre = Offset(size.width / 2, barTop + barH / 2)
    val glowR = barW * 0.75f
    val glow = Brush.radialGradient(listOf(accent.copy(alpha = NAV_GLOW_ALPHA), Color.Transparent), centre, glowR)
    onDrawBehind {
        drawRoundRect(plate, Offset(x, y), box, CornerRadius(r))
        // the glow is a round gradient squeezed to the bar's width
        scale(1f, 0.5f, centre) { drawCircle(glow, glowR, centre) }
        drawRoundRect(accent, Offset(barLeft, barTop), Size(barW, barH), CornerRadius(barH / 2))
    }
}

/**
 * The centre of DOT's dot, from the top of the key's slot: [gap] above the slot's bottom edge, the
 * slot being the bar without the window insets. The bar is at least 64 dp whatever the text scale,
 * so the dot (radius [dotRadius]) stays whole inside it; a [gap] under the radius is lifted to it.
 */
fun navDotCentreY(barInnerHeight: Float, dotRadius: Float, gap: Float): Float =
    (barInnerHeight - maxOf(gap, dotRadius)).coerceAtLeast(dotRadius)

/** DOT's dot: round, centred on the key, [NavDotBottomGap] above the bar's inner bottom edge (navDotCentreY). */
private fun Modifier.dotAtBottom(color: Color): Modifier = drawWithCache {
    val d = NavDotSize.toPx()
    val c = Offset(size.width / 2, navDotCentreY(size.height, d / 2, NavDotBottomGap.toPx()))
    onDrawBehind { drawCircle(color, d / 2, c) }
}
