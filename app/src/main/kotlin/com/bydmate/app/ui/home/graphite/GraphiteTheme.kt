package com.bydmate.app.ui.home.graphite

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.bydmate.app.R
import com.bydmate.app.ui.home.hybrid.fs
import com.bydmate.app.ui.home.hybrid.px

/*
 * The Graphite home's visual language: palette, Inter, the card and its parts. Every size is in
 * mockup pixels (tools/graphite/design/SPEC.md), converted by px()/fs() inside MockupFrame.
 */

/** The home palette of the active skin; GraphiteHome provides it. */
val LocalGr = staticCompositionLocalOf { GrDark }

private val InterWeights = intArrayOf(250, 300, 400, 450, 500, 600)

/** Inter Variable at the weights the mockup uses; only for ui/home/graphite. */
@OptIn(ExperimentalTextApi::class)
val GrInter: FontFamily = FontFamily(
    InterWeights.map { w ->
        Font(R.font.gr_inter, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/**
 * Inter text: [size] and [tracking] in mockup px. The mockup sets "cv11" and "ss01" on the whole
 * page; [tnum] adds tabular figures.
 */
fun grStyle(size: Float, weight: Int, tracking: Float = 0f, color: Color, tnum: Boolean = true): TextStyle = TextStyle(
    color = color,
    fontFamily = GrInter,
    fontWeight = FontWeight(weight),
    fontSize = fs(size),
    letterSpacing = fs(tracking),
    lineHeight = fs(size * 1.2f),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    fontFeatureSettings = if (tnum) "tnum, cv11, ss01" else "cv11, ss01",
)

/* ---------------------------------------------------------------- blur helpers */

/** A native paint whose shape is blurred like a CSS shadow / glow with blur [cssBlur] (device px). */
internal fun blurPaint(color: Color, cssBlur: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        // CSS blur B is a Gaussian with sigma B/2; BlurMaskFilter radius r has sigma 0.57735·r + 0.5.
        val radius = ((cssBlur / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }

private fun DrawScope.drawNativeRoundRect(r: Rect, radius: Float, paint: android.graphics.Paint) {
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, paint) }
}

private fun DrawScope.drawNativePath(path: android.graphics.Path, paint: android.graphics.Paint) {
    drawIntoCanvas { it.nativeCanvas.drawPath(path, paint) }
}

private fun roundRectPath(rect: Rect, radius: Float): Path =
    Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f)))) }

/**
 * CSS `inset 0 dy blur color`: the outside of the rounded box (offset by [dy]) casts a blurred
 * shadow into it. The caller clips to the box.
 */
private fun insetShadowPath(size: Size, radius: Float, dy: Float, spread: Float): android.graphics.Path {
    val out = Rect(Offset.Zero, size).inflate(spread)
    val hole = roundRectPath(Rect(Offset(0f, dy), size), radius)
    return Path().apply {
        addRect(out)
        addPath(hole)
        fillType = PathFillType.EvenOdd
    }.asAndroidPath()
}

/** CSS `inset 0 1px 0 color`: the 1 px band along the top that the box shifted down uncovers. */
private fun topHighlightPath(size: Size, radius: Float, u: Float): Path {
    val box = roundRectPath(Rect(Offset.Zero, size), radius)
    val shifted = roundRectPath(Rect(Offset(0f, u), size), radius)
    return Path().apply { op(box, shifted, PathOperation.Difference) }
}

/* ---------------------------------------------------------------- card */

/**
 * The card surface (SPEC §5): CardTop→CardBottom gradient, 1 px CardEdge contour and CardEdgeTop
 * top highlight drawn over the content (so they stay on top of a photo), clipped to [radius].
 * Graphite / Dark cast a `0 24 48 −12` CardOuterGlow shadow; Edge has a 28 px CardOuterGlow glow
 * outside and a 40 px CardInnerGlow inside. All shapes and paints are built once per size.
 */
fun Modifier.grCard(p: GrPalette, radius: Float = 34f): Modifier = this
    .drawWithCache {
        val u = px(1).toPx()
        val r = px(radius).toPx()
        val box = Rect(Offset.Zero, size)
        if (p.edgeStyle) {
            val glow = blurPaint(p.cardOuterGlow, 28 * u)
            onDrawBehind { drawNativeRoundRect(box, r, glow) }
        } else {
            val spread = 12 * u
            val shadow = blurPaint(p.cardOuterGlow, 48 * u)
            val rect = box.deflate(spread).translate(0f, 24 * u)
            val rr = (r - spread).coerceAtLeast(0f)
            onDrawBehind { drawNativeRoundRect(rect, rr, shadow) }
        }
    }
    .clip(RoundedCornerShape(px(radius)))
    .drawWithCache {
        val u = px(1).toPx()
        val r = px(radius).toPx()
        val fill = Brush.verticalGradient(listOf(p.cardTop, p.cardBottom))
        val highlight = topHighlightPath(size, r, u)
        val inner = if (p.edgeStyle && p.cardInnerGlow.alpha > 0f) {
            insetShadowPath(size, r, 0f, 80 * u) to blurPaint(p.cardInnerGlow, 40 * u)
        } else null
        onDrawWithContent {
            drawRect(fill)
            drawContent()
            inner?.let { (path, paint) -> drawNativePath(path, paint) }
            drawRoundRect(
                p.cardEdge, topLeft = Offset(u / 2, u / 2), size = Size(size.width - u, size.height - u),
                cornerRadius = CornerRadius(r - u / 2), style = Stroke(u),
            )
            drawPath(highlight, p.cardEdgeTop)
        }
    }

