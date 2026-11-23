package com.bydmate.app.fork.cells.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellReadingTest {
    @Test fun `complete reading carries the page time range`() {
        val r = readingOf(feedFixture().snapshot(true, 0, 0L), 184, 24) as CellReading.Complete
        assertTrue(r.oldestPageMs <= r.newestPageMs)
        assertTrue(r.newestPageMs <= msOfDay("19:45:12.400"))
    }

    @Test fun `declared counts unseen is incomplete with all pages missing`() {
        val c = FakeClock(); val a = CellStreamAssembler(c)
        a.onBuffer(GB_STREAM_FID, hex("0000043D000200045D6D37CE0CCE0CCE0C46"))
        assertEquals(CellReading.Incomplete(62, 4), readingOf(a.snapshot(true, 0, 0L), 184, 24))
    }

    @Test fun `reset forgets everything`() {
        val a = feedFixture(); a.reset()
        assertEquals(CellReading.NoFrames, readingOf(a.snapshot(true, 0, 0L), 184, 24))
    }
}
