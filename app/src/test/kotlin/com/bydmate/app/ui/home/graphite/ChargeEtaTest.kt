package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeEtaTest {
    @Test fun computes() = assertEquals(53, ChargeEta.minutesToFull(true, 78, 30.0, -7.5))
    @Test fun notChargingHidden() = assertNull(ChargeEta.minutesToFull(false, 78, 30.0, 7.5))
    @Test fun fullHidden() = assertNull(ChargeEta.minutesToFull(true, 100, 30.0, 7.5))
    @Test fun tinyPowerHidden() = assertNull(ChargeEta.minutesToFull(true, 78, 30.0, 0.3))
    @Test fun noCapacityHidden() = assertNull(ChargeEta.minutesToFull(true, 78, null, 7.5))
}
