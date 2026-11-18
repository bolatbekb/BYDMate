package com.bydmate.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NowPlayingAnchorTest {
    @Test fun growsWithTimeWhilePlaying() {
        assertEquals(30_000L, anchoredPosition(30_000, 1_000, 1_000, true, 1f, 200_000))
        assertEquals(34_000L, anchoredPosition(30_000, 1_000, 5_000, true, 1f, 200_000))
        assertEquals(36_000L, anchoredPosition(30_000, 1_000, 5_000, true, 1.5f, 200_000))
    }

    @Test fun frozenWhilePaused() {
        assertEquals(30_000L, anchoredPosition(30_000, 1_000, 99_000, false, 1f, 200_000))
    }

    @Test fun clampedToDurationAndNeverNegative() {
        assertEquals(200_000L, anchoredPosition(199_000, 0, 5_000, true, 1f, 200_000))
        assertEquals(0L, anchoredPosition(-500, 0, 0, true, 1f, null))
        // a clock that went backwards does not rewind
        assertEquals(10_000L, anchoredPosition(10_000, 5_000, 1_000, true, 1f, null))
        // unknown duration: no upper clamp
        assertEquals(12_000L, anchoredPosition(10_000, 0, 2_000, true, 1f, null))
    }

    @Test fun stalledPlayerAtSpeedZeroStaysFrozen() {
        assertEquals(30_000L, anchoredPosition(30_000, 1_000, 9_000, true, 0f, 200_000))
    }

    @Test fun unknownPositionStaysUnknown() {
        assertNull(anchoredPosition(null, 0, 5_000, true, 1f, 100_000))
    }

    @Test fun nowPlayingRunsFromItsAnchor() {
        val np = NowPlaying(title = "t", isPlaying = true, positionMs = 10_000, positionAtMs = 2_000, durationMs = 60_000)
        assertEquals(13_000L, np.livePosition(5_000))
        val moved = np.advancedTo(5_000)
        assertEquals(13_000L, moved.positionMs)
        assertEquals(5_000L, moved.positionAtMs)
        val paused = np.copy(isPlaying = false)
        assertSame(paused, paused.advancedTo(9_000))
        assertEquals(10_000L, paused.livePosition(9_000))
    }

    @Test fun sameAnchorIsTheSameState() {
        // the source publishes through a StateFlow: equal values are not emitted again
        val a = NowPlaying(title = "t", isPlaying = true, positionMs = 10_000, positionAtMs = 2_000)
        assertEquals(a, NowPlaying(title = "t", isPlaying = true, positionMs = 10_000, positionAtMs = 2_000))
        org.junit.Assert.assertNotEquals(a, a.copy(positionAtMs = 3_000))
    }

    @Test fun metadataIsFetchedOnlyWhenTheTrackOwnerChanges() {
        val gate = MetadataGate<String>()
        var fetches = 0
        val fetch = { fetches++; "md$fetches" }
        assertEquals("md1", gate.get("token-A", fetch))
        assertEquals("md1", gate.get("token-A", fetch))
        assertEquals("md1", gate.get("token-A", fetch))
        assertEquals(1, fetches)
        // a callback pushes the new track: still no fetch
        gate.set("pushed")
        assertEquals("pushed", gate.get("token-A", fetch))
        assertEquals(1, fetches)
        // another session: one fetch
        assertEquals("md2", gate.get("token-B", fetch))
        assertEquals(2, fetches)
        gate.reset()
        gate.get("token-B", fetch)
        assertEquals(3, fetches)
    }

    @Test fun nullMetadataIsCachedToo() {
        val gate = MetadataGate<String>()
        var fetches = 0
        repeat(3) { assertNull(gate.get("t") { fetches++; null }) }
        assertEquals(1, fetches)
    }
}
