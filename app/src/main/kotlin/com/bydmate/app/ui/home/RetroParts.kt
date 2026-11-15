@file:Suppress("TooManyFunctions") // Canvas drawing of the deck door and hardware parts: many small painters by design

package com.bydmate.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.Font
import com.bydmate.app.R
import kotlin.math.roundToInt
import kotlin.math.min

/*
 * A photographed cassette (see PhotoCassettes) behind a tinted deck door, shared by the retro
 * stacks.
 */

/**
 * The deck's cassette compartment: a smoked acrylic door in [frame], the photographed
 * [cassette] centred in it at its true proportions, [label] handwritten on its label. The hubs
 * turn by [hubAngle] degrees, read at draw time so spinning only redraws the hub layer;
 * [caption] is engraved under the tape when the door has room for it.
 */
@Composable
fun CassetteDoor(
    label: String,
    hubAngle: () -> Float,
    modifier: Modifier = Modifier,
    cassette: PhotoCassette = CassetteRotation.current,
    frame: Color = Color(0xFF45474C),
    caption: String? = null,
) {
    val tm = rememberTextMeasurer(cacheSize = 8)
    val face = ImageBitmap.imageResource(cassette.face)
    val hubL = ImageBitmap.imageResource(cassette.hubLImage)
    val hubR = ImageBitmap.imageResource(cassette.hubRImage)
    Box(modifier.semantics { contentDescription = "Кассета: $label" }) {
        Canvas(Modifier.fillMaxSize()) {
            drawDoorBack()
            val (k, o) = cassetteFit()
            drawImage(
                face, dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                dstSize = IntSize((CASSETTE_W * k).roundToInt(), (CASSETTE_H * k).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            drawHandwriting(tm, label, cassette, k, o)
            if (caption != null) drawCaption(tm, caption)
        }
        Canvas(Modifier.fillMaxSize()) {
            val (k, o) = cassetteFit()
            // played side A: the tape runs left to right along the bottom, both reels turn anticlockwise
            val a = -hubAngle()
            drawHubPatch(hubL, o + cassette.hubL * k, k, a)
            drawHubPatch(hubR, o + cassette.hubR * k, k, a)
        }
        Canvas(Modifier.fillMaxSize()) { drawDoorGlass(frame) }
    }
}

/** Where the cassette sits in the door: centred, a margin all round, true aspect ratio. */
private fun DrawScope.cassetteFit(): Pair<Float, Offset> {
    val margin = min(size.width, size.height) * 0.06f
    val k = min((size.width - 2 * margin) / CASSETTE_W, (size.height - 2 * margin) / CASSETTE_H)
    val top = if (size.height - CASSETTE_H * k > 2 * margin + 20.dp.toPx()) margin * 1.2f else (size.height - CASSETTE_H * k) / 2
    return k to Offset((size.width - CASSETTE_W * k) / 2, top)
}

private fun DrawScope.drawDoorBack() {
    val r = CornerRadius(10.dp.toPx())
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF111214), Color(0xFF070708))), cornerRadius = r)
}

