package com.bydmate.app.fork.hybrid

import com.bydmate.app.data.local.dao.TripCounterStats
import com.bydmate.app.data.trips.TripCounterMath
import com.bydmate.app.data.trips.TripResetState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The TRIP counter: a trip that gave energy back to the pack (price 0) never adds money when a reset cuts it. */
class HybridTripCounterTest {

    // The trip the reset happened inside gave 0.9 kWh back and cost 0; a later trip drew 2.0 kWh for 0.40.
    private val stats = TripCounterStats(
        totalKm = 30.0, totalKwh = 1.1, totalCost = 0.40, drivingKwh = 1.1, idleKwh = 0.0, tripCount = 2,
        drivingMs = 20_000L, straddlingKm = 6.0, straddlingKwh = -0.9, straddlingMs = 10_000L, straddlingTripCount = 1,
        landedSessionKm = 0.0, landedSessionKwh = 0.0, landedSessionMs = 0L,
    )

    @Test fun `a reset inside a gain trip leaves the money as it was`() {
        val reset = TripResetState(resetTs = 5_000L, corrKm = 2.0, corrKwh = 0.5, corrMs = 4_000L)
        val ui = TripCounterMath.compute(stats, reset, null, null, null, false, 60_000L, 0.2)
        assertEquals(0.40, ui.cost, 1e-9)
    }

    @Test fun `a degraded reset that drops the gain trip whole leaves the money as it was`() {
        val reset = TripResetState(resetTs = 5_000L, corrKm = 0.0, corrKwh = 0.0, corrMs = 0L, excludeStraddling = true)
        val ui = TripCounterMath.compute(stats, reset, null, null, null, false, 60_000L, 0.2)
        assertEquals(0.40, ui.cost, 1e-9)
    }

    @Test fun `an EV reset inside a consuming trip still takes its part off`() {
        val ev = stats.copy(totalKwh = 6.0, totalCost = 1.2, drivingKwh = 6.0, straddlingKwh = 4.0)
        val reset = TripResetState(resetTs = 5_000L, corrKm = 2.0, corrKwh = 1.0, corrMs = 4_000L)
        val ui = TripCounterMath.compute(ev, reset, null, null, null, false, 60_000L, 0.2)
        assertEquals(1.0, ui.cost, 1e-9)
    }
}
