package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.bydmate.app.ui.home.common.px

// fork: the Graphite menu's card, key face and dividers. The Graphite home drew its own menu with
// them; since 2026-11-20 the app's bottom bar stays under that home too, and the «Graphite» skin
// draws that bar with these parts (ui/skin/SkinNavigationBar.kt).

/** The menu card's radius and padding (mockup px): the «Graphite» skin's bottom bar docks in this card. */
internal const val MENU_CARD_RADIUS = 40f
internal const val MENU_CARD_PADDING = 12

/** The lit key's radius, the gap between its icon and label, the divider's clearance above and below (mockup px). */
private const val MENU_KEY_RADIUS = 30f
private const val MENU_KEY_GAP = 16
private const val MENU_DIVIDER_INSET = 22f

/**
 * A menu key's face, here and in the «Graphite» skin's bottom bar (ui/skin/SkinNavigationBar.kt):
 * [content], the icon and the label in one row, centred; [lit], the raised key (TabActive pill)
 * with the glowing bar under the row. [modifier] sizes the key, its pill fills it.
 */
@Composable
internal fun MenuKeyFace(lit: Boolean, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val p = LocalGr.current
    Box(
        modifier.then(if (lit) Modifier.grPill(p.tabActive, p.tabActiveEdge, p.edgeStyle, MENU_KEY_RADIUS) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(px(MENU_KEY_GAP)),
            content = content,
        )
        if (lit) ActiveBar(p.highlight, Modifier.align(Alignment.BottomCenter).padding(bottom = px(10)))
    }
}

/** The menu divider's line for a key [height] px tall: TextTertiary at 55 % in the middle, fading out at both ends. */
internal fun menuDividerBrush(p: GrPalette, height: Float, u: Float): Brush = Brush.verticalGradient(
    0f to Color.Transparent, 0.5f to p.textTertiary.copy(alpha = 0.55f), 1f to Color.Transparent,
    startY = MENU_DIVIDER_INSET * u, endY = height - MENU_DIVIDER_INSET * u,
)

/** A visible separator centred on [x], 1 px wide, clear of the key's top and bottom; [u] is one mockup px. */
internal fun DrawScope.drawMenuDivider(brush: Brush, x: Float, u: Float) {
    drawRect(brush, topLeft = Offset(x - u / 2, MENU_DIVIDER_INSET * u), size = Size(u, size.height - 2 * MENU_DIVIDER_INSET * u))
}

/** The 36 × 4 Electric bar under a lit key, with a 12 px glow. */
@Composable
private fun ActiveBar(color: Color, modifier: Modifier) {
    Box(
        modifier.size(px(36), px(4)).drawWithCache {
            val u = px(1).toPx()
            val r = 2 * u
            val halo = blurPaint(color, 12 * u)
            onDrawBehind {
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, halo) }
                drawRoundRect(color, cornerRadius = CornerRadius(r))
            }
        },
    )
}
