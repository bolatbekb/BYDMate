package com.bydmate.app.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp

// fork: a soft glow around a rounded rectangle, drawn as rings that fade out (no blur filter, so it
// is the same on every device and cheap to cache). The switch, the chip, the button and the slider
// of Volt draw their glow with it.

internal const val GLOW_STEPS = 7

/**
 * A glow of [color] around the rounded rectangle at [topLeft] of [size] ([corner] radius): the
 * colour at its own alpha at the edge, fading to nothing over [spread] px, outside the shape.
 */
internal fun DrawScope.drawRectGlow(topLeft: Offset, size: Size, corner: Float, color: Color, spread: Float) {
    if (color.alpha <= 0f || spread <= 0f) return
    val ring = spread / GLOW_STEPS
    for (i in 0 until GLOW_STEPS) {
        val fade = 1f - (i + 0.5f) / GLOW_STEPS
        val grow = ring * i
        val w = ring + 1f // overlap a pixel so no gap shows between rings
        val d = grow + w / 2f
        drawRoundRect(
            color.copy(alpha = color.alpha * fade * fade),
            Offset(topLeft.x - d, topLeft.y - d),
            Size(size.width + d * 2f, size.height + d * 2f),
            CornerRadius(corner + d),
            Stroke(w),
        )
    }
}

/** [this] at [alpha] times its own alpha. */
internal fun Color.fade(alpha: Float): Color = if (alpha >= 1f) this else copy(alpha = this.alpha * alpha)

/**
 * A glow of [glow] behind a chip or button ([spread] wide), and, when [start] is specified, a
 * horizontal gradient fill [start] to [end] over its bounds, both cornered as [shape]. The touch
 * target padding M3 would add ([touch]) comes first, outside what is drawn, as skinPill does: call
 * it inside ProvidePillFrame with the frame on. Cached per colour, shape and spread.
 */
@Composable
internal fun Modifier.skinGlow(
    touch: Dp,
    glow: Color,
    spread: Dp,
    shape: Shape,
    start: Color = Color.Unspecified,
    end: Color = Color.Unspecified,
): Modifier {
    val paint = remember(glow, spread, shape, start, end) {
        Modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val corner = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            val spreadPx = spread.toPx()
            val fill = if (start.isSpecified) Brush.horizontalGradient(listOf(start, end)) else null
            onDrawBehind {
                drawRectGlow(Offset.Zero, size, corner, glow, spreadPx)
                if (fill != null) drawOutline(outline, fill)
            }
        }
    }
    return this.then(SkinTouchTargetElement(touch)).then(paint)
}
