package com.bydmate.app.fork.cells.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellReadingTest {
    @Test fun `complete reading carries the page time range`() {
        val r = readingOf(feedFixture().snapshot(true, 0, 0L)) as CellReading.Complete
        assertTrue(r.oldestPageMs <= r.newestPageMs)
        assertTrue(r.newestPageMs <= msOfDay("19:45:12.400"))
    }

    @Test fun `pages before the counts are known wait for the count`() {
        val c = FakeClock(); val a = CellStreamAssembler(c)
        a.onBuffer(GB_STREAM_FID, hex("0000043D000200045D6D37CE0CCE0CCE0C46"))
        assertEquals(CellReading.WaitingForCount, readingOf(a.snapshot(true, 0, 0L)))
    }

    @Test fun `reset forgets everything`() {
        val a = feedFixture(); a.reset()
        assertEquals(CellReading.NoFrames, readingOf(a.snapshot(true, 0, 0L)))
    }
}
