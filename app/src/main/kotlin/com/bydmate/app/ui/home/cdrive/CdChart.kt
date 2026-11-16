@file:Suppress("MagicNumber") // drawn on the backplate's pixel grid (tools/cdrive/LAYOUT.md)

package com.bydmate.app.ui.home.cdrive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.bydmate.app.R
import com.bydmate.app.ui.home.hybrid.K
import com.bydmate.app.ui.home.hybrid.PressArea
import com.bydmate.app.ui.home.hybrid.at
import com.bydmate.app.ui.home.hybrid.fs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/* ---------------------------------------------------------------- monotone spline (pure) */

/**
 * Fritsch–Carlson monotone cubic interpolation: the curve passes through every point and, between
 * two neighbours, never leaves the range of their values (no overshoot past the data).
 */
internal object MonotoneSpline {

    /** Tangents at each point for strictly increasing [xs]. */
    fun tangents(xs: DoubleArray, ys: DoubleArray): DoubleArray {
        val n = xs.size
        val m = DoubleArray(n)
        if (n < 2) return m
        val d = DoubleArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        m[0] = d[0]
        m[n - 1] = d[n - 2]
        for (k in 1 until n - 1) m[k] = if (d[k - 1] * d[k] <= 0.0) 0.0 else (d[k - 1] + d[k]) / 2.0
        for (k in 0 until n - 1) {
            if (d[k] == 0.0) {
                m[k] = 0.0
                m[k + 1] = 0.0
                continue
            }
            val a = m[k] / d[k]
            val b = m[k + 1] / d[k]
            // a tangent against the secant's direction would swing past the data: flatten it
            if (a < 0.0) m[k] = 0.0
            if (b < 0.0) m[k + 1] = 0.0
            val h = a * a + b * b
            if (h > 9.0) {
                val t = 3.0 / sqrt(h)
                m[k] = t * a * d[k]
                m[k + 1] = t * b * d[k]
            }
        }
        return m
    }

    /** The curve's value at [x] (inside xs.first()..xs.last()). */
    fun at(xs: DoubleArray, ys: DoubleArray, m: DoubleArray, x: Double): Double {
        var k = 0
        while (k < xs.size - 2 && x > xs[k + 1]) k++
        val h = xs[k + 1] - xs[k]
        val t = (x - xs[k]) / h
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] +
            (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
    }

    /** Index ranges of consecutive non-null values: a null breaks the line. */
    fun runs(values: List<Double?>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        for (i in values.indices) {
            if (values[i] != null) {
                if (start < 0) start = i
            } else if (start >= 0) {
                out += start until i
                start = -1
            }
        }
        if (start >= 0) out += start until values.size
        return out
    }
}

/* ---------------------------------------------------------------- axes (pure) */

/** [n] grid intervals; the left axis runs 0..[energyTop], the right one 0..[fuelTop]. */
internal data class AxisTicks(val n: Int, val energyTop: Double, val fuelTop: Double)

