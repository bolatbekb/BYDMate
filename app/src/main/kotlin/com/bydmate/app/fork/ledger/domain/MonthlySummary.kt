package com.bydmate.app.fork.ledger.domain

import java.time.YearMonth

data class MonthRow(
    val month: YearMonth,
    val electricity: Double,
    val fuel: Double,
    val other: Double,
    val byCategory: Map<Long, Double>,
    val km: Double,
    /** Calculated fuel cost; informational, not part of [total]. */
    val fuelCalculated: Double?,
) {
    val total: Double get() = electricity + fuel + other
    val costPerKm: Double? get() = if (km > 0.0) total / km else null
}

data class MonthTotals(
    val electricity: Double,
    val fuel: Double,
    val other: Double,
    val byCategory: Map<Long, Double>,
    val km: Double,
    val fuelCalculated: Double?,
) {
    val total: Double get() = electricity + fuel + other
    val costPerKm: Double? get() = if (km > 0.0) total / km else null
}

object MonthlySummary {
    fun rows(
        months: List<YearMonth>,
        electricity: (YearMonth) -> Double,
        fuel: (YearMonth) -> Double,
        other: (YearMonth) -> Map<Long, Double>,
        km: (YearMonth) -> Double,
        fuelCalculated: (YearMonth) -> Double?,
    ): List<MonthRow> = months.map { m ->
        val cats = other(m)
        MonthRow(m, electricity(m), fuel(m), cats.values.sum(), cats, km(m), fuelCalculated(m))
    }

    fun totals(rows: List<MonthRow>): MonthTotals {
        val merged = mutableMapOf<Long, Double>()
        rows.forEach { r -> r.byCategory.forEach { (k, v) -> merged[k] = (merged[k] ?: 0.0) + v } }
        val calc = rows.mapNotNull { it.fuelCalculated }
        return MonthTotals(
            electricity = rows.sumOf { it.electricity },
            fuel = rows.sumOf { it.fuel },
            other = rows.sumOf { it.other },
            byCategory = merged,
            km = rows.sumOf { it.km },
            fuelCalculated = if (calc.isEmpty()) null else calc.sum(),
        )
    }
}
