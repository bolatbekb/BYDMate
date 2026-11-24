package com.bydmate.app.fork.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The «готово к установке» notice fires at most once per app launch. */
class LaunchNotifyGateTest {

    @Test
    fun `first call notifies, the rest do not`() {
        val gate = LaunchNotifyGate()
        assertTrue(gate.shouldNotify())
        assertFalse(gate.shouldNotify())
        assertFalse(gate.shouldNotify())
    }

    @Test
    fun `a fresh gate (new launch) notifies again`() {
        assertTrue(LaunchNotifyGate().shouldNotify())
        assertTrue(LaunchNotifyGate().shouldNotify())
    }
}
