package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.source.Direction
import com.bydmate.app.fork.alerts.source.ScalarAlertSource
import com.bydmate.app.fork.alerts.source.ThresholdSpec
import com.bydmate.app.fork.alerts.model.ScalarUnit
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScalarAlertSourceTest {

    private val spec = ThresholdSpec(Direction.LOW, warn = 11.8, crit = 11.4, clearMargin = 0.2)

    private fun source(vararg values: Double?) = ScalarAlertSource(
        AlertSourceId.VOLT_12V, spec, ScalarUnit.VOLT, flowOf(*values), now = { 1_000L },
    )

    @Test fun `no alert while above the warn line, then a warning`() = runTest {
        val out = source(12.6, 11.7).alerts().toList()
        assertEquals(emptyList<Any>(), out[0])
        assertEquals(1, out[1].size)
        assertEquals(Severity.WARNING, out[1][0].severity)
        assertEquals(AlertValue.Scalar(11.7, ScalarUnit.VOLT), out[1][0].value)
    }

    @Test fun `escalates to critical and keeps the first-seen time`() = runTest {
        val out = ScalarAlertSource(
            AlertSourceId.VOLT_12V, spec, ScalarUnit.VOLT, flowOf(11.7, 11.3), now = { 5_000L },
        ).alerts().toList()
        assertEquals(Severity.WARNING, out[0][0].severity)
        assertEquals(Severity.CRITICAL, out[1][0].severity)
        assertEquals(5_000L, out[1][0].firstSeenMs) // stamped on the first fire, held on escalation
    }

    @Test fun `a null reading clears the alert`() = runTest {
        val out = source(11.3, null).alerts().toList()
        assertEquals(Severity.CRITICAL, out[0][0].severity)
        assertTrue(out[1].isEmpty())
    }

    @Test fun `holds through the clear band and clears past the margin`() = runTest {
        // 11.7 warns, 11.9 is in the band (<12.0) so it is held, 12.1 clears.
        val out = source(11.7, 11.9, 12.1).alerts().toList()
        assertEquals(Severity.WARNING, out[0][0].severity)
        assertEquals(Severity.WARNING, out[1][0].severity)
        assertTrue(out[2].isEmpty())
    }
}
