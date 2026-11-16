package com.bydmate.app.ui.home.cdrive

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTextTest {
    @Test fun minutesAndSeconds() {
        assertEquals("03:24", clockText(204_999L))
        assertEquals("00:00", clockText(0L))
    }

    @Test fun unknownIsDashes() {
        assertEquals("--:--", clockText(null))
        assertEquals("--:--", clockText(-5L))
    }

    @Test fun longTracksStopAt9959() {
        assertEquals("99:59", clockText((99 * 60 + 59) * 1000L))
        assertEquals("99:59", clockText(100 * 60 * 1000L))
        assertEquals("99:59", clockText(5 * 3600 * 1000L))
    }
}
