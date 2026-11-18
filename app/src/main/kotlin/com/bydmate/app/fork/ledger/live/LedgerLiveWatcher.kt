package com.bydmate.app.fork.ledger.live

import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.DetectorAction
import com.bydmate.app.fork.ledger.domain.DetectorState
import com.bydmate.app.fork.ledger.domain.RefuelDetector
import com.bydmate.app.fork.ledger.domain.TankReading
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    private val started = AtomicBoolean(false)
    private var state: DetectorState? = null
    private var loaded = false
    private var lastPersisted: String? = null
    private var thresholdPp = 0
    private var thresholdAt = Long.MIN_VALUE

    @Suppress("TooGenericExceptionCaught") // why: a background step on every live sample; a failure is logged and the next sample retries, never fatal
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // the detector reads only these four values: an identical sample needs no Room read or write
            data.distinctUntilChangedBy { d ->
                if (d == null) null else listOf(kind.value, d.fuelPercent, d.fuelTotalL, d.mileage)
            }.collect { d ->
                if (d == null || kind.value != VehicleKind.HYBRID) return@collect
                try {
                    if (!loaded) {
                        // a failed read must not start a fresh baseline over the good persisted one
                        val stored = repo.detectorState()
                        state = DetectorState.fromJson(stored)
                        lastPersisted = state?.toJson()
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
        val (next, action) = RefuelDetector.step(
            state, TankReading(d.fuelPercent, d.fuelTotalL, d.mileage, ts), threshold(),
        )
        if (action is DetectorAction.None) state = next
        when (action) {
            is DetectorAction.None -> Unit
            is DetectorAction.CreateDraft -> createDraft(action)
            is DetectorAction.UpdateDraft -> refreshDraft(action, next)
        }
        val json = state?.toJson()
        if (json != null && json != lastPersisted) {
            repo.setDetectorState(json)
            lastPersisted = json
        }
    }

    /** The detection threshold is a setting: read it at most every minute (a failed read is not cached). */
    private suspend fun threshold(): Int {
        val now = elapsed()
        if (thresholdAt == Long.MIN_VALUE || abs(now - thresholdAt) >= THRESHOLD_TTL_MS) {
            thresholdPp = repo.detectThresholdPp()
            thresholdAt = now
        }
        return thresholdPp
    }

    private suspend fun createDraft(action: DetectorAction.CreateDraft) {
        val open = repo.refuels.openDraft()
        if (open != null && isDraftOf(open, action.before)) {
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

    /** [open] is the draft this watcher detected from [before]: same tank level, same place. */
    private fun isDraftOf(open: RefuelEntity, before: DetectorState): Boolean {
        val od = open.odometerKm ?: return false
        val detected = open.status == "draft" && open.source == "detected"
        return detected && open.tankPercentBefore == before.baselinePercent && abs(od - before.baselineOdo) < ADOPT_KM
    }

    /** Writes the draft, or finds it gone (then the baseline is dismissed). */
    private suspend fun refreshDraft(action: DetectorAction.UpdateDraft, next: DetectorState?) {
        val changed = updateDraft(action.draftId, action.afterPercent, next?.baselinePercent)
        if (changed == null) state = next?.copy(draftId = null, dismissed = true)
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
        const val THRESHOLD_TTL_MS = 60_000L
    }
}
