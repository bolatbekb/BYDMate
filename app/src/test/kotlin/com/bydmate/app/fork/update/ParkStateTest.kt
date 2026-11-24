package com.bydmate.app.fork.update

import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «На стоянке» = gear P (1) AND speed 0 — stricter than the single-selector automation gate. */
class ParkStateTest {

    @Test
    fun `parked only when gear P and speed zero`() {
        assertTrue(ParkState.isParked(gear = 1, speed = 0))
    }

    @Test
    fun `gear P but still rolling is not parked`() {
        assertFalse(ParkState.isParked(gear = 1, speed = 5))
    }

    @Test
    fun `speed zero but not in P is not parked`() {
        assertFalse(ParkState.isParked(gear = 4, speed = 0)) // D
        assertFalse(ParkState.isParked(gear = 2, speed = 0)) // R
        assertFalse(ParkState.isParked(gear = 3, speed = 0)) // N
    }

    @Test
    fun `unknown gear or speed is never parked`() {
        assertFalse(ParkState.isParked(gear = null, speed = 0))
        assertFalse(ParkState.isParked(gear = 1, speed = null))
        assertFalse(ParkState.isParked(gear = null, speed = null))
    }

    @Test
    fun `reader applies the rule to the live poll`() {
        assertTrue(ParkStateReader { diParsData(gear = 1, speed = 0) }.isParked())
        assertFalse(ParkStateReader { diParsData(gear = 4, speed = 0) }.isParked())
        assertFalse(ParkStateReader { diParsData(gear = 1, speed = 30) }.isParked())
    }

    @Test
    fun `reader with no poll is not parked`() {
        assertFalse(ParkStateReader { null }.isParked())
    }
}
