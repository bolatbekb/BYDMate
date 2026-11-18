package com.bydmate.app.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DecodeRetryTest {
    @Test fun successDoesNotWait() = runTest {
        assertEquals("a", decodeWithRetry(1_000) { "a" })
        assertEquals(0L, currentTime)
    }

    @Test fun oneFailureIsRetriedAfterThePause() = runTest {
        var n = 0
        assertEquals("b", decodeWithRetry(1_000) { if (n++ == 0) null else "b" })
        assertEquals(1_000L, currentTime)
    }

    @Test fun twoFailuresGiveUpAfterTwoTries() = runTest {
        var n = 0
        assertNull(decodeWithRetry<String>(1_000) { n++; null })
        assertEquals(2, n)
    }
}
