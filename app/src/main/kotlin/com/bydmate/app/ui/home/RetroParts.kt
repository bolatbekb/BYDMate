@file:Suppress("TooManyFunctions", "LongParameterList") // Canvas drawing of one cassette: many small painters; label text mirrors SVG text attributes

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
import kotlin.math.min
import kotlin.math.sqrt

/*
 * A Compact Cassette behind a tinted deck door, shared by the retro stacks. The cassette is
 * drawn in tenths of a millimetre on its real 100.4 x 63.8 mm outline (1004 x 638 units).
 */

private const val CW = 1004f
private const val CH = 638f
private const val HUB_Y = 300f
private val HubXs = floatArrayOf(289.5f, 714.5f) // 42.5 mm apart, like the real spools
private const val HUB_R = 84f
private const val PACK_MIN = 92f
private const val PACK_MAX = 228f
private val Window = RoundRect(196f, 204f, 808f, 396f, CornerRadius(14f))

private val ShellTop = Color(0xFF34363A)
private val ShellBottom = Color(0xFF1C1D20)
private val Paper = Color(0xFFDCD6C8)
private val PaperRule = Color(0xFFA9A396)
private val InkColor = Color(0xFF26355A)
private val Hole = Color(0xFF070708)
private val TapeLight = Color(0xFF4A3426)
private val TapeDark = Color(0xFF1F150F)
private val HubFace = Color(0xFFD4D3CF)

/**
 * The deck's cassette door, as on a real KX / Integra deck: an opaque smoked panel with silver
 * [trim] and printed deck legends ([topText] above the window, [bottomText] below), and one
 * rectangular window through which the tape is seen: the label's title line (side A, [artist]
 * — [title]), the two hubs and the brown packs between them. The packs follow [progress] (0 at
 * the start of side A: left pack full); the hubs turn by [hubAngle] degrees, read at draw time
 * so spinning only redraws the hub layer. [stripe] tints the label's thin band.
 */
