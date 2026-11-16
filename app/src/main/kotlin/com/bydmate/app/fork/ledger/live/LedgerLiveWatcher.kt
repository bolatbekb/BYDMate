package com.bydmate.app.fork.ledger.live

import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.DetectorAction
import com.bydmate.app.fork.ledger.domain.DetectorState
import com.bydmate.app.fork.ledger.domain.RefuelDetector
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.round

/**
 * Watches the live tank level on a HYBRID car and keeps a refuel draft for a detected jump.
 * The baseline is persisted so it survives the head unit dying at ignition-off.
 */
class LedgerLiveWatcher(
    private val repo: LedgerRepository,
    private val data: StateFlow<DiParsData?>,
    private val kind: StateFlow<VehicleKind>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val started = AtomicBoolean(false)
    private var state: DetectorState? = null
    private var loaded = false
    private var lastPersistTs = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            data.collect { d ->
                if (d == null || kind.value != VehicleKind.HYBRID) return@collect
                try {
                    if (!loaded) {
                        // a failed read must not start a fresh baseline over the good persisted one
                        state = DetectorState.fromJson(repo.detectorState())
                        loaded = true
                    }
                    process(d)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "ledger watcher step failed: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun process(d: DiParsData) {
        val ts = clock()
        val old = state
        val (next, action) = RefuelDetector.step(
            old, d.fuelPercent, d.fuelTotalL, d.mileage, ts, repo.detectThresholdPp(),
        )
        if (action is DetectorAction.None) state = next
        var acted = false
        when (action) {
            is DetectorAction.None -> {}
            is DetectorAction.CreateDraft -> {
                acted = true
                val open = repo.refuels.openDraft()
                val od = open?.odometerKm
                if (open != null && od != null && open.status == "draft" && open.source == "detected" &&
                    open.tankPercentBefore == action.before.baselinePercent &&
                    abs(od - action.before.baselineOdo) < ADOPT_KM
                ) {
                    // insert happened but the state never reached disk: adopt, don't duplicate
                    state = action.before.copy(draftId = open.id)
                    updateDraft(open.id, action.afterPercent, action.before.baselinePercent)
                } else {
                    val id = repo.refuels.insert(
                        RefuelEntity(
                            date = action.ts,
                            odometerKm = action.before.baselineOdo,
                            liters = estimate(action.before.baselinePercent, action.afterPercent) ?: 0.0,
                            totalCost = null, pricePerLiter = null, fullTank = false,
                            carFuelTotalL = action.before.baselineFuelTotal,
                            tankPercentBefore = action.before.baselinePercent,
                            tankPercentAfter = action.afterPercent,
                            lat = null, lon = null, place = null, note = null,
                            source = "detected", status = "draft", importKey = null,
                        )
                    )
                    state = action.before.copy(draftId = id)
                }
            }
            is DetectorAction.UpdateDraft -> {
                val changed = updateDraft(action.draftId, action.afterPercent, next?.baselinePercent)
                if (changed == null) {
                    acted = true
                    state = next?.copy(draftId = null, dismissed = true)
                } else if (changed) acted = true
            }
        }
        val baselineChanged = old?.let {
            it.baselinePercent != state?.baselinePercent || it.baselineOdo != state?.baselineOdo
        } ?: (state != null)
        if (acted || baselineChanged || ts - lastPersistTs >= PERSIST_EVERY_MS) {
            state?.let { repo.setDetectorState(it.toJson()) }
            lastPersistTs = ts
        }
    }

    /**
     * Touches only tank_percent_after (and liters while it is 0 or still our own estimate).
     * Returns null when the draft is gone or no longer a draft, true when written, false when unchanged.
     */
    private suspend fun updateDraft(id: Long, after: Int, baselinePercent: Int?): Boolean? {
        val draft = repo.refuels.getById(id)
        if (draft == null || draft.status != "draft") return null
        if (draft.tankPercentAfter == after) return false
        val before = baselinePercent ?: draft.tankPercentBefore ?: after
        val newEst = estimate(before, after)
        val prevEst = draft.tankPercentAfter?.let { estimate(before, it) }
        val rows = if (newEst != null) {
            val guarded = repo.refuels.updateDraftPercentAndLiters(id, after, newEst, prevEst ?: -1.0)
            if (guarded > 0) guarded else repo.refuels.updateDraftPercent(id, after)
        } else {
            repo.refuels.updateDraftPercent(id, after)
        }
        return if (rows == 0) null else true
    }

    private suspend fun estimate(before: Int, after: Int): Double? {
        val cap = repo.tankCapacityL() ?: return null
        return round((after - before) / 100.0 * cap * 100.0) / 100.0
    }

    private companion object {
        const val TAG = "LedgerLiveWatcher"
        const val ADOPT_KM = 2.0
        const val PERSIST_EVERY_MS = 30_000L
    }
}
