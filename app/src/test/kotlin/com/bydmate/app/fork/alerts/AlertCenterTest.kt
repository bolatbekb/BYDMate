package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.center.AlertCenter
import com.bydmate.app.fork.alerts.center.AlertSettings
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.ScalarUnit
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.source.AlertSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertCenterTest {

    private class FakeSource(override val id: AlertSourceId, private val list: List<Alert>) : AlertSource {
        override fun alerts(): Flow<List<Alert>> = flowOf(list)
    }

    private fun alert(id: AlertSourceId, severity: Severity, firstSeen: Long) =
        Alert(id, itemKey = "", severity, firstSeen, AlertValue.Scalar(1.0, ScalarUnit.VOLT))

    private val storageWarn = alert(AlertSourceId.STORAGE_FREE, Severity.WARNING, 10L)
    private val voltCrit = alert(AlertSourceId.VOLT_12V, Severity.CRITICAL, 20L)
    private val batteryWarn = alert(AlertSourceId.BATTERY_TEMP, Severity.WARNING, 5L)

    private fun sources() = setOf(
        FakeSource(AlertSourceId.STORAGE_FREE, listOf(storageWarn)),
        FakeSource(AlertSourceId.VOLT_12V, listOf(voltCrit)),
        FakeSource(AlertSourceId.BATTERY_TEMP, listOf(batteryWarn)),
    )

    @Test fun `active merges and sorts critical first then by block`() = runTest {
        val center = AlertCenter(sources(), AlertSettings(fakePrefs()), backgroundScope, now = { 100L })
        val active = center.active.first { it.size == 3 }
        assertEquals(listOf(AlertSourceId.VOLT_12V, AlertSourceId.STORAGE_FREE, AlertSourceId.BATTERY_TEMP), active.map { it.sourceId })
    }

    @Test fun `the settings filter removes a disabled source`() = runTest {
        val settings = AlertSettings(fakePrefs())
        settings.setEnabled(AlertSourceId.VOLT_12V, false)
        val center = AlertCenter(sources(), settings, backgroundScope, now = { 100L })
        val active = center.active.first { it.isNotEmpty() && it.none { a -> a.sourceId == AlertSourceId.VOLT_12V } }
        assertEquals(listOf(AlertSourceId.STORAGE_FREE, AlertSourceId.BATTERY_TEMP), active.map { it.sourceId })
    }

    @Test fun `history records each active alert once with the time it appeared`() = runTest {
        val center = AlertCenter(sources(), AlertSettings(fakePrefs()), backgroundScope, now = { 777L })
        center.active.first { it.size == 3 }
        assertEquals(3, center.history.value.size)
        center.history.value.forEach { assertEquals(777L, it.atMs) }
    }
}
