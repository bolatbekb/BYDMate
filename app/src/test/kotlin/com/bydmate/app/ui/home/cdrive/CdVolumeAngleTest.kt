package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Test

class CdVolumeAngleTest {
    @Test fun endsSitOnThePrintedZeroAndTen() {
        assertEquals(226.5f, cdVolumeAngle(0f), 0.001f)
        assertEquals(-47f, cdVolumeAngle(1f), 0.001f)
    }

    @Test fun middleMarkIsFive() {
        assertEquals(89.5f, cdVolumeAngle(0.5f), 0.001f)
    }

    @Test fun goesLinearlyBetweenMarks() {
        // halfway from 8 (4°) to 9 (−22.5°)
        assertEquals(-9.25f, cdVolumeAngle(0.85f), 0.001f)
    }

    @Test fun clampsOutOfRange() {
        assertEquals(226.5f, cdVolumeAngle(-0.3f), 0.001f)
        assertEquals(-47f, cdVolumeAngle(1.7f), 0.001f)
    }
}
