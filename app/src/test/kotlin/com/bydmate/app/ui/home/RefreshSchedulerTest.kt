package com.bydmate.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshSchedulerTest {
    private val posted = mutableListOf<Long>()
    private var cancels = 0
    private val s = RefreshScheduler(post = { posted += it }, cancel = { cancels++ })

    @Test fun firstScheduledWins() {
        s.schedule(100); s.schedule(500)
        assertEquals(listOf(100L), posted)
    }

    @Test fun destroyCancelsTheQueuedRefreshAndWaitsLonger() {
        s.schedule(100)
        s.pushBack(500)
        assertEquals(1, cancels)
        assertEquals(listOf(100L, 500L), posted)
        s.schedule(100) // still pending: coalesced
        assertEquals(2, posted.size)
    }

    @Test fun afterItFiredANewOneCanBeQueued() {
        s.schedule(100); s.fired(); s.schedule(100)
        assertEquals(listOf(100L, 100L), posted)
    }
}
