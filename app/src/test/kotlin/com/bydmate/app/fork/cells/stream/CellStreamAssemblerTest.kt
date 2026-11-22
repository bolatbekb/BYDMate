package com.bydmate.app.fork.cells.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellStreamAssemblerTest {
    private val clock = FakeClock()
    private val asm = CellStreamAssembler(clock)
    private fun snap() = asm.snapshot(true, 0, 0L)
    private fun feed(h: String) = asm.onBuffer(GB_STREAM_FID, hex(h))

    @Test fun `parseGbEnvelope reads can sub channel and payload`() {
        val f = parseGbEnvelope(GB_STREAM_FID, hex("0000043D000200045D6D37CE0CCE0CCE0C46"))!!
        assertEquals(0x43D, f.can); assertEquals(0, f.sub); assertEquals(2, f.ch)
        assertEquals(8, f.payload.size); assertEquals(0x37, f.payload[0].toInt() and 0xFF)
    }

    @Test fun `parseGbEnvelope rejects other fids and short buffers`() {
        assertNull(parseGbEnvelope(0x99000020.toInt(), hex("0000043D000200045D6D37CE0CCE0CCE0C46")))
        assertNull(parseGbEnvelope(GB_STREAM_FID, ByteArray(9)))
    }

    @Test fun `cell page fills three slots little endian`() {
        feed("0000043D000200045D6D37CE0CCE0CCE0C46")
        val s = snap()
        assertEquals(listOf(3278, 3278, 3278), s.cellMv.slice(165..167))
        assertTrue(s.cellPageMs[0x37] > 0)
    }

    @Test fun `0xFFFF leaves the slot empty`() {
        feed("0000043D00020004789E3DCA0CFFFFFFFF47")
        val s = snap()
        assertEquals(3274, s.cellMv[183]); assertEquals(NO_VALUE, s.cellMv[184]); assertEquals(NO_VALUE, s.cellMv[185])
    }

    @Test fun `probe page with a bad checksum is rejected`() {
        feed("0000043C000200045D63003C3C3C3B3C3C99")
        val s = snap()
        assertEquals(1, s.rejected); assertEquals(NO_TEMP, s.probeC[0]); assertEquals(0, s.frames)
    }

    @Test fun `counts and extremes`() {
        feed("0000043A000200045F92B818913B4B100008")
        feed("0000043B000200045F98013C043BFFFF275E")
        val s = snap()
        assertEquals(184, s.declaredCells); assertEquals(24, s.declaredProbes)
        assertEquals(1, s.hottest); assertEquals(20, s.hottestC); assertEquals(4, s.coldest); assertEquals(19, s.coldestC)
    }

    @Test fun `page 253 and pages past the cap are ignored`() {
        val before = snap().cellMv.copyOf()
        feed("0000043D00020004602FFD8E13CE0CC70C46")
        assertTrue(before.contentEquals(snap().cellMv))
    }

    @Test fun `onBuffer never throws on garbage`() {
        asm.onBuffer(GB_STREAM_FID, null); asm.onBuffer(GB_STREAM_FID, ByteArray(0))
        asm.onBuffer(GB_STREAM_FID, ByteArray(10) { (it * 37 + 11).toByte() }); asm.onBuffer(0, hex("0000043D000200045D6D37CE0CCE0CCE0C46"))
        assertEquals(0, snap().frames)
    }

    @Test fun `real pass decodes to 184 cells and 24 probes`() {
        val r = readingOf(feedFixture().snapshot(true, 0, 0L), 184, 24) as CellReading.Complete
        assertEquals(184, r.cellsMv.size); assertEquals(listOf(3274, 3274, 3275), r.cellsMv.take(3))
        assertEquals(3274, r.cellsMv[183]); assertEquals(3271, r.cellsMv.min()); assertEquals(3278, r.cellsMv.max())
        assertEquals(listOf(20, 20, 20, 19, 20, 20, 20, 19) + List(16) { 20 }, r.probesC.toList())
    }

    @Test fun `reading is incomplete when a page is older than 10 s`() {
        val c = FakeClock(); val a = feedFixture(c)
        c.now = msOfDay("19:45:16.300")
        val r = readingOf(a.snapshot(true, 0, 0L), 184, 24) as CellReading.Incomplete
        assertTrue(r.missingCellPages >= 1)
    }

    @Test fun `reading needs pages after the load window start`() {
        val r = readingOf(feedFixture().snapshot(true, 0, 0L), 184, 24, msOfDay("19:45:08.000"))
        assertTrue(r is CellReading.Incomplete)
    }

    @Test fun `declared count mismatch`() {
        val c = FakeClock(); val a = feedFixture(c)
        a.onBuffer(GB_STREAM_FID, hex("0000043A000200045F9278100000000000"))
        assertEquals(CellReading.Mismatch(120, 16), readingOf(a.snapshot(true, 0, 0L), 184, 24))
    }

    @Test fun `no frames`() {
        assertEquals(CellReading.NoFrames, readingOf(snap(), 184, 24))
    }

    @Test fun `implausible value makes the reading incomplete`() {
        val c = FakeClock(); val a = feedFixture(c)
        a.onBuffer(GB_STREAM_FID, hex("0000043D000200045D6D000100CE0CCE0C46"))
        assertTrue(readingOf(a.snapshot(true, 0, 0L), 184, 24) is CellReading.Incomplete)
    }
}
