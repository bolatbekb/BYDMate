package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.demo.DemoAlertSource
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.Severity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAlertSourceTest {

    @Test fun `the seed shows every level and a storage alert, with real source ids`() = runTest {
        val seed = DemoAlertSource(now = { 10_000_000L }).alerts().first()
        // All three levels appear, so the list and the min-severity filter can be seen.
        assertTrue(seed.any { it.severity == Severity.CRITICAL })
        assertTrue(seed.any { it.severity == Severity.WARNING })
        assertTrue(seed.any { it.severity == Severity.INFO })
        // A storage alert carries the GB line.
        val storage = seed.first { it.sourceId == AlertSourceId.STORAGE_FREE && it.severity == Severity.CRITICAL }
        assertTrue(storage.value is AlertValue.Storage)
        // Every seeded alert first appeared before "now" (so "appeared N ago" is sensible).
        assertTrue(seed.all { it.firstSeenMs < 10_000_000L })
        // Real ids across several blocks, so the settings filter works on them.
        assertEquals(
            setOf(AlertSourceId.STORAGE_FREE, AlertSourceId.VOLT_12V, AlertSourceId.TYRE_PRESSURE, AlertSourceId.BATTERY_TEMP, AlertSourceId.INSULATION),
            seed.map { it.sourceId }.toSet(),
        )
    }
}
