package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTripPublishTest {
    @Test fun transitionPublishesAtOnce() {
        assertTrue(LiveTrip.shouldPublish(true, 5_000L, 5_000L))
        assertTrue(LiveTrip.shouldSave(true, 5_000L, 5_000L))
    }

    @Test fun quietReadingWaits10s() {
        assertFalse(LiveTrip.shouldPublish(false, 19_999L, 10_000L))
        assertTrue(LiveTrip.shouldPublish(false, 20_000L, 10_000L))
    }

    @Test fun quietReadingSavesAfter60s() {
        assertFalse(LiveTrip.shouldSave(false, 69_999L, 10_000L))
        assertTrue(LiveTrip.shouldSave(false, 70_000L, 10_000L))
    }
}
