package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.ScalarUnit
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.source.Direction
import com.bydmate.app.fork.alerts.source.ThresholdSpec
import com.bydmate.app.fork.alerts.source.WheelAlertSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelAlertSourceTest {

    private val spec = ThresholdSpec(Direction.LOW, warn = 180.0, crit = 150.0, clearMargin = 20.0)

    private fun source(vararg frames: List<Double?>) = WheelAlertSource(
        AlertSourceId.TYRE_PRESSURE, spec, ScalarUnit.KPA, flowOf(*frames), now = { 7L },
    )

    @Test fun `only the low wheel fires, labelled by its position`() = runTest {
        val out = source(listOf(250.0, 250.0, 170.0, 250.0)).alerts().toList()
        assertEquals(1, out[0].size)
        val alert = out[0][0]
        assertEquals(AlertSourceId.TYRE_PRESSURE, alert.sourceId)
        assertEquals("RL", alert.itemKey)
        assertEquals(Severity.WARNING, alert.severity)
    }

    @Test fun `two wheels fire independently and a null wheel is silent`() = runTest {
        val out = source(listOf(140.0, 250.0, null, 175.0)).alerts().toList()
        val keys = out[0].associateBy { it.itemKey }
        assertEquals(Severity.CRITICAL, keys.getValue("FL").severity)
        assertEquals(Severity.WARNING, keys.getValue("RR").severity)
        assertTrue(keys["RL"] == null) // null reading: no alert
        assertTrue(keys["FR"] == null) // fine
    }

    @Test fun `a wheel clears with the margin while another stays`() = runTest {
        val out = source(
            listOf(170.0, 250.0, 250.0, 250.0), // FL warns
            listOf(205.0, 250.0, 250.0, 250.0), // FL back above warn+margin (200): clears
        ).alerts().toList()
        assertEquals("FL", out[0][0].itemKey)
        assertTrue(out[1].isEmpty())
    }
}
