package com.bydmate.app.fork.cells.verdict

import kotlin.math.roundToInt

private const val DAY_MS = 86_400_000L
private const val MIN_AGE_DAYS = 14
private const val MAX_AGE_DAYS = 90
private const val MIN_POINTS = 5
private const val MAX_POINTS = 10

/** Personal base (R14): median spread of the oldest 5-10 honest rest points of the same row aged 14-90 days. */
object Baseline {
    fun of(current: MeasurementInput, row: ZoneRow, points: List<SpreadPoint>): Int? {
        val picked = points
            .filter { it.id != current.id && it.row == row && !it.cold && !it.preliminary }
            .filter { current.ts - it.ts in MIN_AGE_DAYS * DAY_MS..MAX_AGE_DAYS * DAY_MS }
            .sortedBy { it.ts }
            .take(MAX_POINTS)
        if (picked.size < MIN_POINTS) return null
        val s = picked.map { it.spreadMv }.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else ((s[n / 2 - 1] + s[n / 2]) / 2.0).roundToInt()
    }
}
