package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Test

class GearLetterTest {
    @Test fun knownGears() {
        assertEquals("P", gearLetter(1))
        assertEquals("R", gearLetter(2))
        assertEquals("N", gearLetter(3))
        assertEquals("D", gearLetter(4))
    }

    @Test fun unknownGivesDash() {
        assertEquals("—", gearLetter(null))
        assertEquals("—", gearLetter(5))
        assertEquals("—", gearLetter(0))
    }
}
