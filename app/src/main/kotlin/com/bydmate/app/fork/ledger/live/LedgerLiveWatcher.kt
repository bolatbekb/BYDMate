package com.bydmate.app.fork.ledger.live

import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.DetectorAction
import com.bydmate.app.fork.ledger.domain.DetectorState
import com.bydmate.app.fork.ledger.domain.RefuelDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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
    private var lastPersistTs = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            state = DetectorState.fromJson(runCatching { repo.detectorState() }.getOrNull())
            data.collect { d ->
                if (d == null || kind.value != VehicleKind.HYBRID) return@collect
                runCatching { process(d) }
            }
        }
    }

    private suspend fun process(d: DiParsData) {
        val ts = clock()
        val old = state
        val (next, action) = RefuelDetector.step(
            old, d.fuelPercent, d.fuelTotalL, d.mileage, ts, repo.detectThresholdPp(),
        )
        state = next
        var acted = false
        when (action) {
            is DetectorAction.None -> {}
            is DetectorAction.CreateDraft -> {
                acted = true
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
            is DetectorAction.UpdateDraft -> {
                val draft = repo.refuels.getById(action.draftId)
                if (draft == null || draft.status != "draft") {
                    // deleted or already confirmed by the user: leave this baseline alone
                    acted = true
                    state = next?.copy(draftId = null, dismissed = true)
                } else if (draft.tankPercentAfter != action.afterPercent) {
                    acted = true
                    val before = next?.baselinePercent ?: draft.tankPercentBefore ?: action.afterPercent
                    repo.refuels.update(
                        draft.copy(
                            tankPercentAfter = action.afterPercent,
                            liters = estimate(before, action.afterPercent) ?: draft.liters,
                        )
                    )
                }
            }
        }
        val baselineChanged = old?.let {
            it.baselinePercent != next?.baselinePercent || it.baselineOdo != next?.baselineOdo
        } ?: (next != null)
        if (acted || baselineChanged || ts - lastPersistTs >= PERSIST_EVERY_MS) {
            state?.let { repo.setDetectorState(it.toJson()) }
            lastPersistTs = ts
        }
    }

    private suspend fun estimate(before: Int, after: Int): Double? {
        val cap = repo.tankCapacityL() ?: return null
        return round((after - before) / 100.0 * cap * 100.0) / 100.0
    }

    private companion object {
        const val PERSIST_EVERY_MS = 30_000L
    }
}
