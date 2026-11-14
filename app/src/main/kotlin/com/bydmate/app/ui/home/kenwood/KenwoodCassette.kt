package com.bydmate.app.ui.home.kenwood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.bydmate.app.ui.home.NowPlaying

/* HF-S cassette, drawn on the mockup's 440 x 280 SVG grid (Cassette.dc.html, variant "hfs"). */

private val Shell = Color(0xFF17181A)
private val ShellTop = Color(0xFF26272A)
private val Edge = Color(0xFF3E4044)
private val LabelBg = Color(0xFF1B1C1F)
private val Band = Color(0xFF0F1012)
private val Teal = Color(0xFF2EC4C4)
private val Orange = Color(0xFFF07A1E)
private val WindowFrame = Color(0xFF0E0F10)
private val WindowBg = Color(0xFF101012)
private val Ink = Color(0xFF1F3E8F)
private val Caption = Color(0xFF7A7468)
private val Rule = Color(0xFFB9B2A3)
private val Hole = Color(0xFF0B0A09)
private val TapeEdge = Color(0xFF4A3120)
private val HubWhite = Color(0xFFF6F4EE)

private val windowRect = RoundRect(118f, 96f, 322f, 152f, CornerRadius(8f))

/** Five screws: x, y and whether the slot is diagonal. */
private val screws = listOf(Triple(20f, 20f, false), Triple(420f, 20f, true), Triple(20f, 260f, true), Triple(420f, 260f, false), Triple(220f, 198f, true))

/**
 * HF-S tape in the deck well. The tape packs follow the track progress (left shrinks, right
 * grows); the hubs turn only while playing, fast while winding.
 */
@Composable
internal fun HfsCassette(music: NowPlaying, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val artist = music.artist?.takeIf { it.isNotBlank() } ?: "—"
    val title = music.title?.takeIf { it.isNotBlank() } ?: "Нет музыки"
    val p = music.progress
    val angle = rememberRunningClock(music.isPlaying, if (music.winding) 900f else 360f / 2.2f)
    Box(
        modifier
            .aspectRatio(440f / 280f)
            .semantics { contentDescription = "Кассета: $artist — $title" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 440f
            withTransform({ scale(k, k, Offset.Zero) }) { drawBody(p) }
            drawLabels(measurer, k, artist, title)
        }
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 440f
            val a = angle.value
            withTransform({ scale(k, k, Offset.Zero) }) {
                drawHub(168f, a)
                drawHub(272f, a)
                drawRoundRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.35f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.White.copy(alpha = 0.08f),
                        start = Offset(1f, 1f), end = Offset(439f, 279f),
                    ),
                    Offset(1f, 1f), Size(438f, 278f), CornerRadius(12f),
                )
            }
        }
    }
}

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, color: Color, r: Float = 0f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))

private fun DrawScope.drawBody(progress: Float) {
    // shell and moulded rim
    drawRoundRect(Brush.verticalGradient(listOf(ShellTop, Shell), 1f, 279f), Offset(1f, 1f), Size(438f, 278f), CornerRadius(12f))
    drawRoundRect(Edge, Offset(1f, 1f), Size(438f, 278f), CornerRadius(12f), Stroke(2f))
    drawRoundRect(Edge.copy(alpha = 0.6f), Offset(9f, 9f), Size(422f, 262f), CornerRadius(8f), Stroke(1f))

    // label with the dark top band
    box(26f, 16f, 388f, 176f, LabelBg, 6f)
    box(26f, 16f, 388f, 30f, Band, 6f)
    box(26f, 40f, 388f, 6f, Band)
    box(34f, 54f, 34f, 34f, Color.White, 3f)
    drawRoundRect(Color(0xFF2A2A2A), Offset(34f, 54f), Size(34f, 34f), CornerRadius(3f), Stroke(1.5f))
    drawLine(Rule, Offset(116f, 66f), Offset(404f, 66f), 1f)
    drawLine(Rule, Offset(116f, 88f), Offset(404f, 88f), 1f)

    // window with the tape packs
    box(104f, 92f, 232f, 64f, WindowFrame, 12f)
    box(118f, 96f, 204f, 56f, WindowBg, 8f)
    clipPath(Path().apply { addRoundRect(windowRect) }) {
        tapePack(Offset(168f, 124f), 21f + 34f * (1f - progress))
        tapePack(Offset(272f, 124f), 21f + 34f * progress)
        drawLine(TapeEdge, Offset(118f, 150f), Offset(322f, 150f), 2f)
        drawRect(Color.White.copy(alpha = 0.14f), Offset(118f, 96f), Size(204f, 56f))
    }
    for (i in 0..10) {
        val x = 126f + i * 18.8f
        drawLine(Color.White.copy(alpha = 0.55f), Offset(x, 96f), Offset(x, if (i % 5 == 0) 104f else 100f), 1f)
    }
    drawRoundRect(Color.Black.copy(alpha = 0.5f), Offset(118f, 96f), Size(204f, 56f), CornerRadius(8f), Stroke(1.5f))

    // stripes
    box(26f, 162f, 388f, 10f, Orange)
    box(26f, 174f, 388f, 6f, Teal)

    // bottom trapezoid with the capstan and guide holes
    val trap = Path().apply { moveTo(92f, 279f); lineTo(120f, 208f); lineTo(320f, 208f); lineTo(348f, 279f); close() }
    drawPath(trap, Shell)
    drawPath(trap, Edge, style = Stroke(1.5f))
    drawLine(Color.White.copy(alpha = 0.12f), Offset(120f, 208f), Offset(320f, 208f), 1f)
    box(136f, 234f, 14f, 22f, Hole, 2f)
    box(290f, 234f, 14f, 22f, Hole, 2f)
    box(198f, 238f, 44f, 24f, Hole, 3f)
    drawCircle(Hole, 6f, Offset(172f, 246f))
    drawCircle(Hole, 6f, Offset(268f, 246f))
    box(198f, 262f, 44f, 6f, TapeEdge)

    for ((x, y, diag) in screws) {
        drawCircle(Color(0xFF8C877E), 7f, Offset(x, y))
        drawCircle(Color(0xFF3A3631), 7f, Offset(x, y), style = Stroke(1f))
        if (diag) drawLine(Color(0xFF3A3631), Offset(x - 4f, y - 4f), Offset(x + 4f, y + 4f), 2f)
        else drawLine(Color(0xFF3A3631), Offset(x - 5f, y), Offset(x + 5f, y), 2f)
    }
}