/** A hub patch scaled by [k], its centre on [c], turned by [angle] degrees. */
private fun DrawScope.drawHubPatch(patch: ImageBitmap, c: Offset, k: Float, angle: Float) {
    val w = (patch.width * k).roundToInt()
    val h = (patch.height * k).roundToInt()
    rotate(angle, c) {
        drawImage(
            patch, dstOffset = IntOffset((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt()),
            dstSize = IntSize(w, h), filterQuality = FilterQuality.Medium,
        )
    }
}

/**
 * [text] written by hand into the cassette's label box: Caveat in the cassette's ink, as large
 * as the box allows (one line, shrunk to fit, ellipsized at the floor size), its baseline
 * tilted a little.
 */
private fun DrawScope.drawHandwriting(tm: TextMeasurer, text: String, cassette: PhotoCassette, k: Float, o: Offset) {
    val box = cassette.label
    val maxW = box.width * k
    fun layout(units: Float) = tm.measure(
        text,
        TextStyle(color = cassette.ink, fontSize = (units * k).toSp(), fontFamily = Handwriting),
        overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
        constraints = Constraints(maxWidth = maxW.roundToInt()),
    )
    val full = box.height * 0.95f
    var units = full
    var l = layout(units)
    while (l.hasVisualOverflow && units > full * 0.55f) {
        units *= 0.92f
        l = layout(units)
    }
    val left = o.x + box.left * k + (maxW - l.size.width) / 2
    val top = o.y + box.top * k + (box.height * k - l.size.height) / 2
    rotate(-1.2f, Offset(left, top + l.size.height / 2f)) {
        drawText(l, topLeft = Offset(left, top))
    }
}

/** Caveat (SIL OFL, assets/licenses/caveat-OFL.txt): a handwriting face with Cyrillic. */
private val Handwriting = FontFamily(Font(R.font.caveat, FontWeight.Medium))

private fun DrawScope.drawCaption(tm: TextMeasurer, caption: String) {
    val (k, o) = cassetteFit()
    val layout = tm.measure(
        caption,
        TextStyle(color = Color(0xFF7E8187), fontSize = 10.dp.toSp(), letterSpacing = 1.dp.toSp(), fontFamily = FontFamily.SansSerif),
    )
    val y = o.y + CASSETTE_H * k + (size.height - o.y - CASSETTE_H * k - layout.size.height) / 2
    drawText(layout, topLeft = Offset((size.width - layout.size.width) / 2, y))
}

/** The acrylic: a smoky tint over everything, one soft diagonal reflection, the door frame. */
private fun DrawScope.drawDoorGlass(frame: Color) {
    val r = CornerRadius(10.dp.toPx())
    drawRoundRect(Color(0xFF0A0C10).copy(alpha = 0.30f), cornerRadius = r)
    val glare = Path().apply {
        moveTo(size.width * 0.06f, 0f); lineTo(size.width * 0.42f, 0f)
        lineTo(size.width * 0.20f, size.height); lineTo(0f, size.height); lineTo(0f, size.height * 0.55f); close()
    }
    clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }) {
        drawPath(
            glare,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.10f), 0.5f to Color.White.copy(alpha = 0.03f), 1f to Color.Transparent,
                start = Offset.Zero, end = Offset(size.width * 0.4f, size.height),
            ),
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), 0f, size.height * 0.25f),
        )
    }
    val px = 1.dp.toPx()
    drawRoundRect(frame, Offset(px, px), Size(size.width - 2 * px, size.height - 2 * px), r, Stroke(2 * px))
    drawRoundRect(Color.Black.copy(alpha = 0.7f), Offset(3 * px, 3 * px), Size(size.width - 6 * px, size.height - 6 * px), CornerRadius(8.dp.toPx()), Stroke(2 * px))
}

/* ---------------------------------------------------------------- engraved key legends */

/**
 * [icon] cut into a key: a dark copy up-left, a light copy down-right, the legend itself in
 * [tone] between them, so it reads as debossed rather than printed.
 */
@Composable
fun EngravedIcon(icon: ImageVector, tone: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val painter = rememberVectorPainter(icon)
    Canvas(modifier.size(size)) {
        val o = 1.dp.toPx()
        with(painter) {
            translate(-o, -o) { draw(this@Canvas.size, colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.85f))) }
            translate(o, o) { draw(this@Canvas.size, colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.22f))) }
            draw(this@Canvas.size, colorFilter = ColorFilter.tint(tone))
        }
    }
}

/** A cassette outline in the Material icon grid, for the TAPE key. */
val CassetteGlyph: ImageVector by lazy {
    ImageVector.Builder("Cassette", 24.dp, 24.dp, 24f, 24f).apply {
        val ink = SolidColor(Color.Black)
        path(stroke = ink, strokeLineWidth = 1.8f, strokeLineJoin = StrokeJoin.Round, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 5f); lineTo(20f, 5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 21.5f, 6.5f)
            lineTo(21.5f, 17.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20f, 19f)
            lineTo(4f, 19f)
            arcTo(1.5f, 1.5f, 0f, false, true, 2.5f, 17.5f)
            lineTo(2.5f, 6.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 4f, 5f)
            close()
            moveTo(7f, 19f); lineTo(8.5f, 15.5f); lineTo(15.5f, 15.5f); lineTo(17f, 19f)
        }
        path(stroke = ink, strokeLineWidth = 1.6f) {
            moveTo(10.2f, 11f); arcTo(2f, 2f, 0f, true, true, 6.2f, 11f); arcTo(2f, 2f, 0f, true, true, 10.2f, 11f); close()
            moveTo(17.8f, 11f); arcTo(2f, 2f, 0f, true, true, 13.8f, 11f); arcTo(2f, 2f, 0f, true, true, 17.8f, 11f); close()
        }
    }.build()
}

