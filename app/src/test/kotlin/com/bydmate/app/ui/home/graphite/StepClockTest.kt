package com.bydmate.app.ui.home.graphite

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StepClockTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun quantizerRoundsToHundred() {
        assertNull(quantizeRpm(null, 1000))
        assertEquals(0, quantizeRpm(-30, null))
        assertEquals(1500, quantizeRpm(1449 + 1, null)) // 1450 rounds up
        assertEquals(1400, quantizeRpm(1449, null))
    }

    @Test fun quantizerHoldsInsideTheBand() {
        assertEquals(1400, quantizeRpm(1499, 1400))
        assertEquals(1500, quantizeRpm(1501, 1400))
        assertEquals(1400, quantizeRpm(1300, 1400))
        assertEquals(1300, quantizeRpm(1299, 1400))
    }

    @Test fun quantizerSlowRampMovesInSingleSteps() {
        var held: Int? = 1400
        val seen = ArrayList<Int?>()
        for (raw in 1400..1620 step 10) {
            held = quantizeRpm(raw, held)
            if (seen.lastOrNull() != held) seen.add(held)
        }
        assertEquals(listOf<Int?>(1400, 1500, 1600), seen)
    }

    @Test fun quantizerJitterDoesNotMove() {
        var held: Int? = null
        val seen = HashSet<Int?>()
        for (raw in listOf(1500, 1530, 1470, 1560, 1440, 1510)) {
            held = quantizeRpm(raw, held); seen.add(held)
        }
        assertEquals(1, seen.size)
    }

    @Test fun glowBreathKeepsItsRangeAndPeriod() {
        assertEquals(0.10f, glowBreath(0f), 1e-4f)
        assertEquals(0.28f, glowBreath(2f), 1e-4f)
        assertEquals(0.10f, glowBreath(4f), 1e-4f)
        assertTrue(glowBreath(1f) in 0.10f..0.28f)
    }

    @Test fun clockTicksOnlyWhileStarted() = runTest(dispatcher) {
        val owner = object : LifecycleOwner {
            val reg = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = reg
        }
        var seconds = 0f
        owner.reg.currentState = Lifecycle.State.STARTED
        val job = launch { runStepClock(owner.lifecycle, 83, { currentTime }, { seconds }, { seconds = it }) }
        advanceTimeBy(1_000)
        val running = seconds
        assertTrue("ran: $running", running > 0.8f)
        owner.reg.currentState = Lifecycle.State.CREATED // stopped
        advanceTimeBy(10)
        val frozen = seconds
        advanceTimeBy(5_000)
        assertEquals(frozen, seconds, 0f)
        owner.reg.currentState = Lifecycle.State.STARTED // back: continues from where it was
        advanceTimeBy(500)
        assertTrue(seconds > frozen && seconds < frozen + 1f)
        job.cancel()
    }
}
