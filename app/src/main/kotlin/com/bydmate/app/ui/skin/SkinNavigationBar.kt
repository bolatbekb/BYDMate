package com.bydmate.app.ui.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.common.px
import com.bydmate.app.ui.home.graphite.GrPalette
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.home.graphite.LocalGr
import com.bydmate.app.ui.home.graphite.LocalGrLook
import com.bydmate.app.ui.home.graphite.MENU_CARD_PADDING
import com.bydmate.app.ui.home.graphite.MENU_CARD_RADIUS
import com.bydmate.app.ui.home.graphite.MenuKeyFace
import com.bydmate.app.ui.home.graphite.drawMenuDivider
import com.bydmate.app.ui.home.graphite.grCard
import com.bydmate.app.ui.home.graphite.menuDividerBrush
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.NavigationBar as M3NavigationBar
import androidx.compose.material3.NavigationBarItem as M3NavigationBarItem

// fork: drop-ins for the M3 NavigationBar and NavigationBarItem (same names, parameters and
// defaults as M3 1.3.1). MATERIAL passes every argument straight to M3.
// - GRAPHITE is the Graphite home's menu, drawn with its code (home/graphite/GraphiteMenu.kt) in
//   the live look (GraphiteLookState). The bar keeps the caller's height and colour; inside it
//   sits the home's menu card, and each key is the home's MenuKeyFace with the caller's icon and
//   label in one row, lit (the raised pill and the glowing bar) when selected or pressed. The dock
//   draws the home's fading dividers between the keys, none beside a lit one.
// - TOP and DOT keep the M3 bar and items, add a 1 dp top border and colour the key; TOP draws a
//   glowing line on the selected slot's top edge, DOT a dot under the label. DOCK floats the bar as
//   a card 8 dp in from the bottom and the sides (insets handled as the GRAPHITE dock does) with a
//   raised plate and a glowing bar under the selected key.
// - CUT, UNDERLINE and OUTLINE keep the M3 bar and items; the fork draws the selected key's
//   indicator (navLook) under the icon, and the M3 pill is made transparent.
//
// Each wrapper makes one M3 call inside one provider, whatever the style: the style picks the
// arguments, never the call. A skin switch then keeps the bar, its items and what they remember
// (the item's press and animation state). The slot content does follow the style: GRAPHITE puts
// the caller's icon and label in one row in the icon slot, the indicator styles wrap the icon; so
// the caller's Icon and Text, two stateless leaves, are built again on a switch to or from them.

/** M3 pads its row by the insets; the dock pads outside its card instead and hands M3 none. */
private val NoInsets = WindowInsets(0, 0, 0, 0)

/**
 * Between the bar's edges and the dock's card: the Graphite home's 32 px frame at the sides, 6 px
 * above and below. In the author's 64 dp bar the card is then the home's 116 px and its keys 92 px.
 */
private val DockSidePadding = px(32)
private val DockEndPadding = px(6)

/** The caller's Material icon in a Graphite key: 20 dp, so its glyph is about the home's 16 dp icons. */
private val NavKeyIconSize = 20.dp

/**
 * The keys of a GRAPHITE dock: where each one sits in the bar's row and whether it is lit. A key
 * joins when it enters (with its lit state), places itself on every layout and leaves when it
 * goes. The dock reads them in its draw cache, so a key that moves, lights or leaves redraws the
 * dividers; nothing is read per frame.
 */
@Stable
internal class NavDock {
    private class Key(val lit: State<Boolean>) {
        var left by mutableFloatStateOf(Float.NaN)
        var right by mutableFloatStateOf(Float.NaN)
    }

    private val keys = mutableStateMapOf<Any, Key>()

    fun join(id: Any, lit: State<Boolean>) {
        keys[id] = Key(lit)
    }

    fun place(id: Any, left: Float, right: Float) {
        val key = keys[id] ?: return
        key.left = left
        key.right = right
    }

    fun leave(id: Any) {
        keys.remove(id)
    }

    /** The keys placed so far, in the row's px. */
    fun spans(): List<NavKeySpan> =
        keys.values.filter { !it.left.isNaN() }.map { NavKeySpan(it.left, it.right, it.lit.value) }
}

