package com.bydmate.app.ui.home.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowSamplingTest {
    @Test fun firstValueAtOnceThenOnePerPeriodTheLatest() = runTest {
        val up = Channel<Int>(Channel.UNLIMITED)
        val got = mutableListOf<Pair<Long, Int>>()
        val job = launch { up.receiveAsFlow().throttleLatest(1_000).collect { got += currentTime to it } }
        up.send(1); runCurrent()
        assertEquals(listOf(0L to 1), got)
        up.send(2); up.send(3); up.send(4)
        advanceTimeBy(999); runCurrent()
        assertEquals(1, got.size)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(0L to 1, 1_000L to 4), got)
        job.cancel()
    }

    @Test fun aValueAfterAQuietSpellGoesOutAtOnce() = runTest {
        val up = Channel<Int>(Channel.UNLIMITED)
        val got = mutableListOf<Pair<Long, Int>>()
        val job = launch { up.receiveAsFlow().throttleLatest(1_000).collect { got += currentTime to it } }
        up.send(1); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        up.send(2); runCurrent()
        assertEquals(listOf(0L to 1, 5_000L to 2), got)
        job.cancel()
    }

    @Test fun idleUpstreamSchedulesNoWakeUps() = runTest {
        val up = Channel<Int>(Channel.UNLIMITED)
        val job = launch { up.receiveAsFlow().throttleLatest(1_000).collect { } }
        up.send(1); runCurrent()
        advanceTimeBy(1_001); runCurrent() // the pause after the one value is over
        // nothing is scheduled any more: no periodic timer is left behind
        testScheduler.advanceUntilIdle()
        assertEquals(1_001L, currentTime)
        job.cancel()
    }
}
