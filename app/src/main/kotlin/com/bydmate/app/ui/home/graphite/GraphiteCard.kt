package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bydmate.app.ui.home.common.px

/* ---------------------------------------------------------------- card */

/*
 * The draw helpers below are @Composable and remember their drawWithCache modifier per key: a
 * fresh lambda on every call would make an unequal modifier, so each recomposition (the live
 * cards recompose about once a second) would drop the cache and rebuild paints, paths and brushes.
 */

/**
 * The card surface (SPEC §5): CardTop→CardBottom gradient, 1 px CardEdge contour and CardEdgeTop
 * top highlight drawn over the content (so they stay on top of a photo), clipped to [radius].
 * Graphite / Dark cast a `0 24 48 −12` CardOuterGlow shadow; Edge has a 28 px CardOuterGlow glow
 * outside and a 40 px CardInnerGlow inside. All shapes and paints are built once per size.
 *
 * The look from the settings ([LocalGrLook]) adds a material texture over the fill and under the
 * content (glass: a glare over it), the relief's light and shade, and the 2.5D bevel. A [photo]
 * card (the gallery) is covered by its picture: the relief lies over it and only glass shows.
 */
@Composable
fun Modifier.grCard(p: GrPalette, radius: Float = 34f, photo: Boolean = false): Modifier {
    val look = LocalGrLook.current
    return this then remember(p, radius, look, photo) { cardModifier(p, radius, look, photo) }
}

private fun cardModifier(p: GrPalette, radius: Float, look: GrLook, photo: Boolean): Modifier =
    cardShadow(p, radius).clip(RoundedCornerShape(px(radius))).then(cardSurface(p, radius, look, photo))

/** Edge's CardOuterGlow glow around the card; elsewhere its `0 24 48 −12` shadow. */
private fun cardShadow(p: GrPalette, radius: Float): Modifier = Modifier.drawWithCache {
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

/** The card's fill, texture and relief under the content; glare, inner glow, contour and rims over it. */
private fun cardSurface(p: GrPalette, radius: Float, look: GrLook, photo: Boolean): Modifier = Modifier.drawWithCache {
    val layers = CardLayers(p, look, photo, size, px(radius).toPx(), px(1).toPx())
    onDrawWithContent {
        layers.drawUnder(this)
        drawContent()
        layers.drawOver(this)
    }
}

/** What [cardSurface] draws for one [size], built once: [r] the radius and [u] one mockup px, in canvas px. */
private class CardLayers(
    private val p: GrPalette,
    look: GrLook,
    private val photo: Boolean,
    size: Size,
    private val r: Float,
    private val u: Float,
) {
    private val fill = Brush.verticalGradient(reliefFill(p, look.relief))
    private val texture = if (photo) null else materialBrush(look.material)
    private val sheen = if (!photo && look.material == GrMaterial.METAL) METAL_SHEEN else null
    private val relief = reliefShade(look.relief)
    private val glare = if (look.material == GrMaterial.GLASS) glassGlare(size) else null
    private val rim = if (look.material == GrMaterial.GLASS) glassRim(size) else null
    private val topRim = if (look.relief == GrRelief.CONCAVE) null else topHighlightPath(size, r, u)
    private val bottomRim = if (look.relief == GrRelief.CONCAVE) bottomHighlightPath(size, r, u) else null
    private val bevel = if (look.edges25d) bevelBrushes(size) else null
    private val inner = if (p.edgeStyle && p.cardInnerGlow.alpha > 0f) {
        insetShadowPath(size, r, 0f, 80 * u) to blurPaint(p.cardInnerGlow, 40 * u)
    } else null

    /** Under the content: fill, texture, sheen, and the relief unless a photo covers the card. */
    fun drawUnder(scope: DrawScope) = with(scope) {
        drawRect(fill)
        texture?.let { drawRect(it) }
        sheen?.let { drawRect(it) }
        if (!photo) relief?.let { drawRect(it) }
    }

    /** Over the content: a photo's relief, the glass glare, the inner glow, the contour and the rims. */
    fun drawOver(scope: DrawScope) = with(scope) {
        if (photo) relief?.let { drawRect(it) }
        glare?.let { drawRect(it) }
        inner?.let { (path, paint) -> drawNativePath(path, paint) }
        drawRoundRect(
            p.cardEdge, topLeft = Offset(u / 2, u / 2), size = Size(size.width - u, size.height - u),
            cornerRadius = CornerRadius(r - u / 2), style = Stroke(u),
        )
        topRim?.let { drawPath(it, p.cardEdgeTop) }
        bottomRim?.let { drawPath(it, p.cardEdgeTop.copy(alpha = p.cardEdgeTop.alpha * 0.6f)) }
        rim?.let { drawRim(it, 1.5f * u, r) }
        bevel?.let { (band, line) ->
            // Three nested rims of the same faint band: strongest at the very edge, fading
            // inwards over ~10 px, the way a curved glass edge catches the light.
            drawRim(band, 3 * u, r)
            drawRim(band, 6 * u, r)
            drawRim(band, 10 * u, r)
            drawRim(line, u, r)
        }
    }

    /** A rounded stroke of [width] whose outer edge lies on the card's edge. */
    private fun DrawScope.drawRim(brush: Brush, width: Float, radius: Float) {
        val h = width / 2
        drawRoundRect(
            brush, topLeft = Offset(h, h), size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius((radius - h).coerceAtLeast(0f)), style = Stroke(width),
        )
    }
}

/** The card fill: convex lifts the top a little and sinks the bottom. */
private fun reliefFill(p: GrPalette, relief: GrRelief): List<Color> = when (relief) {
    GrRelief.CONVEX -> listOf(lerp(p.cardTop, Color.White, 0.035f), lerp(p.cardBottom, Color.Black, 0.18f))
    else -> listOf(p.cardTop, p.cardBottom)
}

/** Light and shade of the relief across the card height; null when flat. */
private fun reliefShade(relief: GrRelief): Brush? = when (relief) {
    GrRelief.FLAT -> null
    GrRelief.CONVEX -> Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.045f), 0.45f to Color.Transparent,
        0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f),
    )
    GrRelief.CONCAVE -> Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.22f), 0.25f to Color.Transparent,
        0.7f to Color.Transparent, 1f to Color.White.copy(alpha = 0.03f),
    )
}

