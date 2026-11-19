package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.bydmate.app.R
import com.bydmate.app.ui.home.RetroTab
import com.bydmate.app.ui.home.common.px

// fork: the Graphite home's bottom menu (its menu card and keys). The «Graphite» app skin draws
// its bottom bar with the same card, key face and dividers (ui/skin/SkinNavigationBar.kt).

/** Icon and label of a menu key by route, in [lang]; an unknown route keeps its own label with sliders. */
internal fun menuItem(route: String, label: String, lang: GrLang = GrLang.RU): Pair<Int, String> {
    val w = lang.words
    return when (route) {
        "dashboard" -> R.drawable.gr_ic_home to w.menuHome
        "tech_panel" -> R.drawable.gr_ic_car to w.menuCar
        "trips" -> R.drawable.gr_ic_route to w.menuTrips
        "charges" -> R.drawable.gr_ic_plug to w.menuCharges
        "automation" -> R.drawable.gr_ic_flow to w.menuAutomation
        "settings" -> R.drawable.gr_ic_sliders to w.menuSettings
        else -> R.drawable.gr_ic_sliders to label
    }
}

/** The menu card's radius and padding (mockup px); the «Graphite» skin's bottom bar docks in the same card. */
internal const val MENU_CARD_RADIUS = 40f
internal const val MENU_CARD_PADDING = 12

/** The lit key's radius, the gap between its icon and label, the divider's clearance above and below (mockup px). */
private const val MENU_KEY_RADIUS = 30f
private const val MENU_KEY_GAP = 16
private const val MENU_DIVIDER_INSET = 22f

/**
 * The menu card (radius 40, padding 12): equal keys, 1 px Divider between them. The key of
 * [current] (the home's own «Главная») and a pressed key light (TabActive, Electric bar) and hide
 * their neighbouring dividers.
 */
@Composable
internal fun BottomMenu(tabs: List<RetroTab>, onTab: (RetroTab) -> Unit, modifier: Modifier, current: String? = null) {
    val p = LocalGr.current
    val sources = remember(tabs.size) { List(tabs.size) { MutableInteractionSource() } }
    val lit = sources.mapIndexed { i, s -> s.collectIsPressedAsState().value || tabs[i].route == current }
    Row(modifier.grCard(p, MENU_CARD_RADIUS).padding(px(MENU_CARD_PADDING))) {
        tabs.forEachIndexed { i, tab ->
            val divider = i > 0 && !lit[i] && !lit[i - 1]
            MenuKey(tab, lit[i], divider, sources[i], onTab)
        }
    }
}

@Composable
private fun RowScope.MenuKey(
    tab: RetroTab,
    active: Boolean,
    divider: Boolean,
    source: MutableInteractionSource,
    onTab: (RetroTab) -> Unit,
) {
    val p = LocalGr.current
    val (icon, label) = menuItem(tab.route, tab.label, LocalGrLang.current)
    val color = if (active) p.textPrimary else p.textSecondary
    MenuKeyFace(
        lit = active,
        modifier = Modifier.weight(1f).fillMaxHeight()
            .drawBehind {
                if (divider) {
                    val u = px(1).toPx()
                    drawMenuDivider(menuDividerBrush(p, size.height, u), 0f, u)
                }
            }
            .clickable(interactionSource = source, indication = null) { onTab(tab) },
    ) {
        GrIcon(icon, if (icon == R.drawable.gr_ic_car) 34f else 32f, color)
        Text(label, maxLines = 1, softWrap = false, style = grStyle(26f, 400, color = color, tnum = false))
    }
}

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

/** The 36 × 4 Electric bar under a pressed key, with a 12 px glow. */
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
