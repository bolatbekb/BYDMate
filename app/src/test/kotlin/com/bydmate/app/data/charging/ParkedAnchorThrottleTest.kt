package com.bydmate.app.data.charging

import com.bydmate.app.data.charging.AutoserviceChargingDetector.Companion.anchorFresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parked anchor is rewritten only when it would otherwise drift (perf: not on every tick). */
class ParkedAnchorThrottleTest {
    private val now = 1_000_000L
    private fun prev(soc: Int? = 70, km: Float? = 1000f, ts: Long = now - 5_000) =
        ChargingStateStore.State(socPercent = soc, mileageKm = km, capacityKwh = null, ts = ts)

    @Test fun `same soc, odometer still and fresh anchor - skip the write`() =
        assertTrue(anchorFresh(prev(), 70, 1000.1, now))

    @Test fun `soc changed - write`() = assertFalse(anchorFresh(prev(), 69, 1000.0, now))

    @Test fun `odometer moved 0_2 km or more - write`() = assertFalse(anchorFresh(prev(), 70, 1000.25, now))

    @Test fun `anchor a minute old - write`() = assertFalse(anchorFresh(prev(ts = now - 60_000), 70, 1000.0, now))

    @Test fun `clock went backwards - write`() = assertFalse(anchorFresh(prev(ts = now + 1), 70, 1000.0, now))

    @Test fun `unknown mileage on either side - write`() {
        assertFalse(anchorFresh(prev(km = null), 70, 1000.0, now))
        assertFalse(anchorFresh(prev(), 70, null, now))
    }
}
