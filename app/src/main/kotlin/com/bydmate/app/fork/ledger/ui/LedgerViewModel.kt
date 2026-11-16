package com.bydmate.app.fork.ledger.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.local.dao.ChargeDao
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.LedgerParking
import com.bydmate.app.fork.ledger.domain.LedgerPrefill
import com.bydmate.app.fork.ledger.domain.MonthRow
import com.bydmate.app.fork.ledger.domain.MonthTotals
import com.bydmate.app.fork.ledger.domain.MonthlySummary
import com.bydmate.app.fork.ledger.domain.Prefill
import com.bydmate.app.fork.ledger.domain.PrefillSource
import com.bydmate.app.fork.ledger.domain.RefuelSegment
import com.bydmate.app.fork.ledger.domain.RefuelSegments
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Named

data class LedgerUiState(
    /** False until the first build from the database finished. */
    val loaded: Boolean = false,
    /** Newest first, drafts included. */
    val refuels: List<RefuelEntity> = emptyList(),
    /** Confirmed refuels only (drafts never form a segment). */
    val segments: List<RefuelSegment> = emptyList(),
    /** Newest first. */
    val expenses: List<ExpenseEntity> = emptyList(),
    /** All categories, archived (hidden) ones included. */
    val categories: List<ExpenseCategoryEntity> = emptyList(),
    /** Newest month first. */
    val months: List<MonthRow> = emptyList(),
    val yearTotals: MonthTotals? = null,
    val totals: MonthTotals? = null,
    val currencySymbol: String = "",
    val parked: Boolean = true,
    val tankCapacityL: Double? = null,
    val detectThresholdPp: Int = LedgerRepository.DEFAULT_DETECT_THRESHOLD_PP,
    val mismatchThresholdPct: Double = LedgerRepository.DEFAULT_MISMATCH_THRESHOLD_PCT,
)

/** Settings › Expenses. Every write is ignored while the car is moving. */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repo: LedgerRepository,
    private val chargeDao: ChargeDao,
    private val tripDao: TripDao,
    private val settings: SettingsRepository,
    private val prefill: LedgerPrefill,
    @Named("ledgerCarData") private val carData: @JvmSuppressWildcards StateFlow<DiParsData?>,
) : ViewModel() {

    private val _state = MutableStateFlow(LedgerUiState(parked = LedgerParking.isParked(carData.value)))
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    /** Bumped after a ledger setting changes so the state re-reads it. */
    private val settingsVersion = MutableStateFlow(0)
    private var firstChargeTs: Long? = null
    private var firstChargeLoaded = false

    init {
        viewModelScope.launch {
            carData.map { LedgerParking.isParked(it) }.distinctUntilChanged().collect { p ->
                _state.update { it.copy(parked = p) }
            }
        }
        viewModelScope.launch {
            combine(
                repo.refuels.observeAll(),
                repo.expenses.observeAll(),
                repo.categories.observeActive(),
                settingsVersion,
            ) { r, e, _, _ -> r to e }
                // an upstream Room failure (e.g. the ledger file swapped by a restore) must not crash Main
                .catch { ex -> Log.w(TAG, "ledger flows failed: ${ex.message}", ex) }
                .collectLatest { (r, e) ->
                    try {
                        val built = build(r, e)
                        _state.update { built.copy(parked = it.parked) }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (ex: Exception) {
                        Log.w(TAG, "ledger state build failed: ${ex.message}", ex)
                    }
                }
        }
    }

    fun saveRefuel(e: RefuelEntity): Job = write {
        if (e.id == 0L) repo.refuels.insert(e) else repo.refuels.update(e)
    }

    fun deleteRefuel(e: RefuelEntity): Job = write { repo.refuels.delete(e) }

    /** Saves the edited draft as a confirmed refuel (same row). */
    fun confirmDraft(e: RefuelEntity): Job = write {
        if (e.id != 0L) repo.refuels.update(e.copy(status = "confirmed"))
    }

    fun saveExpense(e: ExpenseEntity): Job = write {
        if (e.id == 0L) repo.expenses.insert(e) else repo.expenses.update(e)
    }

    fun deleteExpense(e: ExpenseEntity): Job = write { repo.expenses.delete(e) }

    /** New category (id 0) goes last; otherwise rename / hide / show. */
    fun saveCategory(c: ExpenseCategoryEntity): Job = write {
        val name = c.name.trim()
        if (name.isEmpty()) return@write
        if (c.id == 0L) {
            val sort = (repo.categories.getAll().maxOfOrNull { it.sort } ?: -1) + 1
            repo.categories.insert(c.copy(name = name, sort = sort))
        } else {
            repo.categories.update(c.copy(name = name))
        }
    }

    fun setTankCapacity(liters: Double?): Job = write {
        repo.setTankCapacityL(liters?.takeIf { it > 0.0 })
        settingsVersion.update { it + 1 }
    }

    fun setThresholds(detectPp: Int, mismatchPct: Double): Job = write {
        if (detectPp > 0) repo.setDetectThresholdPp(detectPp)
        if (mismatchPct > 0.0) repo.setMismatchThresholdPct(mismatchPct)
        settingsVersion.update { it + 1 }
    }

    /** Odometer and car fuel counter for an entry at [entryTs]; NONE on any failure. */
    suspend fun prefillFor(entryTs: Long): Prefill = try {
        prefill.forEntry(entryTs, System.currentTimeMillis(), carData.value, repo.refuels.openDraft())
    } catch (ce: CancellationException) {
        throw ce
    } catch (ex: Exception) {
        Log.w(TAG, "ledger prefill failed: ${ex.message}", ex)
        Prefill(null, null, PrefillSource.NONE)
    }

    private fun write(block: suspend () -> Unit): Job = viewModelScope.launch {
        if (!LedgerParking.isParked(carData.value)) return@launch
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Exception) {
            Log.w(TAG, "ledger write failed: ${ex.message}", ex)
        }
    }

    private suspend fun build(refuels: List<RefuelEntity>, expenses: List<ExpenseEntity>): LedgerUiState {
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
            electricity = { electricity[it] ?: 0.0 },
            fuel = { fuel[it] ?: 0.0 },
            other = { other[it].orEmpty() },
            km = { km[it] ?: 0.0 },
            fuelCalculated = { calc[it] },
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
        const val TAG = "LedgerViewModel"
        /** Guards against a bogus (epoch-zero) date turning the summary into hundreds of months. */
        const val MAX_YEARS = 30L
    }
}