/** The dock of the GRAPHITE bar around this point; null under any other style. */
internal val LocalNavDock = staticCompositionLocalOf<NavDock?> { null }

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor),
    tonalElevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit,
) {
    val style = SkinState.current.kit.controls.nav
    val dock = style == NavStyle.GRAPHITE
    val keys = remember { NavDock() }
    val palette = SkinState.palette
    val dockHeight = remember(windowInsets) { Modifier.dockHeight(windowInsets) }
    val barLook = remember(style, palette, containerColor, windowInsets) { barModifier(style, palette, containerColor, windowInsets) }
    CompositionLocalProvider(
        LocalNavDock provides if (dock) keys else null,
        LocalGrLook provides graphiteLookFor(dock),
    ) {
        M3NavigationBar(
            // the caller's height stays the bar's: the content above does not move
            modifier = when {
                dock -> modifier.graphiteSkinDock(containerColor, windowInsets, keys)
                // DOCK's card is a fixed 64 dp: enough at the supported text scales (<= 1.3)
                style == NavStyle.DOCK -> dockHeight.then(modifier).then(barLook)
                else -> modifier.then(barLook)
            },
            containerColor = if (dock || style == NavStyle.DOCK) Color.Transparent else containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            windowInsets = if (dock || style == NavStyle.DOCK) NoInsets else windowInsets,
            content = content,
        )
    }
}

// LocalRippleConfiguration (experimental in M3 1.3.1): a GRAPHITE key lights instead of rippling
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val style = SkinState.current.kit.controls.nav
    val graphite = style == NavStyle.GRAPHITE
    // a GRAPHITE key also lights while pressed, as the Graphite home's menu does, so it hands M3
    // a source it can read when the caller gave none
    val ownSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: ownSource
    val pressed = source.collectIsPressedAsState()
    val look = navLook(style, selected || (graphite && pressed.value), SkinState.palette)
    val dock = LocalNavDock.current
    val id = remember { Any() }
    val lit = rememberUpdatedState(graphite && look.lit)
    DisposableEffect(dock, id) {
        dock?.join(id, lit)
        onDispose { dock?.leave(id) }
    }
    val slot = remember(dock, id) { if (dock == null) Modifier else Modifier.dockSlot(dock, id) }
    val noPill = remember(colors) { colors.copy(selectedIndicatorColor = Color.Transparent) }
    // TOP, DOT and DOCK colour the key through M3's own colours, so its icon and label follow M3
    val keyColors = remember(colors, look) {
        if (look.style == NavStyle.TOP || look.style == NavStyle.DOT || look.style == NavStyle.DOCK) {
            noPill.copy(
                selectedIconColor = look.content, selectedTextColor = look.content,
                unselectedIconColor = look.content, unselectedTextColor = look.content,
            )
        } else {
            noPill
        }
    }
    val mark = remember(look) { keyMark(look) }
    val keyColor = if (enabled) look.content else colors.disabledIconColor
    val graphiteKey: @Composable () -> Unit = {
        GraphiteNavKey(look, keyColor, icon, if (alwaysShowLabel || selected) label else null)
    }
    val indicatorIcon: @Composable () -> Unit = { NavIndicatorIcon(look, icon) }
    CompositionLocalProvider(
        LocalRippleConfiguration provides if (graphite) null else LocalRippleConfiguration.current,
        LocalGr provides graphitePaletteFor(graphite),
        LocalGrLook provides graphiteLookFor(graphite),
    ) {
        M3NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = when (style) {
                NavStyle.MATERIAL, NavStyle.TOP, NavStyle.DOT, NavStyle.DOCK -> icon
                NavStyle.GRAPHITE -> graphiteKey
                NavStyle.CUT, NavStyle.UNDERLINE, NavStyle.OUTLINE -> indicatorIcon
            },
            modifier = (if (graphite) modifier.then(slot) else modifier).then(mark),
            enabled = enabled,
            // GRAPHITE has the label in the key's row
            label = if (graphite) null else label,
            alwaysShowLabel = alwaysShowLabel,
            colors = if (style == NavStyle.MATERIAL) colors else keyColors,
            interactionSource = if (graphite) source else interactionSource,
        )
    }
}

