package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.source.Direction
import com.bydmate.app.fork.alerts.source.StorageAlertSource
import com.bydmate.app.fork.alerts.source.ThresholdSpec
import com.bydmate.app.fork.alerts.source.VolumeInfo
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GB = 1_000_000_000L

class StorageAlertSourceTest {

    private val spec = ThresholdSpec(Direction.LOW, warn = 10.0, crit = 3.0, clearMargin = 2.0)

    private fun vol(id: String, label: String, totalGb: Long, freeGb: Long, removable: Boolean = false) =
        VolumeInfo(id, label, removable, totalGb * GB, freeGb * GB)

    private fun source(vararg frames: List<VolumeInfo>) =
        StorageAlertSource(flowOf(*frames), specOf = { spec }, now = { 42L })

    @Test fun `each low volume fires with its own percent and GB line`() = runTest {
        val out = source(
            listOf(
                vol("primary", "Внутренняя", totalGb = 64, freeGb = 5),       // 7.8 % -> WARNING
                vol("card", "Карта", totalGb = 128, freeGb = 2, removable = true), // 1.6 % -> CRITICAL
                vol("data", "Данные", totalGb = 64, freeGb = 50),             // 78 % -> none
            ),
        ).alerts().toList()
        val byItem = out[0].associateBy { it.itemKey }
        assertEquals(2, out[0].size)
        assertEquals(Severity.WARNING, byItem.getValue("primary").severity)
        assertEquals(AlertValue.Storage("Внутренняя", 8, 5, 64), byItem.getValue("primary").value)
        assertEquals(Severity.CRITICAL, byItem.getValue("card").severity)
        assertEquals(AlertValue.Storage("Карта", 2, 2, 128), byItem.getValue("card").value)
        assertNull(byItem["data"])
    }

    @Test fun `a volume clears once it has the margin of free space`() = runTest {
        val out = source(
            listOf(vol("primary", "Внутренняя", totalGb = 100, freeGb = 7)),  // 7 % warns
            listOf(vol("primary", "Внутренняя", totalGb = 100, freeGb = 13)), // 13 % >= 12 clears
        ).alerts().toList()
        assertEquals(Severity.WARNING, out[0][0].severity)
        assertTrue(out[1].isEmpty())
    }
}