@Composable
fun CassetteDoor(
    artist: String,
    title: String,
    progress: Float,
    hubAngle: () -> Float,
    modifier: Modifier = Modifier,
    trim: Color = Color(0xFFA9ACB1),
    stripe: Color = Color(0xFF8A5048),
    topText: String = "STEREO CASSETTE DECK",
    bottomText: String = "DOLBY B·C NR",
) {
    val tm = rememberTextMeasurer(cacheSize = 8)
    Box(modifier.semantics { contentDescription = "Кассета: $artist — $title" }) {
        Canvas(Modifier.fillMaxSize()) {
            val win = doorWindow()
            clipPath(Path().apply { addRoundRect(win) }) {
                drawRect(Color(0xFF060607))
                withTape(win) { k ->
                    drawRect(Brush.verticalGradient(listOf(ShellTop, ShellBottom), 0f, CH), Offset.Zero, Size(CW, CH))
                    drawLabel(tm, k, artist, title, stripe)
                    drawWindow(progress)
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val win = doorWindow()
            val a = hubAngle()
            clipPath(Path().apply { addRoundRect(win) }) {
                withTape(win) {
                    clipPath(Path().apply { addRoundRect(Window) }) { HubXs.forEach { drawHub(it, a) } }
                    drawWindowGlass()
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) { drawDoor(tm, trim, topText, bottomText) }
    }
}

/** Part of the cassette framed by the door window, in cassette units: the title line to the label under the window. */
private const val VIEW_X = 150f
private const val VIEW_Y = 138f
private const val VIEW_W = 704f
private const val VIEW_H = 272f

/** The door's window: 80 % of the door wide, the view's aspect ratio, centred. */
private fun DrawScope.doorWindow(): RoundRect {
    val w = min(size.width * 0.8f, size.height * 0.52f * VIEW_W / VIEW_H)
    val h = w * VIEW_H / VIEW_W
    val l = (size.width - w) / 2
    val t = (size.height - h) / 2 + size.height * 0.02f
    return RoundRect(l, t, l + w, t + h, CornerRadius(4.dp.toPx()))
}

private inline fun DrawScope.withTape(win: RoundRect, block: DrawScope.(Float) -> Unit) {
    val k = win.width / VIEW_W
    withTransform({ translate(win.left - VIEW_X * k, win.top - VIEW_Y * k); scale(k, k, Offset.Zero) }) { block(k) }
}

private fun DrawScope.drawLabel(tm: TextMeasurer, k: Float, artist: String, title: String, stripe: Color) {
    val labelRect = RoundRect(84f, 40f, 920f, 452f, CornerRadius(14f))
    clipPath(
        Path().apply {
            addRoundRect(labelRect)
            addRoundRect(RoundRect(Window.left - 10f, Window.top - 10f, Window.right + 10f, Window.bottom + 10f, CornerRadius(20f)))
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        },
    ) {
        drawRect(Brush.verticalGradient(listOf(Paper, Color(0xFFCFC8B8)), 40f, 452f), Offset(84f, 40f), Size(836f, 412f))
        drawRect(stripe, Offset(84f, 140f), Size(836f, 7f))
        drawRect(stripe.copy(alpha = 0.55f), Offset(84f, 150f), Size(836f, 3f))
    }
    // side letter and the writing line with artist — title, just above the tape window
    labelText(tm, k, "A", 166f, 190f, 36f, Color(0xFF2A2A2A), FontWeight.Bold)
    drawLine(PaperRule, Offset(206f, 196f), Offset(836f, 196f), 1.5f)
    labelText(tm, k, "$artist — $title", 212f, 188f, 32f, InkColor, FontWeight.SemiBold, family = FontFamily.Cursive, maxW = 620f)
}

private fun DrawScope.labelText(
    tm: TextMeasurer, k: Float, text: String, x: Float, baseline: Float, sizeUnits: Float, color: Color,
    weight: FontWeight = FontWeight.Normal, family: FontFamily = FontFamily.SansSerif, maxW: Float? = null,
) {
    // measured in px, drawn inside the cassette's unit transform: undo k for the layout
    val layout = tm.measure(
        text,
        TextStyle(color = color, fontSize = (sizeUnits * k).toSp(), fontWeight = weight, fontFamily = family),
        overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
        constraints = maxW?.let { Constraints(maxWidth = (it * k).toInt()) } ?: Constraints(),
    )
    withTransform({ translate(x, baseline - layout.firstBaseline / k); scale(1f / k, 1f / k, Offset.Zero) }) {
        drawText(layout)
    }
}

/** Window: dark inside, the two tape packs (area conserved, as on a real spool pair), the tape path. */
private fun DrawScope.drawWindow(progress: Float) {
    drawRoundRect(Color(0xFF0E0E0F), Window.topLeft(), Window.size(), CornerRadius(14f))
    val p = progress.coerceIn(0f, 1f)
    val span = PACK_MAX * PACK_MAX - PACK_MIN * PACK_MIN
    clipPath(Path().apply { addRoundRect(Window) }) {
        tapePack(HubXs[0], sqrt(PACK_MIN * PACK_MIN + span * (1f - p)))
        tapePack(HubXs[1], sqrt(PACK_MIN * PACK_MIN + span * p))
        drawLine(TapeLight, Offset(HubXs[0] - 60f, Window.bottom - 2f), Offset(HubXs[1] + 60f, Window.bottom - 2f), 3f)
    }
}

private fun DrawScope.tapePack(cx: Float, r: Float) {
    val c = Offset(cx, HUB_Y)
    drawCircle(
        Brush.radialGradient(0f to TapeDark, 0.55f to TapeLight, 0.9f to Color(0xFF3A2A1F), 1f to TapeDark, center = c, radius = r),
        r, c,
    )
    // faint winding rings
    var rr = PACK_MIN + 14f
    while (rr < r - 4f) {
        drawCircle(Color.Black.copy(alpha = 0.18f), rr, c, style = Stroke(1.2f))
        rr += 17f
    }
    drawCircle(Color(0xFF6B5240).copy(alpha = 0.5f), r - 1f, c, style = Stroke(2f))
}

/** Spline hub: a light-grey ring with six inward teeth around the dark drive hole. */
private fun DrawScope.drawHub(cx: Float, angle: Float) {
    val c = Offset(cx, HUB_Y)
    drawCircle(Color.Black.copy(alpha = 0.45f), HUB_R + 4f, c + Offset(2f, 3f))
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFEDECE8), 0.7f to HubFace, 1f to Color(0xFF9C9B97), center = c - Offset(18f, 22f), radius = HUB_R * 1.5f),
        HUB_R, c,
    )
    drawCircle(Color(0xFF7D7C78), HUB_R, c, style = Stroke(2f))
    drawCircle(Hole, 46f, c)
    rotate(angle, c) {
        for (t in 0 until 6) {
            rotate(t * 60f, c) {
                drawRect(HubFace, Offset(cx - 7f, HUB_Y - 47f), Size(14f, 17f))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(cx + 5f, HUB_Y - 47f), Size(2f, 17f))
            }
            rotate(t * 60f + 30f, c) { drawCircle(Color(0xFFB5B4B0), 5f, Offset(cx, HUB_Y - 66f)) }
        }
    }
}

