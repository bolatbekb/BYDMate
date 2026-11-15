package com.bydmate.app.ui.home.hybrid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The RS stack is the mockup itself (res/drawable-nodpi/rs_base.webp, 2560 x 1208, twice the car's
 * 1280 x 604 dp content area) with the live parts erased (tools/rs/RsBackplate.java). Everything
 * the app draws on top is placed on the mockup's pixel grid, converted by [K].
 */

/** Mockup pixel to dp. */
internal const val K = 0.5f

internal fun px(v: Int): Dp = (v * K).dp
internal fun px(v: Float): Dp = (v * K).dp
internal fun fs(v: Float): TextUnit = (v * K).sp

/** Placed at mockup pixel ([x], [y]) with mockup size [w] x [h]. */
internal fun Modifier.at(x: Int, y: Int, w: Int, h: Int): Modifier = offset(px(x), px(y)).size(px(w), px(h))

internal object Rs {
    val Letterbox = Color(0xFF0B0B0C)
    val Cyan = Color(0xFF3CDDF2)
    val CyanTrack = Color(0xFF123A40)
    val White = Color(0xFFECECEA)
    val Cream = Color(0xFFF1E8D2)
    val Green = Color(0xFF2EE070)
    val GreenLed = Color(0xFF35E04A)
    val Yellow = Color(0xFFF2D83A)
    val Lime = Color(0xFFC9EE45)
    val Amber = Color(0xFFFF8E22)
    val Dim = Color(0xFF8A908C)
    val Screen = Color(0xFF06130C)
}

/**
 * One line of text whose vertical centre is at mockup y [cy]: left-aligned at [x], or centred on
 * it with [centre]. [size] is the font size in mockup pixels; [glow] lights it like the display.
 */
@Composable
internal fun Txt(
    text: String,
    x: Int,
    cy: Int,
    size: Float,
    color: Color = Rs.White,
    glow: Boolean = false,
    centre: Boolean = false,
    right: Boolean = false,
    width: Int = 600,
    weight: FontWeight = FontWeight.Normal,
) {
    val h = (size * 1.4f).toInt()
    val left = when {
        centre -> x - width / 2
        right -> x - width
        else -> x
    }
    Box(
        Modifier.at(left, cy - h / 2, width, h),
        contentAlignment = when {
            centre -> Alignment.Center
            right -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        },
    ) {
        Text(
            text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centre) TextAlign.Center else if (right) TextAlign.End else TextAlign.Start,
            style = TextStyle(
                color = color, fontSize = fs(size), fontWeight = weight, fontFamily = FontFamily.SansSerif,
                lineHeight = fs(size * 1.2f), letterSpacing = 0.sp,
                shadow = if (glow) Shadow(color.copy(alpha = 0.7f), blurRadius = size * K * 0.8f) else null,
            ),
        )
    }
}

/** Value and its unit on one baseline, e.g. "824" + " км". */
@Composable
internal fun ValueUnit(value: String, unit: String, x: Int, cy: Int, size: Float, unitSize: Float, color: Color = Rs.Cyan) {
    Box(Modifier.at(x, cy - (size * 0.7f).toInt(), 700, (size * 1.4f).toInt()), contentAlignment = Alignment.CenterStart) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
            Text(value, maxLines = 1, softWrap = false, style = valueStyle(size, color))
            Text(unit, maxLines = 1, softWrap = false, style = valueStyle(unitSize, color), modifier = Modifier.offset(y = -px(size * 0.06f)))
        }
    }
}

private fun valueStyle(size: Float, color: Color) = TextStyle(
    color = color, fontSize = fs(size), fontFamily = FontFamily.SansSerif, lineHeight = fs(size * 1.2f),
    shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = size * K * 0.8f),
)

/**
 * Ten-segment level bar as on the mockup: lit blocks in [color] with a light rim, the rest dark
 * with a grey outline. All dark when unknown.
 */
@Composable
internal fun SegmentBar(pct: Int?, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val n = 10
        val gap = size.width * 0.012f
        val w = (size.width - gap * (n - 1)) / n
        val lit = pct?.let { ((it + 5) / 10).coerceIn(0, n) } ?: 0
        val px1 = 1.dp.toPx()
        for (i in 0 until n) {
            val x = i * (w + gap)
            if (i < lit) {
                drawRect(color, Offset(x, 0f), Size(w, size.height))
                drawRect(Color.White.copy(alpha = 0.25f), Offset(x, 0f), Size(w, size.height * 0.35f))
                drawRect(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Size(w, size.height), style = Stroke(px1))
            } else {
                drawRect(Color(0xFF0B0F0D), Offset(x, 0f), Size(w, size.height))
                drawRect(Color(0xFF6B6F6D), Offset(x, 0f), Size(w, size.height), style = Stroke(px1))
            }
        }
    }
}

/** The AUTO badge of the climate: an outlined pill. */
@Composable
internal fun AutoBadge(x: Int, cy: Int) {
    Canvas(Modifier.at(x, cy - 17, 88, 34)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color(0xFF0B3036), cornerRadius = r)
        drawRoundRect(Rs.Cyan.copy(alpha = 0.6f), cornerRadius = r, style = Stroke(1.dp.toPx()))
    }
    Txt("AUTO", x + 44, cy, 24f, Rs.Cyan, centre = true, width = 88)
}
