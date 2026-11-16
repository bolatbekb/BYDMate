package com.bydmate.app.ui.home.graphite

import kotlin.random.Random
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryPickTest {
    @Test
    fun neverRepeats() {
        val rnd = Random(1)
        repeat(1000) { assertNotEquals(2, nextPhoto(2, 5, rnd)) }
    }

    @Test
    fun delayInRange() {
        val rnd = Random(7)
        repeat(1000) { assertTrue(nextDelayMs(rnd) in 120_000L..240_000L) }
    }
}
