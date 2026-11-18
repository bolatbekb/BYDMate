package com.bydmate.app.ui.home.graphite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTripSampleTest {
    @Test fun firstAtOnceThenOnePairPerSecondWithItsOwnTime() = runTest {
        val data = MutableStateFlow(0)
        var at = 100L
        val got = mutableListOf<Pair<Int, Long>>()
        val job = launch { LiveTrip.sampledReadings(data) { at }.collect { got += it } }
        runCurrent()
        assertEquals(listOf(0 to 100L), got)
        // two reads inside one second: the last one wins and carries its own poll time
        advanceTimeBy(100); at = 200L; data.value = 1; runCurrent()
        advanceTimeBy(100); at = 300L; data.value = 2; runCurrent()
        assertEquals(1, got.size)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(0 to 100L, 2 to 300L), got)
        // a push patch: new data, the poll time unchanged
        advanceTimeBy(100); data.value = 3; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(3 to 300L, got.last())
        job.cancel()
    }

    @Test fun anIdleStreamEmitsNothingMore() = runTest {
        val data = MutableStateFlow(0)
        val got = mutableListOf<Pair<Int, Long>>()
        val job = launch { LiveTrip.sampledReadings(data) { 1L }.collect { got += it } }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, got.size)
        assertEquals(5_000L, currentTime)
        job.cancel()
    }
}