/** Brushed metal's broad soft sheen across the upper third. */
private val METAL_SHEEN = Brush.verticalGradient(
    0f to Color.Transparent, 0.16f to Color.White.copy(alpha = 0.04f), 0.34f to Color.Transparent,
)

/** The texture tile of [material], repeated; null for none and glass. */
private fun materialBrush(material: GrMaterial): Brush? {
    val image = when (material) {
        GrMaterial.NONE, GrMaterial.GLASS -> return null
        GrMaterial.METAL -> TextureImages.metal
        GrMaterial.SAND_FINE -> TextureImages.sandFine
        GrMaterial.CONCRETE -> TextureImages.concrete
    }
    return ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))
}

/** Glass: a soft diagonal glare band at 16–27 % of the diagonal and a faint one near 52 %. */
private fun glassGlare(size: Size): Brush {
    val clear = Color.White.copy(alpha = 0f)
    return Brush.linearGradient(
        0f to clear, 0.155f to clear,
        0.16f to Color.White.copy(alpha = 0.075f), 0.27f to Color.White.copy(alpha = 0.03f),
        0.285f to clear, 0.5f to clear,
        0.51f to Color.White.copy(alpha = 0.03f), 0.54f to Color.White.copy(alpha = 0.012f),
        0.55f to clear, 1f to clear,
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
}

/** Glass rim: white 30 % at the top left fading out towards the bottom right. */
private fun glassRim(size: Size): Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.3f), 1f to Color.White.copy(alpha = 0f),
    start = Offset.Zero, end = Offset(size.width, size.height),
)

/**
 * The 2.5D bevel, lit from above like a phone's curved glass edge: a band along the edge that is
 * light on the top, neutral on the sides and dark on the bottom (drawn three times nested, so it
 * fades inwards), and a 1 px specular line on the top edge only.
 */
private fun bevelBrushes(size: Size): Pair<Brush, Brush> {
    val band = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.05f), 0.10f to Color.Transparent,
        0.88f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.09f),
        startY = 0f, endY = size.height,
    )
    val line = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.26f), 0.06f to Color.Transparent,
        startY = 0f, endY = size.height,
    )
    return band to line
}

/** The material tiles as bitmaps, each built on first use and kept. */
private object TextureImages {
    val sandFine by lazy { GrTextures.sandFine().toImage() }
    val metal by lazy { GrTextures.metal().toImage() }
    val concrete by lazy { GrTextures.concrete().toImage() }

    private fun GrTile.toImage(): ImageBitmap =
        android.graphics.Bitmap.createBitmap(argb, width, height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
