@file:Suppress("TooManyFunctions") // Canvas drawing of one hardware panel: many small private painters by design

package com.bydmate.app.ui.home.onkyo

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette of the deck, straight from the mockup. */
internal object Ok {
    val Letterbox = Color(0xFF09090A)
    val Brushed = Color(0xFF202124)
    val BrushedLine = Color(0xFF1C1D1F)
    val Edge = Color(0xFF47494E)
    val Edge2 = Color(0xFF3A3C41)
    val Shadow = Color(0xFF040405)
    val Face = Color(0xFF16171A)
    val KeyFace = Color(0xFF1A1B1E)
    val KeyDown = Color(0xFF101112)
    val Panel = Color(0xFF111214)
    val Bar = Color(0xFF2B2C2F)
    val Text = Color(0xFFC9CBCF)
    val Light = Color(0xFFB5B8BD)
    val White = Color(0xFFE8E9EB)
    val Dim = Color(0xFF8E9196)
    val Off = Color(0xFF4A4C51)
    val LedOff = Color(0xFF2A2B2E)
    val Green = Color(0xFF3CFF6E)
    val Amber = Color(0xFFE8B830)
    val Yellow = Color(0xFFF2C230)
    val Red = Color(0xFFFF3B2F)
    val KeyRed = Color(0xFFFF5A4E)
    val Gold = Color(0xFFC9A64A)
    val CentreBg = Color(0xFF141517)
}

/** One line of panel lettering. */
@Composable
internal fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 9.sp,
    color: Color = Ok.Light,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = 1.sp,
    family: FontFamily = FontFamily.SansSerif,
    style: FontStyle = FontStyle.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    shadow: Shadow? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, letterSpacing = spacing,
            fontFamily = family, fontStyle = style, textAlign = align,
            lineHeight = size * 1.2f, shadow = shadow,
        ),
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
        maxLines = maxLines,
    )
}

/** Brushed black aluminium with a light top edge and a dark bottom lip. */
internal fun Modifier.brushedAluminium(): Modifier = drawBehind {
    val px = 1.dp.toPx()
    drawRect(Ok.Brushed)
    var y = size.height
    while (y > 0f) {
        drawRect(Ok.BrushedLine, Offset(0f, y - px), Size(size.width, px))
        y -= 3 * px
    }
    drawRect(Ok.Edge, Offset.Zero, Size(size.width, px))
    drawRect(Ok.Shadow, Offset(0f, size.height - 2 * px), Size(size.width, 2 * px))
}

/** Black inset panel with a thin grey frame. */
internal fun Modifier.insetPanel(): Modifier = background(Ok.Panel).border(1.dp, Ok.Edge2)

/** A plastic key face: raised with a dark lip below, or pushed in with an inner shadow. */
internal fun Modifier.keyFace(down: Boolean, face: Color = Ok.KeyFace): Modifier = drawBehind {
    val px = 1.dp.toPx()
    if (!down) drawRect(Ok.Shadow, Offset(0f, 2 * px), size)
    drawRect(if (down) Ok.KeyDown else face)
    if (down) {
        drawRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.8f), 1f to Color.Transparent, endY = 6 * px),
            size = Size(size.width, 6 * px),
        )
    }
    drawRect(Ok.Edge, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), style = Stroke(px))
}

/** Soft halo behind a lit lamp. */
internal fun DrawScope.glow(color: Color, rect: Rect, radius: Float) {
    if (radius <= 0f) return
    drawIntoCanvas { c ->
        val p = Paint().asFrameworkPaint().apply {
            this.color = color.copy(alpha = 0.85f).toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        c.nativeCanvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, p)
    }
}

/** A lamp strip: [lit] glows in [color], otherwise dark plastic. */
@Composable
internal fun LedStrip(lit: Boolean, modifier: Modifier, color: Color = Ok.Amber, off: Color = Ok.LedOff, halo: Dp = 6.dp) {
    Box(
        modifier.drawBehind {
            if (lit) glow(color, Rect(Offset.Zero, size), halo.toPx())
            drawRect(if (lit) color else off)
        }
    )
}

