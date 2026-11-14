package com.bydmate.app.data.loop

import com.bydmate.app.data.local.EnergyDataReader
import com.bydmate.app.data.local.dao.LastStateDao
import com.bydmate.app.data.nativestack.ParsReader
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/** With the head-unit screen off a drive is polled at the parked cadence; nothing else changes. */
class ScreenOffCadenceTest {
    private val cadence = CadenceConfig.default()
    private fun loop(screenOn: Boolean) = SharedAdaptiveLoop(
        parsReader = mockk<ParsReader>(relaxed = true),
        lastStateDao = mockk<LastStateDao>(relaxed = true),
        energyDataReader = mockk<EnergyDataReader>(relaxed = true),
        cadence = cadence,
        screenOn = { screenOn },
    )

    @Test fun `screen on keeps every cadence`() {
        val l = loop(screenOn = true)
        LoopState.entries.forEach { assertEquals(cadence.intervalFor(it), l.nextInterval(it)) }
    }

    @Test fun `screen off slows only the drive`() {
        val l = loop(screenOn = false)
        assertEquals(cadence.parkedMs, l.nextInterval(LoopState.DRIVE))
        assertEquals(cadence.chargeMs, l.nextInterval(LoopState.CHARGE))
        assertEquals(cadence.parkedMs, l.nextInterval(LoopState.PARKED))
        assertEquals(cadence.idleMs, l.nextInterval(LoopState.IDLE))
    }
}
