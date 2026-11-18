package com.bydmate.app.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDataFlowTest {
    @Test fun buildsOncePerSecondNotPerPush() = runTest {
        val src = MutableStateFlow(0)
        var builds = 0
        val out = ArrayList<Int>()
        val job = launch {
            src.sampleBuild(1_000, StandardTestDispatcher(testScheduler)) { builds++; it }.collect { out += it }
        }
        runCurrent()
        // 20 pushes inside one second: one build
        for (i in 1..20) { src.value = i; advanceTimeBy(40) }
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, builds)
        assertEquals(listOf(20), out.takeLast(1))
        job.cancel()
    }

    @Test fun sameResultIsNotEmittedAgain() = runTest {
        val src = MutableStateFlow(1)
        val out = ArrayList<Int>()
        val job = launch {
            src.sampleBuild(1_000, StandardTestDispatcher(testScheduler)) { it / 10 }.collect { out += it }
        }
        advanceTimeBy(1_100); runCurrent()
        src.value = 5   // same built value (0)
        advanceTimeBy(1_000); runCurrent()
        src.value = 12  // a different one
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(0, 1), out)
        job.cancel()
    }
}
