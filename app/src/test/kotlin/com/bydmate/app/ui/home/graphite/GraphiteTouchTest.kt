package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphiteTouchTest {
    @Test fun volumeIndexFromPercent() {
        assertEquals(0, volumeIndex(0, 30))
        assertEquals(15, volumeIndex(50, 30))
        assertEquals(30, volumeIndex(100, 30))
        assertEquals(30, volumeIndex(140, 30))
        assertEquals(0, volumeIndex(-5, 30))
    }

    @Test fun volumeAlongTrackIsClamped() {
        // track from x = 76 to x = 276
        assertEquals(0, volumeOnTrack(10f, 76f, 200f))
        assertEquals(0, volumeOnTrack(76f, 76f, 200f))
        assertEquals(50, volumeOnTrack(176f, 76f, 200f))
        assertEquals(100, volumeOnTrack(276f, 76f, 200f))
        assertEquals(100, volumeOnTrack(400f, 76f, 200f))
        assertEquals(0, volumeOnTrack(100f, 76f, 0f))
    }

    @Test fun onlyTouchesNearTheTrackStart() {
        assertTrue(nearTrack(66f, 76f, 200f, 14f))
        assertTrue(nearTrack(286f, 76f, 200f, 14f))
        assertFalse(nearTrack(40f, 76f, 200f, 14f)) // the icon
        assertFalse(nearTrack(320f, 76f, 200f, 14f)) // the number
    }

    @Test fun tabUnderX() {
        val w = intArrayOf(100, 140, 120)
        assertEquals(0, tabAt(0f, 5f, w)) // left padding belongs to the first tab
        assertEquals(0, tabAt(104f, 5f, w))
        assertEquals(1, tabAt(105f, 5f, w))
        assertEquals(1, tabAt(244f, 5f, w))
        assertEquals(2, tabAt(245f, 5f, w))
        assertEquals(2, tabAt(500f, 5f, w)) // right padding belongs to the last tab
    }
}
