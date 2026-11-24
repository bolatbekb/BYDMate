package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.center.AlertSettings
import com.bydmate.app.fork.alerts.model.Alert
import com.bydmate.app.fork.alerts.model.AlertSourceId
import com.bydmate.app.fork.alerts.model.AlertValue
import com.bydmate.app.fork.alerts.model.ScalarUnit
import com.bydmate.app.fork.alerts.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsTest {

    private fun alert(id: AlertSourceId, severity: Severity) =
        Alert(id, itemKey = "", severity, firstSeenMs = 0L, value = AlertValue.Scalar(1.0, ScalarUnit.VOLT))

    @Test fun `defaults are all on at INFO and storage warn 10`() {
        val settings = AlertSettings(fakePrefs())
        val config = settings.config.value
        assertEquals(AlertSourceId.entries.size, config.sources.size)
        config.sources.values.forEach {
            assertTrue(it.enabled)
            assertEquals(Severity.INFO, it.minSeverity)
        }
        assertEquals(AlertSettings.STORAGE_WARN_DEFAULT, config.storageWarnPercent)
        // Everything passes by default, at any level.
        assertTrue(settings.passes(alert(AlertSourceId.VOLT_12V, Severity.INFO)))
    }

    @Test fun `a disabled source passes nothing`() {
        val settings = AlertSettings(fakePrefs())
        settings.setEnabled(AlertSourceId.VOLT_12V, false)
        assertFalse(settings.passes(alert(AlertSourceId.VOLT_12V, Severity.CRITICAL)))
        // Another source is unaffected.
        assertTrue(settings.passes(alert(AlertSourceId.STORAGE_FREE, Severity.INFO)))
    }

    @Test fun `minimum severity drops lower levels only`() {
        val settings = AlertSettings(fakePrefs())
        settings.setMinSeverity(AlertSourceId.TYRE_PRESSURE, Severity.WARNING)
        assertFalse(settings.passes(alert(AlertSourceId.TYRE_PRESSURE, Severity.INFO)))
        assertTrue(settings.passes(alert(AlertSourceId.TYRE_PRESSURE, Severity.WARNING)))
        assertTrue(settings.passes(alert(AlertSourceId.TYRE_PRESSURE, Severity.CRITICAL)))
    }

    @Test fun `settings survive a reload from the same prefs and storage warn is coerced`() {
        val backing = mutableMapOf<String, Any?>()
        AlertSettings(fakePrefs(backing)).apply {
            setMinSeverity(AlertSourceId.BATTERY_TEMP, Severity.CRITICAL)
            setStorageWarnPercent(999) // out of range -> clamped to max
        }
        val reloaded = AlertSettings(fakePrefs(backing)).config.value
        assertEquals(Severity.CRITICAL, reloaded.sources.getValue(AlertSourceId.BATTERY_TEMP).minSeverity)
        assertEquals(AlertSettings.STORAGE_WARN_MAX, reloaded.storageWarnPercent)
    }
}