/**
 * A rounded fill with the variant's rim: a 1 px [edge] contour on Edge, a 1 px [edge] top
 * highlight elsewhere. Not clipped, so a glow inside may spill. Used by chips and the pressed menu key.
 */
internal fun Modifier.grPill(fill: Color, edge: Color, edgeStyle: Boolean, radius: Float): Modifier = this
    .drawWithCache {
        val u = px(1).toPx()
        val r = px(radius).toPx()
        val highlight = if (edgeStyle) null else topHighlightPath(size, r, u)
        onDrawBehind {
            drawRoundRect(fill, cornerRadius = CornerRadius(r))
            if (highlight != null) {
                drawPath(highlight, edge)
            } else {
                drawRoundRect(
                    edge, topLeft = Offset(u / 2, u / 2), size = Size(size.width - u, size.height - u),
                    cornerRadius = CornerRadius(r - u / 2), style = Stroke(u),
                )
            }
        }
    }

/** Card with the common padding (30 top/bottom, 34 sides): every header sits at the same height. */
@Composable
fun CardBody(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.grCard(LocalGr.current).padding(horizontal = px(34), vertical = px(30)),
        content = content,
    )
}

/** Card header: icon 26 TextTertiary, gap 12, title 23/450/0.1 TextSecondary; [trailing] on the right. */
@Composable
fun CardHeader(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalGr.current
    Row(modifier.fillMaxWidth().height(px(34)), verticalAlignment = Alignment.CenterVertically) {
        GrIcon(icon, 26f, p.textTertiary)
        Spacer(Modifier.width(px(12)))
        Text(
            title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = grStyle(23f, 450, 0.1f, titleColor ?: p.textSecondary, tnum = false),
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A gr_ic_* vector, [size] mockup px, coloured by [tint]. */
@Composable
fun GrIcon(id: Int, size: Float, tint: Color) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(px(size)))
}

/**
 * Top-bar chip (SPEC §5): height 56, radius 28, padding 18 left / 24 right, gap 12 after [lead];
 * [text] 22/500 TextPrimary, then [note] 20/400 TextTertiary after a space.
 */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, note: String? = null, lead: (@Composable () -> Unit)? = null) {
    val p = LocalGr.current
    Row(
        modifier.height(px(56)).grPill(p.chip, p.chipEdge, p.edgeStyle, 28f).padding(start = px(18), end = px(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) {
            lead()
            Spacer(Modifier.width(px(12)))
        }
        Text(chipText(text, note, p), maxLines = 1, softWrap = false, style = grStyle(22f, 500, color = p.textPrimary))
    }
}

private fun chipText(text: String, note: String?, p: GrPalette): AnnotatedString = buildAnnotatedString {
    append(text)
    if (note != null) {
        withStyle(SpanStyle(color = p.textTertiary, fontWeight = FontWeight(400), fontSize = fs(20f))) { append(" $note") }
    }
}

/**
 * A sunken block (volume, tabs, footer): Inset fill; on Graphite / Dark an `inset 0 1px 3px`
 * InsetEdge shadow, on Edge a 1 px InsetEdge contour.
 */
@Composable
fun InsetBox(radius: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalGr.current
    Box(modifier.clip(RoundedCornerShape(px(radius))).background(p.inset).insetRim(p, radius), content = content)
}

private fun Modifier.insetRim(p: GrPalette, radius: Float): Modifier = drawWithCache {
    val u = px(1).toPx()
    val r = px(radius).toPx()
    if (p.edgeStyle) {
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                p.insetEdge, topLeft = Offset(u / 2, u / 2), size = Size(size.width - u, size.height - u),
                cornerRadius = CornerRadius(r - u / 2), style = Stroke(u),
            )
        }
    } else {
        val path = insetShadowPath(size, r, u, 8 * u)
        val paint = blurPaint(p.insetEdge, 3 * u)
        onDrawBehind { drawNativePath(path, paint) }
    }
}