/** Grey title strip on top of an inset panel. */
@Composable
internal fun TitleBar(text: String) {
    Box(Modifier.fillMaxWidth().background(Ok.Bar).padding(vertical = 1.dp), contentAlignment = Alignment.Center) {
        Label(text)
    }
}

/** Inset panel with a title strip; content is centred. */
@Composable
internal fun TitledPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.insetPanel().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleBar(title)
        content()
    }
}

/** Small round control knob, lit from the upper left. */
@Composable
internal fun SmallKnob(size: Dp = 26.dp) {
    Box(
        Modifier.size(size).drawBehind {
            val r = this.size.minDimension / 2
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFF3A3C41), 1f to Color(0xFF0E0F11),
                    center = Offset(this.size.width * 0.4f, this.size.height * 0.35f), radius = r * 1.5f,
                ),
            )
            drawCircle(Color(0xFF5A5C61), r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
    )
}

/** 6.3 mm headphone jack. */
@Composable
internal fun Jack() {
    Box(Modifier.size(28.dp).background(Color(0xFF6E7176), CircleShape).padding(4.dp).background(Color(0xFF050506), CircleShape))
}

/** Flat rectangular push key (OPEN, POWER, the toggles). */
@Composable
internal fun FlatKey(modifier: Modifier, onClick: (() -> Unit)? = null) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier
            .keyFace(pressed, Ok.Face)
            .then(if (onClick != null) Modifier.clickable(src, null, onClick = onClick) else Modifier)
    )
}

/** Small key with a label above and an amber LED strip between label and key. */
@Composable
internal fun LedKey(label: String, lit: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(label, size = 8.sp, spacing = 0.5.sp)
        LedStrip(lit, Modifier.fillMaxWidth().height(3.dp))
        FlatKey(Modifier.fillMaxWidth().height(22.dp), onClick)
    }
}

/** Glyphs of the transport keys, drawn rather than typeset so every head unit font shows them. */
internal enum class Glyph { PLAY, PREV, NEXT, STOP, PAUSE, REC, MENU }

@Composable
internal fun GlyphIcon(glyph: Glyph, color: Color, height: Dp = 9.dp) {
    val w = when (glyph) { Glyph.PREV, Glyph.NEXT -> height * 1.8f; else -> height }
    Canvas(Modifier.size(w, height)) {
        val h = size.height
        fun tri(x: Float, right: Boolean, tw: Float) {
            val p = Path().apply {
                if (right) { moveTo(x, 0f); lineTo(x + tw, h / 2); lineTo(x, h) } else { moveTo(x + tw, 0f); lineTo(x, h / 2); lineTo(x + tw, h) }
                close()
            }
            drawPath(p, color)
        }
        when (glyph) {
            Glyph.PLAY -> tri(h * 0.1f, true, h * 0.85f)
            Glyph.NEXT -> { tri(0f, true, size.width / 2); tri(size.width / 2, true, size.width / 2) }
            Glyph.PREV -> { tri(0f, false, size.width / 2); tri(size.width / 2, false, size.width / 2) }
            Glyph.STOP -> drawRect(color, Offset(h * 0.1f, h * 0.1f), Size(h * 0.8f, h * 0.8f))
            Glyph.PAUSE -> {
                drawRect(color, Offset(h * 0.1f, 0f), Size(h * 0.28f, h))
                drawRect(color, Offset(h * 0.62f, 0f), Size(h * 0.28f, h))
            }
            Glyph.REC -> drawCircle(color, h * 0.42f)
            Glyph.MENU -> for (i in 0..2) drawRect(color, Offset(0f, h * (0.1f + i * 0.35f)), Size(h, h * 0.15f))
        }
    }
}

/** Measures a line once for drawing on a canvas, anchored at its baseline. */
internal fun DrawScope.drawAtBaseline(text: TextLayoutResult, x: Float, baseline: Float, anchor: Float = 0.5f) {
    drawText(text, topLeft = Offset(x - text.size.width * anchor, baseline - text.firstBaseline))
}
