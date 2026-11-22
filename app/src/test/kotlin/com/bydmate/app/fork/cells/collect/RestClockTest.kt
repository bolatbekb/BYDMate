package com.bydmate.app.fork.cells.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestClockTest {
    private val h = 3_600_000L
    private val day = 24 * h

    @Test fun `restSince prefers elapsed when the wall clock jumped back`() =
        assertEquals(
            9 * h,
            restSince(SessionAnchor(wallMs = 10 * h, elapsedMs = 1 * h, soc = 50), nowWallMs = 8 * h, nowElapsedMs = 10 * h),
        )

    @Test fun `restSince falls back to the wall clock after a reboot`() =
        assertEquals(8 * h, restSince(SessionAnchor(100 * h, 50 * h, 50), nowWallMs = 108 * h, nowElapsedMs = 2 * h))

    @Test fun `restSince is unknown after a reboot with the clock gone back`() =
        assertNull(restSince(SessionAnchor(100 * h, 50 * h, 50), 90 * h, 2 * h))

    @Test fun `restSince is unknown past 30 days and without an anchor`() {
        assertNull(restSince(SessionAnchor(0L, 500 * day, 50), nowWallMs = 40 * day, nowElapsedMs = 1 * h))
        assertEquals(30 * day, restSince(SessionAnchor(0L, 500 * day, 50), nowWallMs = 30 * day, nowElapsedMs = 1 * h))
        assertNull(restSince(null, 40 * day, 1 * h))
    }
}