/* ---------------------------------------------------------------- fascia and key materials */

/**
 * Horizontally brushed metal fascia from [top] to [bottom]: irregular fine hairlines, a soft
 * sheen band in the upper third, light falling off to the right, a lit top edge in [edge], a
 * dark lip in [lip] and dark seams at both ends. Built once per size (drawWithCache).
 */
fun Modifier.brushedFascia(top: Color, bottom: Color, edge: Color, lip: Color): Modifier = drawWithCache {
    val px = 1.dp.toPx()
    val dark = Path()
    val light = Path()
    var seed = 0x2F6E2B1
    var y = 0f
    while (y < size.height) {
        seed = seed * 1_103_515_245 + 12_345
        val r = (seed ushr 16) and 0x7FFF
        val h = px * (0.5f + (r % 3) * 0.25f)
        (if (r % 3 == 0) light else dark).addRect(Rect(0f, y, size.width, y + h))
        y += px * (1f + (r % 5) * 0.35f)
    }
    val base = Brush.verticalGradient(listOf(top, bottom))
    val sheen = Brush.verticalGradient(
        0f to Color.Transparent, 0.2f to Color.White.copy(alpha = 0.045f), 0.45f to Color.Transparent, 1f to Color.Transparent,
    )
    val falloff = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.025f), Color.Transparent, Color.Black.copy(alpha = 0.07f)))
    onDrawBehind {
        drawRect(base)
        drawPath(dark, Color.Black.copy(alpha = 0.15f))
        drawPath(light, Color.White.copy(alpha = 0.035f))
        drawRect(sheen)
        drawRect(falloff)
        drawRect(edge, size = Size(size.width, px))
        drawRect(Color.White.copy(alpha = 0.05f), Offset(0f, px), Size(size.width, px))
        drawRect(lip, Offset(0f, size.height - 2 * px), Size(size.width, 2 * px))
        drawRect(Color.Black.copy(alpha = 0.45f), size = Size(px, size.height))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(size.width - px, 0f), Size(px, size.height))
    }
}

/**
 * Face of a hardware push key: [face] with a faint vertical gradient, a lit top edge and a
 * [shadow] lip below when raised; flush, darker and shaded from the top edge when [down].
 */
fun Modifier.hardwareKey(down: Boolean, face: Color, edge: Color, shadow: Color, radius: Dp): Modifier = drawWithCache {
    val px = 1.dp.toPx()
    val r = CornerRadius(radius.toPx())
    val raised = Brush.verticalGradient(listOf(lerp(face, Color.White, 0.07f), face, lerp(face, Color.Black, 0.18f)))
    val pushed = Brush.verticalGradient(listOf(lerp(face, Color.Black, 0.35f), lerp(face, Color.Black, 0.1f)))
    val inner = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), 0f, 6 * px)
    onDrawBehind {
        if (!down) drawRoundRect(shadow, Offset(0f, 2 * px), size, r)
        drawRoundRect(if (down) pushed else raised, cornerRadius = r)
        if (down) {
            drawRoundRect(inner, cornerRadius = r)
        } else {
            drawLine(Color.White.copy(alpha = 0.16f), Offset(radius.toPx(), 1.5f * px), Offset(size.width - radius.toPx(), 1.5f * px), px)
        }
        drawRoundRect(edge, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), r, style = Stroke(px))
    }
}

/** Spun-aluminium cap: a conical sheen ([dark] / [light], two highlights round) drawn at [radius] around [c]. */
fun DrawScope.spunCap(c: Offset, radius: Float, dark: Color = Color(0xFF1F2023), light: Color = Color(0xFF45474C)) {
    drawCircle(Brush.sweepGradient(listOf(dark, light, dark, lerp(dark, light, 0.6f), dark), c), radius, c)
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), c, radius), radius, c)
    drawCircle(Color.Black.copy(alpha = 0.6f), radius, c, style = Stroke(1.dp.toPx()))
}
