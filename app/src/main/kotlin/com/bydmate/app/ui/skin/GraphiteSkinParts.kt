package com.bydmate.app.ui.skin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.common.px
import com.bydmate.app.ui.home.graphite.GrLook
import com.bydmate.app.ui.home.graphite.GrPalette
import com.bydmate.app.ui.home.graphite.GrRelief
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.home.graphite.LocalGr
import com.bydmate.app.ui.home.graphite.LocalGrLook
import com.bydmate.app.ui.home.graphite.blurPaint
import com.bydmate.app.ui.home.graphite.drawNativePath
import com.bydmate.app.ui.home.graphite.grBackground
import com.bydmate.app.ui.home.graphite.grCard
import com.bydmate.app.ui.home.graphite.grPill
import com.bydmate.app.ui.home.graphite.insetShadowPath
import com.bydmate.app.ui.home.graphite.smallRelief
import com.bydmate.app.ui.home.graphite.topHighlightPath

// fork: what the «Graphite» skin draws with the Graphite home's own code and its live look
// (GraphiteLookState): the card, the pill under buttons and chips, the background and the switch;
// the bottom bar is in SkinNavigationBar.kt. Each part is remembered per palette and look, so a recomposition keeps
// its draw cache: shaders, paths and paints are built once per size, the material tiles once
// per process (GraphiteCard.kt).

/**
 * [content] with LocalGrLook from GraphiteLookState.look when [on], so the grCard and grPill
 * inside draw the owner's material, relief and 2.5D edges. Off, it re-provides the look already
 * in effect. The provider is there either way: a branch would be two groups, and a skin switch
 * would drop the content's remembered state (scroll, list position, an open dialog).
 */
@Composable
internal fun ProvideGraphiteLook(on: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGrLook provides graphiteLookFor(on), content = content)
}

/** The LocalGrLook value for a skinned part: the live look when [on], else the one in effect. */
@Composable
internal fun graphiteLookFor(on: Boolean): GrLook = if (on) GraphiteLookState.look else LocalGrLook.current

/** The LocalGr value for a skinned part: the live scheme's palette when [on], else the one in effect. */
@Composable
internal fun graphitePaletteFor(on: Boolean): GrPalette = if (on) GraphiteLookState.scheme.palette else LocalGr.current

/**
 * The edge of a card inside a Graphite card (CardBorderKind.GRAPHITE_EDGE): the 1 px edge of the
 * Graphite home's raised key, tabActiveEdge of the live scheme, over the caller's own container.
 */
/** The nested blocks' edge: the key edge at twice its alpha (capped at 1), so it reads inside a card. */
internal fun graphiteNestedEdge(edge: Color): Color = edge.copy(alpha = (edge.alpha * 2f).coerceAtMost(1f))

@Composable
internal fun graphiteEdgeBorder(): BorderStroke {
    val edge = GraphiteLookState.scheme.palette.tabActiveEdge
    return remember(edge) { BorderStroke(1.dp, graphiteNestedEdge(edge)) }
}

/**
 * The Graphite home's card under a skinned Card or dialog of [shape]: grCard in the live scheme,
 * its corner taken from [shape] (grRadiusOf). Call it inside [ProvideGraphiteLook].
 */
@Composable
internal fun Modifier.graphiteSkinCard(shape: Shape): Modifier =
    grCard(GraphiteLookState.scheme.palette, grRadiusOf(shape, LocalDensity.current))

/**
 * The Graphite home's pill (grPill) under a skinned button or chip of [shape]: [fill] with the
 * [edge] rim of the live scheme (a top highlight, a contour on Edge) and its reduced relief. A dp
 * corner keeps its size, a percent corner (the M3 button's default) is a pill. Call it inside
 * [ProvideGraphiteLook], on the control's own bounds (skinTouchTarget before it).
 */
@Composable
internal fun Modifier.graphiteSkinPill(fill: Color, edge: Color, shape: Shape): Modifier =
    grPill(fill, edge, GraphiteLookState.scheme.palette.edgeStyle, grRadiusOf(shape, LocalDensity.current, GR_PILL_RADIUS))

/** The Graphite home's background in the live scheme: Bg with its BgGlow radial. */
@Composable
internal fun Modifier.graphiteSkinBackground(): Modifier = grBackground(GraphiteLookState.scheme.palette)

