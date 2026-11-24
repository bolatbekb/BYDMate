package com.bydmate.app.fork.update

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.service.TrackingService

/**
 * Thin fork adapter that reads «на стоянке» off the SAME live signal the automation park gate
 * uses. The engine's park gate reads `TrackingService.lastData.value` (via its `liveData` seam)
 * and checks `gear == 1`; this reads the same flow and additionally requires speed 0 — see
 * [ParkState]. fork-local.
 *
 * [liveData] is a test seam, exactly like `AutomationEngine.liveData`, so the gate can be driven
 * from a fake poll in unit tests without touching the service.
 */
class ParkStateReader(
    private val liveData: () -> DiParsData? = { TrackingService.lastData.value },
) {
    fun isParked(): Boolean {
        val data = liveData()
        return ParkState.isParked(data?.gear, data?.speed)
    }
}
