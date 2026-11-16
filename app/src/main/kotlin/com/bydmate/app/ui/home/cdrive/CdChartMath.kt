package com.bydmate.app.ui.home.cdrive

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

/**
 * What the chart shows on the fuel side. The right axis and the printed «л/100 км» belong to the
 * car ([carHasFuel], from its live data), not to the period: a hybrid keeps them on a day without
 * trips. Only the yellow line depends on the period having fuel data.
 */
internal data class FuelSide(val rightAxis: Boolean, val coverTitle: Boolean, val line: Boolean)

internal fun fuelSide(carHasFuel: Boolean, series: ChartSeries?): FuelSide =
    FuelSide(rightAxis = carHasFuel, coverTitle = !carHasFuel, line = carHasFuel && series?.fuel != null)

/** "10", "2.5": an axis number without a zero fraction. */
internal fun axisText(v: Double): String =
    if (abs(v - Math.round(v)) < 1e-6) Math.round(v).toString() else String.format(Locale.US, "%.1f", v)
