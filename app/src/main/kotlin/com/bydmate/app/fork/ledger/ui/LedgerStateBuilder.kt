package com.bydmate.app.fork.ledger.ui

import com.bydmate.app.data.local.dao.ChargeDao
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.MonthSources
import com.bydmate.app.fork.ledger.domain.MonthlySummary
import com.bydmate.app.fork.ledger.domain.RefuelSegments
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Builds the Settings › Expenses state from the ledger and the author's charges and trips. One per
 * view model: it keeps the first charge's date after the first read.
 */
class LedgerStateBuilder @Inject constructor(
    private val repo: LedgerRepository,
    private val chargeDao: ChargeDao,
    private val tripDao: TripDao,
    private val settings: SettingsRepository,
) {
    private var firstChargeTs: Long? = null
    private var firstChargeLoaded = false

    suspend fun build(refuels: List<RefuelEntity>, expenses: List<ExpenseEntity>): LedgerUiState {
        val months = monthRange(refuels, expenses)
        val electricity = HashMap<YearMonth, Double>()
        val fuel = HashMap<YearMonth, Double>()
        val other = HashMap<YearMonth, Map<Long, Double>>()
        val km = HashMap<YearMonth, Double>()
        val calc = HashMap<YearMonth, Double?>()
        for (m in months) {
            val from = m.startMs()
            val to = m.plusMonths(1).startMs()
            // author queries take an inclusive upper bound, ledger ones a half-open range
            electricity[m] = chargeDao.getPeriodSummary(from, to - 1).totalCost
            fuel[m] = repo.refuels.sumCost(from, to)
            other[m] = repo.expenses.sumByCategory(from, to).associate { it.categoryId to it.total }
            km[m] = tripDao.getPeriodSummary(from, to - 1).totalKm
            val costs = tripDao.getWithEnergyInRange(from, to - 1).mapNotNull { it.fuelCost }
            calc[m] = if (costs.isEmpty()) null else costs.sum()
        }
        val rows = MonthlySummary.rows(
            months,
            MonthSources(
                electricity = { electricity[it] ?: 0.0 },
                fuel = { fuel[it] ?: 0.0 },
                other = { other[it].orEmpty() },
                km = { km[it] ?: 0.0 },
                fuelCalculated = { calc[it] },
            ),
        )
        val year = YearMonth.now().year
        val yearRows = rows.filter { it.month.year == year }
        return LedgerUiState(
            loaded = true,
            refuels = refuels,
            segments = RefuelSegments.build(refuels),
            expenses = expenses,
            categories = repo.categories.getAll(),
            months = rows,
            yearTotals = if (yearRows.isEmpty()) null else MonthlySummary.totals(yearRows),
            totals = if (rows.isEmpty()) null else MonthlySummary.totals(rows),
            currencySymbol = settings.getCurrency().symbol,
            tankCapacityL = repo.tankCapacityL(),
            detectThresholdPp = repo.detectThresholdPp(),
            mismatchThresholdPct = repo.mismatchThresholdPct(),
        )
    }

    /** From the earliest refuel, expense or charge to the current month, newest first. */
    private suspend fun monthRange(refuels: List<RefuelEntity>, expenses: List<ExpenseEntity>): List<YearMonth> {
        if (!firstChargeLoaded) {
            firstChargeTs = chargeDao.getInRangeAsc(0L, System.currentTimeMillis()).firstOrNull()?.startTs
            firstChargeLoaded = true
        }
        val dates = refuels.map { it.date } + expenses.map { it.date } + listOfNotNull(firstChargeTs)
        val first = dates.minOrNull() ?: return emptyList()
        val now = YearMonth.now()
        val last = maxOf(now, dates.max().toYearMonth())
        val start = maxOf(first.toYearMonth(), now.minusYears(MAX_YEARS))
        val out = ArrayList<YearMonth>()
        var m = last
        while (!m.isBefore(start)) {
            out += m
            m = m.minusMonths(1)
        }
        return out
    }

    private fun YearMonth.startMs(): Long = atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun Long.toYearMonth(): YearMonth = YearMonth.from(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

    private companion object {
        /** Guards against a bogus (epoch-zero) date turning the summary into hundreds of months. */
        const val MAX_YEARS = 30L
    }
}
