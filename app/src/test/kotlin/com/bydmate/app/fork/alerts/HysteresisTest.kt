package com.bydmate.app.fork.alerts

import com.bydmate.app.fork.alerts.model.Severity
import com.bydmate.app.fork.alerts.source.Direction
import com.bydmate.app.fork.alerts.source.Hysteresis
import com.bydmate.app.fork.alerts.source.ThresholdSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HysteresisTest {

    // Storage-like: low is the problem. warn 10 %, crit 3 %, clear at 12 %.
    private val low = ThresholdSpec(Direction.LOW, warn = 10.0, crit = 3.0, clearMargin = 2.0)

    // Temperature-like: high is the problem. warn 80, crit 95, clear at 75.
    private val high = ThresholdSpec(Direction.HIGH, warn = 80.0, crit = 95.0, clearMargin = 5.0)

    @Test fun `low fires warning then critical`() {
        assertNull(Hysteresis.evaluate(low, 15.0, firing = false))
        assertEquals(Severity.WARNING, Hysteresis.evaluate(low, 8.0, firing = false))
        assertEquals(Severity.CRITICAL, Hysteresis.evaluate(low, 3.0, firing = false))
        assertEquals(Severity.CRITICAL, Hysteresis.evaluate(low, 2.0, firing = false))
    }

    @Test fun `low holds in the clear band and clears past the margin`() {
        // Between warn (10) and clear (12) while firing: held as WARNING, does not flap off.
        assertEquals(Severity.WARNING, Hysteresis.evaluate(low, 11.0, firing = true))
        // At or above the clear line: gone.
        assertNull(Hysteresis.evaluate(low, 12.0, firing = true))
        // The same reading when nothing was firing: still nothing (no new fire in the band).
        assertNull(Hysteresis.evaluate(low, 11.0, firing = false))
    }

    @Test fun `high fires warning then critical and holds then clears`() {
        assertNull(Hysteresis.evaluate(high, 70.0, firing = false))
        assertEquals(Severity.WARNING, Hysteresis.evaluate(high, 85.0, firing = false))
        assertEquals(Severity.CRITICAL, Hysteresis.evaluate(high, 95.0, firing = false))
        // Firing, dropped into the clear band (75..80): held as WARNING.
        assertEquals(Severity.WARNING, Hysteresis.evaluate(high, 78.0, firing = true))
        // At the clear line (75): gone.
        assertNull(Hysteresis.evaluate(high, 75.0, firing = true))
    }
}