/** Clear window plastic of the cassette: ruler marks between the hubs, a soft sheen, a thin frame. */
private fun DrawScope.drawWindowGlass() {
    for (i in 0..16) {
        val x = 404f + i * 12.25f
        val long = i % 4 == 0
        drawLine(Color.White.copy(alpha = 0.5f), Offset(x, Window.top + 2f), Offset(x, Window.top + if (long) 22f else 12f), 1.6f)
    }
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Window.top, Window.top + 90f),
        Window.topLeft(), Window.size(), CornerRadius(14f),
    )
    drawRoundRect(Color.Black.copy(alpha = 0.8f), Window.topLeft(), Window.size(), CornerRadius(14f), Stroke(4f))
    drawRoundRect(Color.White.copy(alpha = 0.10f), Window.topLeft() + Offset(0f, 3f), Window.size(), CornerRadius(14f), Stroke(1.5f))
}

/**
 * The door over everything: an opaque smoked panel with a window cut out, the smoked acrylic
 * in the window (tint, one soft reflection, an inner shadow), silver trim round the door and
 * the window, the printed legends.
 */
private fun DrawScope.drawDoor(tm: TextMeasurer, trim: Color, topText: String, bottomText: String) {
    val px = 1.dp.toPx()
    val win = doorWindow()
    val r = CornerRadius(6.dp.toPx())
    val panel = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, r))
        addRoundRect(win)
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(panel, Brush.verticalGradient(listOf(Color(0xFF1C1D20), Color(0xFF111214), Color(0xFF0C0D0E))))
    // a faint sheen across the upper half of the panel
    drawPath(panel, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), 0f, size.height * 0.45f))
    // smoked acrylic in the window
    clipPath(Path().apply { addRoundRect(win) }) {
        drawRect(Color(0xFF0B0D12).copy(alpha = 0.34f))
        val glare = Path().apply {
            moveTo(win.left + win.width * 0.05f, win.top); lineTo(win.left + win.width * 0.30f, win.top)
            lineTo(win.left + win.width * 0.18f, win.bottom); lineTo(win.left - win.width * 0.07f, win.bottom); close()
        }
        drawPath(glare, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.02f)), win.top, win.bottom))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), win.top, win.top + 7 * px))
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), win.left, win.left + 6 * px))
    }
    // silver trim: lit from above, darker at the bottom edge
    val trimBrush = Brush.verticalGradient(listOf(trim, trim.copy(alpha = 0.45f), trim.copy(alpha = 0.8f)))
    drawRoundRect(trimBrush, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), r, style = Stroke(1.5f * px))
    drawRoundRect(Color.Black.copy(alpha = 0.8f), Offset(2.5f * px, 2.5f * px), Size(size.width - 5 * px, size.height - 5 * px), CornerRadius(4.dp.toPx()), Stroke(px))
    drawRoundRect(
        Brush.verticalGradient(listOf(trim.copy(alpha = 0.5f), trim), win.top, win.bottom),
        Offset(win.left - px, win.top - px), Size(win.width + 2 * px, win.height + 2 * px), CornerRadius(5.dp.toPx()), style = Stroke(1.2f * px),
    )
    // printed legends
    val legend = TextStyle(color = Color(0xFF9EA1A7), fontSize = 9.dp.toSp(), letterSpacing = 1.4.dp.toSp(), fontFamily = FontFamily.SansSerif)
    val top = tm.measure(topText, legend)
    drawText(top, topLeft = Offset((size.width - top.size.width) / 2, (win.top - top.size.height) / 2 + px))
    val bottom = tm.measure(bottomText, legend.copy(color = Color(0xFF7E8187)))
    drawText(bottom, topLeft = Offset((size.width - bottom.size.width) / 2, win.bottom + (size.height - win.bottom - bottom.size.height) / 2))
    // tape direction arrows either side of the window
    val ay = win.top + win.height / 2
    val arrow = Color(0xFF6E7176)
    drawPath(Path().apply { moveTo(win.left - 9 * px, ay - 4 * px); lineTo(win.left - 15 * px, ay); lineTo(win.left - 9 * px, ay + 4 * px); close() }, arrow)
    drawPath(Path().apply { moveTo(win.right + 9 * px, ay - 4 * px); lineTo(win.right + 15 * px, ay); lineTo(win.right + 9 * px, ay + 4 * px); close() }, arrow)
}

private fun RoundRect.topLeft() = Offset(left, top)
private fun RoundRect.size() = Size(width, height)

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