private val NiceSteps = doubleArrayOf(1.0, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

/** The smallest "nice" step (1, 2, 2.5, 3, 4, 5, 6, 8 × 10ⁿ) that is at least [x]. */
private fun niceStep(x: Double): Double {
    if (!(x > 0.0)) return 1.0
    val p = 10.0.pow(floor(log10(x)))
    return NiceSteps.first { it * p >= x - 1e-9 } * p
}

/**
 * Shared grid rows for both axes: 3 or 4 intervals, whichever gives nice steps on both with the
 * least headroom over [energyMax] and [fuelMax] (energy alone when [fuelMax] is null).
 */
internal fun axisTicks(energyMax: Double, fuelMax: Double?): AxisTicks =
    (3..4).map { n ->
        AxisTicks(n, niceStep(energyMax / n) * n, niceStep((fuelMax ?: 12.0) / n) * n)
    }.minBy { t ->
        t.energyTop / energyMax + (if (fuelMax != null) t.fuelTop / fuelMax else 0.0)
    }

/** "10", "2.5": an axis number without a zero fraction. */
internal fun axisText(v: Double): String =
    if (abs(v - Math.round(v)) < 1e-6) Math.round(v).toString() else String.format(Locale.US, "%.1f", v)

/* ---------------------------------------------------------------- period keys */

private const val PERIOD_X = 64
private const val PERIOD_W = 180
private const val PERIOD_H = 65
private val PeriodY = intArrayOf(633, 712, 789)
private val PeriodNames = listOf("День", "Неделя", "Месяц")
private const val PERIOD_GLYPHS_X = 80
private const val PERIOD_GLYPHS_Y = 640
private const val PERIOD_GLYPHS_W = 153
private const val PERIOD_GLYPHS_H = 210
private val PeriodIconLit = Color(0xFF35ED50)

/** «День / Неделя / Месяц»: the current period's key is lit; a press selects its period. */
@Composable
internal fun CdPeriodKeys(current: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    val icons = ImageBitmap.imageResource(R.drawable.cd_period_icons)
    val words = ImageBitmap.imageResource(R.drawable.cd_period_words)
    ChartPeriod.entries.forEachIndexed { i, p ->
        val y = PeriodY[i]
        if (p == current) {
            Image(
                painterResource(R.drawable.cd_period_lit), null,
                Modifier.at(PERIOD_X - 18, y - 19, 216, 97), contentScale = ContentScale.FillBounds,
            )
            Canvas(Modifier.at(PERIOD_X, y, PERIOD_W, PERIOD_H)) {
                clipRect {
                    val o = Offset((PERIOD_GLYPHS_X - PERIOD_X).toFloat(), (PERIOD_GLYPHS_Y - y).toFloat())
                    glyphLayer(icons, o, PERIOD_GLYPHS_W, PERIOD_GLYPHS_H, ColorFilter.tint(PeriodIconLit, BlendMode.Modulate))
                    glyphLayer(words, o, PERIOD_GLYPHS_W, PERIOD_GLYPHS_H, null)
                }
            }
        }
        PressArea(Modifier.at(PERIOD_X, y, PERIOD_W, PERIOD_H), PeriodNames[i], onClick = { onSelect(p) })
    }
}

/* ---------------------------------------------------------------- chart */

// the cleared part of the frame the canvas covers
private const val CHART_X = 296
private const val CHART_Y = 704
private const val CHART_W = 1078
private const val CHART_H = 197

// the plot, mockup pixels
private const val GRID_L = 367f
private const val GRID_R = 1295f
private const val PLOT_TOP = 716f
private const val PLOT_BASE = 855f
private const val POINT_L = 391f // the first bucket, under the mockup's "00:00"
private const val POINT_R = 1280f // the last bucket

// the printed "л/100 км" title, covered while the car has no fuel line
private const val FUEL_TITLE_X = 1232
private const val FUEL_TITLE_Y = 676
private const val FUEL_TITLE_W = 104
private const val FUEL_TITLE_H = 28

private val Panel = Color(0xFF151615)
private val GridLine = Color(0xFF3E413F)
private val Baseline = Color(0xFFE8E6E3)
private val EnergyAxis = Color(0xFF65FFFF)
private val FuelAxis = Color(0xFFFFFF0F)
private val TimeLabel = Color(0xFFF2F2F2)
private val EnergyLine = Color(0xFF3BFEFF)
private val FuelLine = Color(0xFFFFF70E)
private val EnergyFill = Color(0xFF10E6D2)
private val NoTrips = Color(0xFF8F9A97)

private const val AXIS_SIZE = 25f
private const val TIME_SIZE = 25f

/** One curve, built in mockup pixels: smooth runs, their fills down to the baseline, lone points. */
private class Curve(val lines: List<Path>, val fills: List<Pair<Path, Float>>, val dots: List<Offset>)

/** Everything the chart draws for one series, in mockup pixels and measured text. */
private class ChartScene(
    val ticks: AxisTicks,
    val rows: List<Float>,
    val columns: List<Float>,
    val energy: Curve?,
    val fuel: Curve?,
    val leftNumbers: List<Pair<TextLayoutResult, Float>>,
    val rightNumbers: List<Pair<TextLayoutResult, Float>>,
    val timeLabels: List<Pair<TextLayoutResult, Float>>,
    val empty: TextLayoutResult?,
)

private fun xOf(i: Int, n: Int): Float = if (n <= 1) POINT_L else POINT_L + (POINT_R - POINT_L) * i / (n - 1)

private fun yOf(v: Double, top: Double): Float = PLOT_BASE - ((v / top).coerceIn(0.0, 1.0) * (PLOT_BASE - PLOT_TOP)).toFloat()

/** The smooth curve through [values] (index = bucket) on an axis 0..[top]. */
private fun curve(values: List<Double?>, top: Double): Curve {
    val n = values.size
    val lines = ArrayList<Path>()
    val fills = ArrayList<Pair<Path, Float>>()
    val dots = ArrayList<Offset>()
    for (run in MonotoneSpline.runs(values)) {
        if (run.first == run.last) {
            dots += Offset(xOf(run.first, n), yOf(values[run.first]!!, top))
            continue
        }
        val xs = DoubleArray(run.count()) { run.first + it.toDouble() }
        val ys = DoubleArray(run.count()) { values[run.first + it]!! }
        val m = MonotoneSpline.tangents(xs, ys)
        val step = if (n <= 1) 0f else (POINT_R - POINT_L) / (n - 1)
        val scaleY = ((PLOT_BASE - PLOT_TOP) / top).toFloat()
        val line = Path()
        val fill = Path()
        line.moveTo(xOf(run.first, n), yOf(ys[0], top))
        fill.moveTo(xOf(run.first, n), PLOT_BASE)
        fill.lineTo(xOf(run.first, n), yOf(ys[0], top))
        var peak = yOf(ys[0], top)
        for (k in 0 until xs.size - 1) {
            // a Hermite segment as a Bézier: control points a third of the way along each tangent
            val x0 = xOf(run.first + k, n)
            val x1 = xOf(run.first + k + 1, n)
            val y0 = yOf(ys[k], top)
            val y1 = yOf(ys[k + 1], top)
            val c1y = y0 - (m[k] / 3.0).toFloat() * scaleY
            val c2y = y1 + (m[k + 1] / 3.0).toFloat() * scaleY
            line.cubicTo(x0 + step / 3f, c1y, x1 - step / 3f, c2y, x1, y1)
            fill.cubicTo(x0 + step / 3f, c1y, x1 - step / 3f, c2y, x1, y1)
            peak = minOf(peak, y1)
        }
        lines += line
        fill.lineTo(xOf(run.last, n), PLOT_BASE)
        fill.close()
        fills += fill to peak
    }
    return Curve(lines, fills, dots)
}

private fun TextMeasurer.label(text: String, size: Float, color: Color, density: Density) =
    measure(
        text, TextStyle(color = color, fontSize = fs(size), fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp),
        maxLines = 1, softWrap = false, density = density,
    )

/**
 * «Trip Computer & Energy Monitor»: the grid and axis numbers, the energy line (cyan, filled down
 * to zero) and the fuel line (yellow) as monotone curves broken at empty buckets, the period's
 * time labels; «Нет поездок за период» when the period has none. Hides the right axis for a car
 * without fuel. The paths are built once per series, the canvas sits on its own layer.
 */
@Composable
internal fun CdTripChart(series: ChartSeries?) {
    val measurer = rememberTextMeasurer()
    val dens = LocalDensity.current
    val scene = remember(series, dens) { buildScene(series, measurer, dens) }
    val hasFuel = series?.fuel != null
    if (series != null && !hasFuel) {
        Canvas(Modifier.at(FUEL_TITLE_X, FUEL_TITLE_Y, FUEL_TITLE_W, FUEL_TITLE_H)) { drawRect(Panel) }
    }
    Canvas(Modifier.at(CHART_X, CHART_Y, CHART_W, CHART_H).graphicsLayer().clipToBounds()) {
        val s = K * density
        withTransform({
            scale(s, s, Offset.Zero)
            translate(-CHART_X.toFloat(), -CHART_Y.toFloat())
        }) {
            for (y in scene.rows.dropLast(1)) drawLine(GridLine, Offset(GRID_L, y), Offset(GRID_R, y), 2f)
            for (x in scene.columns) {
                drawLine(GridLine, Offset(x, PLOT_TOP), Offset(x, PLOT_BASE), 1.5f)
                drawLine(Baseline.copy(alpha = 0.7f), Offset(x, PLOT_BASE), Offset(x, PLOT_BASE + 6f), 2f)
            }
            drawLine(Baseline.copy(alpha = 0.75f), Offset(GRID_L, PLOT_BASE), Offset(GRID_R, PLOT_BASE), 2f)

            scene.energy?.fills?.forEach { (p, peak) ->
                drawPath(
                    p,
                    Brush.verticalGradient(
                        0f to EnergyFill.copy(alpha = 0.32f), 1f to EnergyFill.copy(alpha = 0.03f),
                        startY = peak, endY = maxOf(PLOT_BASE, peak + 1f),
                    ),
                )
            }
            scene.fuel?.let { drawCurve(it, FuelLine) }
            scene.energy?.let { drawCurve(it, EnergyLine) }
        }
        for ((t, y) in scene.leftNumbers) {
            drawText(t, topLeft = Offset((350 - CHART_X) * s - t.size.width, (y - CHART_Y + 3f) * s - t.size.height / 2f))
        }
        for ((t, y) in scene.rightNumbers) {
            drawText(t, topLeft = Offset((1312 - CHART_X) * s, (y - CHART_Y + 3f) * s - t.size.height / 2f))
        }
        for ((t, x) in scene.timeLabels) {
            drawText(t, topLeft = Offset((x - CHART_X) * s - t.size.width / 2f, (877.5f - CHART_Y) * s - t.size.height / 2f))
        }
        scene.empty?.let { t ->
            val cx = ((GRID_L + GRID_R) / 2f - CHART_X) * s
            val cy = ((PLOT_TOP + PLOT_BASE) / 2f - CHART_Y) * s
            drawText(t, topLeft = Offset(cx - t.size.width / 2f, cy - t.size.height / 2f))
        }
    }
}

/** A curve's glow, line and lone-point dots, in mockup pixels. */
private fun DrawScope.drawCurve(c: Curve, color: Color) {
    val glow = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val line = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (p in c.lines) {
        drawPath(p, color.copy(alpha = 0.16f), style = glow)
        drawPath(p, color, style = line)
    }
    for (d in c.dots) {
        drawCircle(color.copy(alpha = 0.2f), 9f, d)
        drawCircle(color, 5f, d)
    }
}

private fun buildScene(series: ChartSeries?, measurer: TextMeasurer, density: Density): ChartScene {
    val fuel = series?.fuel
    val ticks = axisTicks(series?.energyMax ?: 30.0, if (series == null) 12.0 else series.fuelMax.takeIf { fuel != null })
    val rows = (ticks.n downTo 0).map { PLOT_BASE - (PLOT_BASE - PLOT_TOP) * it / ticks.n }
    val left = (ticks.n downTo 0).mapIndexed { i, k ->
        measurer.label(axisText(ticks.energyTop * k / ticks.n), AXIS_SIZE, EnergyAxis, density) to rows[i]
    }
    val right = if (series != null && fuel == null) emptyList()
    else (ticks.n downTo 0).mapIndexed { i, k ->
        measurer.label(axisText(ticks.fuelTop * k / ticks.n), AXIS_SIZE, FuelAxis, density) to rows[i]
    }
    val labels = series?.labels.orEmpty()
    val columns = ArrayList<Float>()
    val time = ArrayList<Pair<TextLayoutResult, Float>>()
    labels.forEachIndexed { i, l ->
        if (l.isEmpty()) return@forEachIndexed
        val x = xOf(i, labels.size)
        columns += x
        time += measurer.label(l, TIME_SIZE, TimeLabel, density) to x
    }
    val noTrips = series != null && series.energy.all { it == null } && (fuel == null || fuel.all { it == null })
    return ChartScene(
        ticks = ticks,
        rows = rows,
        columns = columns,
        energy = series?.let { curve(it.energy, ticks.energyTop) },
        fuel = fuel?.let { curve(it, ticks.fuelTop) },
        leftNumbers = left,
        rightNumbers = right,
        timeLabels = time,
        empty = if (noTrips) measurer.label("Нет поездок за период", 30f, NoTrips, density) else null,
    )
}
