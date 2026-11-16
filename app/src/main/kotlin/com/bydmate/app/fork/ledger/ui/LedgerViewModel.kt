package com.bydmate.app.fork.ledger.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.data.unlessDbFails
import com.bydmate.app.fork.ledger.domain.LedgerParking
import com.bydmate.app.fork.ledger.domain.LedgerPrefill
import com.bydmate.app.fork.ledger.domain.MonthRow
import com.bydmate.app.fork.ledger.domain.MonthTotals
import com.bydmate.app.fork.ledger.domain.Prefill
import com.bydmate.app.fork.ledger.domain.PrefillSource
import com.bydmate.app.fork.ledger.domain.RefuelSegment
import com.bydmate.app.fork.ledger.fuelio.FuelioImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val stateBuilder: LedgerStateBuilder,
    private val prefill: LedgerPrefill,
    @Named("ledgerCarData") private val carData: @JvmSuppressWildcards StateFlow<DiParsData?>,
    @ApplicationContext appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(LedgerUiState(parked = LedgerParking.isParked(carData.value)))
    val state: StateFlow<LedgerUiState> = _state.asStateFlow()

    /** Import from Fuelio: preview, then a write like any other. */
    internal val fuelio = LedgerFuelioImport(viewModelScope, FuelioImporter(repo), appContext, ::write)

    /** Bumped after a ledger setting changes so the state re-reads it. */
    private val settingsVersion = MutableStateFlow(0)

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
                    unlessDbFails({ Log.w(TAG, "ledger state build failed: ${it.message}", it) }) {
                        val built = stateBuilder.build(r, e)
                        _state.update { built.copy(parked = it.parked) }
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

    /**
     * Odometer and car fuel counter for an entry at [entryTs]; NONE when a database fails.
     * [useDraft] false: the open draft is a different refuel and must not lend its values.
     */
    suspend fun prefillFor(entryTs: Long, useDraft: Boolean = true): Prefill =
        unlessDbFails({ Log.w(TAG, "ledger prefill failed: ${it.message}", it) }) {
            val draft = if (useDraft) repo.refuels.openDraft() else null
            prefill.forEntry(entryTs, System.currentTimeMillis(), carData.value, draft)
        } ?: Prefill(null, null, PrefillSource.NONE)

    private fun write(block: suspend () -> Unit): Job = viewModelScope.launch {
        if (!LedgerParking.isParked(carData.value)) return@launch
        unlessDbFails({ Log.w(TAG, "ledger write failed: ${it.message}", it) }) { block() }
    }

    private companion object {
        const val TAG = "LedgerViewModel"
    }
}