private fun DrawScope.tapePack(c: Offset, r: Float) = drawCircle(
    Brush.radialGradient(0.3f to Color(0xFF7B5638), 0.75f to TapeEdge, 1f to Color(0xFF2B1C11), center = c, radius = r),
    r, c,
)

/** White hub with an orange ring and six inward teeth, turned by [angle] degrees. */
private fun DrawScope.drawHub(cx: Float, angle: Float) {
    val c = Offset(cx, 124f)
    rotate(angle, c) {
        drawCircle(HubWhite, 19f, c)
        drawCircle(Orange, 19f, c, style = Stroke(4f))
        drawCircle(Color(0xFF1A1816), 11f, c)
        for (t in 0 until 6) rotate(t * 60f, c) { drawRect(HubWhite, Offset(cx - 2f, 112f), Size(4f, 6f)) }
    }
}

private enum class Anchor { START, MIDDLE, END }

/** SVG-style text: ([x], [y]) is the baseline point in grid units, [size] the font size in units. */
private fun DrawScope.svgText(
    m: TextMeasurer, text: String, x: Float, y: Float, size: Float, color: Color, k: Float,
    weight: FontWeight = FontWeight.Normal, family: FontFamily = FontFamily.SansSerif,
    spacing: Float = 0f, anchor: Anchor = Anchor.START, maxWidth: Float? = null,
) {
    val layout = m.measure(
        text = AnnotatedString(text),
        style = TextStyle(
            color = color,
            fontSize = (size * k).toSp(),
            fontWeight = weight,
            fontFamily = family,
            letterSpacing = (spacing * k).toSp(),
        ),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = maxWidth?.let { Constraints(maxWidth = (it * k).toInt()) } ?: Constraints(),
    )
    val w = layout.size.width
    val left = when (anchor) {
        Anchor.START -> x * k
        Anchor.MIDDLE -> x * k - w / 2f
        Anchor.END -> x * k - w
    }
    drawText(layout, topLeft = Offset(left, y * k - layout.firstBaseline))
}

private fun DrawScope.drawLabels(m: TextMeasurer, k: Float, artist: String, title: String) {
    svgText(m, "HF-S", 40f, 38f, 17f, Teal, k, FontWeight.Bold, spacing = 2f)
    svgText(m, "HIGH FIDELITY · TYPE II", 400f, 38f, 15f, Teal, k, FontWeight.SemiBold, spacing = 1f, anchor = Anchor.END)
    svgText(m, "A", 51f, 80f, 26f, Color(0xFF1A1A1A), k, FontWeight.Bold, anchor = Anchor.MIDDLE)
    svgText(m, "ARTIST", 80f, 64f, 10f, Caption, k, spacing = 1f)
    svgText(m, "TITLE", 80f, 86f, 10f, Caption, k, spacing = 1f)
    svgText(m, artist, 120f, 62f, 22f, Ink, k, FontWeight.Bold, FontFamily.Cursive, maxWidth = 284f)
    svgText(m, title, 120f, 84f, 20f, Ink, k, FontWeight.SemiBold, FontFamily.Cursive, maxWidth = 284f)
    svgText(m, "90 MIN · 45 + 45", 408f, 188f, 11f, Color(0xFF6E685C), k, spacing = 1f, anchor = Anchor.END)
}