/** Tells [dock] where the key [id] sits in the bar's row, on every layout. */
private fun Modifier.dockSlot(dock: NavDock, id: Any): Modifier = onPlaced { c ->
    val left = c.positionInParent().x
    dock.place(id, left, left + c.size.width)
}

/**
 * The caller's icon with the indicator of [look] behind it when lit, in M3's indicator box (the
 * icon and 20 dp on each side, 4 dp above and below): CUT a plate with cut corners and an edge,
 * OUTLINE a pill ring, UNDERLINE a bar along the box's bottom. Drawn outside the icon's bounds,
 * so the M3 layout stays as it is.
 */
@Composable
private fun NavIndicatorIcon(look: NavLook, icon: @Composable () -> Unit) {
    Box(remember(look) { if (look.lit) Modifier.indicator(look) else Modifier }) { icon() }
}

private fun Modifier.indicator(look: NavLook): Modifier = drawWithCache {
    val padX = NavIndicatorPadX.toPx()
    val padY = NavIndicatorPadY.toPx()
    val box = Size(size.width + 2 * padX, size.height + 2 * padY)
    val edge = NavEdgeWidth.toPx()
    val bar = NavUnderlineHeight.toPx()
    val shape = if (look.style == NavStyle.CUT) CutCornerShape(NavCutCorner) else CircleShape
    val plate = Path().apply { addOutline(shape.createOutline(box, layoutDirection, this@drawWithCache)) }
    // the edge is stroked on its centre line, so its path is inset by half the edge
    val ring = Path().apply {
        addOutline(shape.createOutline(Size(box.width - edge, box.height - edge), layoutDirection, this@drawWithCache))
        translate(Offset(edge / 2, edge / 2))
    }
    val stroke = Stroke(edge)
    onDrawBehind {
        translate(-padX, -padY) {
            if (look.style == NavStyle.UNDERLINE) {
                drawRect(look.fill, Offset(0f, box.height - bar), Size(box.width, bar))
            } else {
                if (look.fill.alpha > 0f) drawPath(plate, look.fill)
                drawPath(ring, look.edge, style = stroke)
            }
        }
    }
}

/**
 * The GRAPHITE dock around the M3 bar's own surface: the bar's [background] and [insets] outside
 * the Graphite home's menu card (grCard, radius 40, padding 12), and the fading menu dividers
 * between the keys that [dock] knows, none beside a lit key. grCard reads LocalGrLook: the bar
 * provides the live look around this call.
 */
@Composable
private fun Modifier.graphiteSkinDock(background: Color, insets: WindowInsets, dock: NavDock): Modifier {
    val p = GraphiteLookState.scheme.palette
    val dividers = remember(p, dock) { Modifier.dockDividers(p, dock) }
    return this
        .background(background)
        .windowInsetsPadding(insets)
        .padding(horizontal = DockSidePadding, vertical = DockEndPadding)
        .grCard(p, MENU_CARD_RADIUS)
        .padding(px(MENU_CARD_PADDING))
        .then(dividers)
}

/** The dividers in the bar's row (the keys' coordinates), rebuilt when a key moves, lights or leaves. */
private fun Modifier.dockDividers(p: GrPalette, dock: NavDock): Modifier = drawWithCache {
    val u = px(1).toPx()
    val brush = menuDividerBrush(p, size.height, u)
    val xs = navDividerXs(dock.spans()).toFloatArray()
    onDrawBehind { for (x in xs) drawMenuDivider(brush, x, u) }
}

/**
 * A key of the «Graphite» skin's bottom bar: the Graphite home's menu key (MenuKeyFace) filling the
 * M3 item, lit as [look] says, with the caller's [icon] and [label] in one row in [color] and the
 * M3 label style. With a label the icon is hidden from accessibility, as M3 does, so the key reads
 * its label once. The item provides the live LocalGr and LocalGrLook around it.
 */
@Composable
private fun GraphiteNavKey(look: NavLook, color: Color, icon: @Composable () -> Unit, label: (@Composable () -> Unit)?) {
    MenuKeyFace(lit = look.lit, modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides MaterialTheme.typography.labelMedium,
        ) {
            Box(
                Modifier.size(NavKeyIconSize).then(if (label != null) Modifier.clearAndSetSemantics {} else Modifier),
                propagateMinConstraints = true,
            ) { icon() }
            if (label != null) label()
        }
    }
}