/**
 * The GRAPHITE switch: [look] gives the sizes and colours (a sunken groove lit by the accent
 * when on, a raised round thumb), the live scheme adds the Graphite rims: the groove has the
 * inset shadow of a sunken block (Edge: its 1 px contour), the thumb the top highlight of a pill
 * (Edge: a contour), both the relief in its reduced form, as InsetBox and grPill do on the home.
 */
@Composable
internal fun GraphiteSwitchFace(look: SwitchLook, checked: Boolean, pressed: State<Boolean>?, modifier: Modifier) {
    val g = GraphiteLookState.scheme.palette
    val relief = GraphiteLookState.relief
    val fraction = animateFloatAsState(if (checked) 1f else 0f, tween(THUMB_MOVE_MS), label = "graphiteSwitchThumb")
    val face = remember(look, g, relief, fraction, pressed) { graphiteSwitch(look, g, relief, fraction, pressed) }
    Spacer(modifier.then(if (look.alpha < 1f) Modifier.alpha(look.alpha) else Modifier).then(face))
}

private fun graphiteSwitch(
    look: SwitchLook,
    g: GrPalette,
    relief: GrRelief,
    fraction: State<Float>,
    pressed: State<Boolean>?,
): Modifier = Modifier.drawWithCache {
    val u = px(1).toPx()
    val rim = Stroke(u)
    val trackH = look.trackHeight.toPx()
    val trackTop = (size.height - trackH) / 2f
    val groove = Size(size.width, trackH)
    val grooveR = CornerRadius(look.trackCorner.toPx().coerceAtMost(trackH / 2f))
    val grooveClip = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, groove), grooveR)) }
    // InsetBox's `inset 0 1px 3px InsetEdge`; Edge draws a contour instead
    val grooveShadow = if (g.edgeStyle) null else insetShadowPath(groove, grooveR.x, u, 8 * u) to blurPaint(g.insetEdge, 3 * u)
    val grooveShade = smallRelief(relief) // one brush per size: a shared one would rebuild its shader on every draw
    val thumb = look.thumbSize.toPx()
    val thumbBox = Size(thumb, thumb)
    val thumbTop = (size.height - thumb) / 2f
    val thumbR = CornerRadius(look.thumbCorner.toPx().coerceAtMost(thumb / 2f))
    val thumbHighlight = if (g.edgeStyle) null else topHighlightPath(thumbBox, thumbR.x, u)
    val thumbShade = smallRelief(relief)
    val inset = look.thumbInset.toPx()
    val halo = PressHaloRadius.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    onDrawBehind {
        translate(top = trackTop) {
            drawRoundRect(look.stateColor, size = groove, cornerRadius = grooveR)
            grooveShade?.let { drawRoundRect(it, size = groove, cornerRadius = grooveR) }
            if (grooveShadow != null) {
                clipPath(grooveClip) { drawNativePath(grooveShadow.first, grooveShadow.second) }
            } else {
                drawRoundRect(
                    g.insetEdge, topLeft = Offset(u / 2, u / 2), size = Size(groove.width - u, groove.height - u),
                    cornerRadius = CornerRadius((grooveR.x - u / 2).coerceAtLeast(0f)), style = rim,
                )
            }
        }
        val x = switchThumbX(size.width, thumb, inset, fraction.value, rtl)
        if (pressed?.value == true) {
            drawCircle(look.thumbColor.copy(alpha = PRESS_HALO_ALPHA), halo, Offset(x + thumb / 2f, size.height / 2f))
        }
        translate(left = x, top = thumbTop) {
            drawRoundRect(look.thumbFill, size = thumbBox, cornerRadius = thumbR)
            thumbShade?.let { drawRoundRect(it, size = thumbBox, cornerRadius = thumbR) }
            if (thumbHighlight != null) {
                drawPath(thumbHighlight, g.tabActiveEdge)
            } else {
                drawRoundRect(
                    g.tabActiveEdge, topLeft = Offset(u / 2, u / 2), size = Size(thumb - u, thumb - u),
                    cornerRadius = CornerRadius((thumbR.x - u / 2).coerceAtLeast(0f)), style = rim,
                )
            }
        }
    }
}
