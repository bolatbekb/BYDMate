package com.bydmate.app.fork.hybrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HybridEnergyTest {

    @Test fun `a gain within the pack and the SOC rise is kept`() {
        assertEquals(-0.9, HybridEnergy.plausibleGain(-0.9, 60, 63, 31.8)!!, 1e-12)
        assertEquals(-2.0, HybridEnergy.plausibleGain(-2.0, 40, 47, 31.8)!!, 1e-12)
    }

    @Test fun `without a SOC pair only the pack bounds the gain`() {
        assertEquals(-5.0, HybridEnergy.plausibleGain(-5.0, null, null, 31.8)!!, 1e-12)
        assertNull(HybridEnergy.plausibleGain(-31.9, null, 63, 31.8))
    }

    @Test fun `regen ticks the SOC by one at most - a small gain with a flat SOC is kept`() {
        assertEquals(-0.1, HybridEnergy.plausibleGain(-0.1, 87, 87, 31.8)!!, 1e-12)
        assertEquals(-0.1, HybridEnergy.plausibleGain(-0.1, 88, 87, 31.8)!!, 1e-12)
    }

    @Test fun `a gain while the SOC fell, or far beyond its rise, is a glitch`() {
        assertNull(HybridEnergy.plausibleGain(-1.0, 80, 70, 72.9))
        assertNull(HybridEnergy.plausibleGain(-10.0, 60, 61, 31.8))
    }

    @Test fun `no gain at all - zero, a draw, a broken number`() {
        assertNull(HybridEnergy.plausibleGain(0.0, 60, 60, 31.8))
        assertNull(HybridEnergy.plausibleGain(1.5, 60, 56, 31.8))
        assertNull(HybridEnergy.plausibleGain(Double.NaN, 60, 63, 31.8))
        assertNull(HybridEnergy.plausibleGain(Double.NEGATIVE_INFINITY, 60, 63, 31.8))
    }

    @Test fun `an unset capacity falls back to a hundred kWh`() {
        assertEquals(-40.0, HybridEnergy.plausibleGain(-40.0, null, null, 0.0)!!, 1e-12)
        assertNull(HybridEnergy.plausibleGain(-2981.2, null, null, 0.0))
    }

    @Test fun `only what left the pack is paid for`() {
        assertEquals(0.0, HybridEnergy.billableKwh(-0.9), 0.0)
        assertEquals(1.8, HybridEnergy.billableKwh(1.8), 0.0)
        assertEquals(0.0, HybridEnergy.billableKwh(0.0), 0.0)
    }
}
