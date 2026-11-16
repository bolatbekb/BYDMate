package com.bydmate.app.ui.home.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * ([originX], [originY]) by [painter]. Dimmed and parked at zero while the value is unknown.
 */
@Composable
internal fun MeterNeedle(
    scale: PrintedScale,
    value: Double?,
    originX: Int,
    originY: Int,
    modifier: Modifier,
    painter: NeedlePainter,
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
