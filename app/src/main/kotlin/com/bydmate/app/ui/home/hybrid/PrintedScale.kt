package com.bydmate.app.ui.home.hybrid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A meter printed on the mockup: its pivot ([cx], [cy], mockup pixels, below the screen), and
 * the angle (degrees, maths convention) of each printed number in [marks], so the needle lands
 * on the scale as drawn even where the artwork's spacing is uneven. The needle runs from
 * radius [inner] to [outer].
 */
internal data class PrintedScale(
    val cx: Float,
    val cy: Float,
    val inner: Float,
    val outer: Float,
    val marks: List<Pair<Double, Float>>,
) {
    /** Angle for [value], linear between the two printed marks around it; null parks at the start. */
    fun angleOf(value: Double?): Float {
        val lo = marks.first().first
        val hi = marks.last().first
        val v = (value ?: lo).coerceIn(lo, hi)
        for (i in 0 until marks.size - 1) {
            val (a, da) = marks[i]
            val (b, db) = marks[i + 1]
            if (v <= b) return da + (db - da) * ((v - a) / (b - a)).toFloat()
        }
        return marks.last().second
    }
}

/**
 * Draws a needle of [scale] from the pivot [pivot] along the unit vector [dir] (canvas pixels,
 * y down), [s] canvas pixels per mockup pixel, faded to [alpha].
 */
internal typealias NeedlePainter = DrawScope.(scale: PrintedScale, pivot: Offset, dir: Offset, s: Float, alpha: Float) -> Unit

/**
 * The needle of [scale] at [value], drawn over the meter screen whose top-left is mockup pixel
 * ([originX], [originY]) by [painter] (the RS blade by default). Dimmed and parked at zero while
 * the value is unknown.
 */
@Composable
internal fun MeterNeedle(
    scale: PrintedScale,
    value: Double?,
    originX: Int,
    originY: Int,
    modifier: Modifier,
    painter: NeedlePainter = RsNeedle, // fork: C-Drive draws its own needle
) {
    // read in the draw phase only: the swing redraws this canvas, never recomposes the screen
    val angleState = animateFloatAsState(scale.angleOf(value), tween(700), label = "needle")
    Canvas(modifier.clipToBounds()) {
        val angle = angleState.value
        val s = K * density // px per mockup pixel
        val c = Offset((scale.cx - originX) * s, (scale.cy - originY) * s)
        val a = angle * PI.toFloat() / 180f
        painter(scale, c, Offset(cos(a), -sin(a)), s, if (value == null) 0.45f else 1f)
    }
}

/** A tapered cream blade with a brown root and a soft shadow, like the RS mockup's. */
private val RsNeedle: NeedlePainter = { scale, c, dir, s, alpha ->
    val side = Offset(-dir.y, dir.x)
    val tip = c + dir * (scale.outer * s)
    val root = c + dir * (scale.inner * s)
    val blade = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(root.x + side.x * 5f * s, root.y + side.y * 5f * s)
        lineTo(root.x - side.x * 5f * s, root.y - side.y * 5f * s)
        close()
    }
    translate(9f * s, 6f * s) { drawPath(blade, Color.Black.copy(alpha = 0.45f * alpha)) }
    drawPath(
        blade,
        Brush.linearGradient(
            0f to Color(0xFF6E4B2A), 0.35f to Color(0xFFC9A77C), 0.7f to Color(0xFFF4EBDD), 1f to Color.White,
            start = root, end = tip,
        ),
        alpha = alpha,
    )
    drawLine(Color.White.copy(alpha = 0.6f * alpha), root + side * (1.5f * s), tip, 1.2f * s)
}

/** Engine speed: 0..8 ×1000 rpm, the numbers as printed. */
internal val RpmScale = PrintedScale(
    855f, 1080f, 245f, 452f,
    listOf(0.0 to 142.7f, 1.0 to 129.6f, 2.0 to 117.7f, 3.0 to 103.3f, 4.0 to 89.0f, 5.0 to 74.6f, 6.0 to 61.6f, 7.0 to 49.8f, 8.0 to 38.2f),
)

/** AC compressor power: 0..4.5 kW, ten marks where the artwork has its ten numbers. */
internal val CompressorScale = PrintedScale(
    2000f, 1128f, 299f, 452f,
    listOf(
        0.0 to 136.5f, 0.5 to 126.7f, 1.0 to 115.2f, 1.5 to 103.0f, 2.0 to 87.4f,
        2.5 to 74.5f, 3.0 to 63.5f, 3.5 to 52.1f, 4.0 to 42.7f, 4.5 to 35.4f,
    ),
)

/** Where the compressor numbers are printed (mockup pixels, centres), in [CompressorScale] order. */
internal val CompressorLabels = listOf(
    1635 to 782, 1700 to 725, 1790 to 682, 1892 to 660, 2022 to 650,
    2130 to 660, 2220 to 687, 2310 to 730, 2380 to 777, 2415 to 833,
)
