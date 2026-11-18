package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTripSampleTest {
    private val t0 = 10_000_000L
    private val boot = BootInfo(bootCount = 41, elapsedMs = 1_000L, sleepMs = 0L)
    private val ticks = flow { while (true) { emit(Unit); delay(10_000) } }

    private fun reading(odo: Double, ps: Int?, speed: Int = 0): DiParsData =
        diParsData(mileage = odo, totalElecConsumption = 2650.0).copy(gear = 1, speed = speed, powerState = ps)

    @Test fun identicalPollsStayFreshSoTheOffIsConfirmed() = runTest {
        val tracker = LiveTripTracker(LiveTripState(), t0)
        tracker.onTick(boot, 500L, reading(17000.0, 2), t0)
        tracker.onTick(boot, 500L, reading(17002.0, 2, 40), t0 + 10_000)
        val data = MutableStateFlow(reading(17002.0, 0))
        var at = t0 + 20_000 // the first ps=0 poll
        val job = launch {
            LiveTrip.beats(data, MutableStateFlow(500L), ticks).collect { anchor ->
                val (d, readingAt) = LiveTrip.liveReading(data) { at }
                tracker.onTick(boot, anchor, d, t0 + 20_000 + currentTime, readingAt)
            }
        }
        runCurrent()
        assertTrue(tracker.state.open?.offSinceMs != null)
        // the idle 30-s poll returns the SAME snapshot: the StateFlow emits nothing, the time advances
        advanceTimeBy(25_000); at = t0 + 50_000; data.value = reading(17002.0, 0)
        advanceTimeBy(10_000); runCurrent()
        assertNull(tracker.state.open)
        assertEquals(1, tracker.state.finished.size)
        job.cancel()
    }

    @Test fun triggersAreLimitedToOnePerSecondFirstAtOnce() = runTest {
        val data = MutableStateFlow(0)
        var runs = 0
        val job = launch { LiveTrip.beats(data, MutableStateFlow(1), flow { emit(Unit) }).collect { runs++ } }
        runCurrent()
        assertEquals(1, runs)
        repeat(5) { advanceTimeBy(100); data.value = it + 1; runCurrent() }
        assertEquals(1, runs)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, runs)
        job.cancel()
    }

    @Test fun liveReadingTakesTheTimeBeforeTheData() {
        val order = mutableListOf<String>()
        val flow = object : kotlinx.coroutines.flow.StateFlow<Int> {
            override val replayCache get() = listOf(1)
            override val value: Int get() { order += "data"; return 1 }
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<Int>): Nothing = error("unused")
        }
        LiveTrip.liveReading(flow) { order += "time"; 7L }
        assertEquals(listOf("time", "data"), order)
    }
}
