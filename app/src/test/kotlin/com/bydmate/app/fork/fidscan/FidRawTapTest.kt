package com.bydmate.app.fork.fidscan

import com.bydmate.app.helper.push.FidRecLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class FidRawTapTest {

    @get:Rule val tmp = TemporaryFolder()

    private val gb = 0x9900001F.toInt()
    private var now = 0L
    private val lines = mutableListOf<String>()

    /** Built at [now]; its first window runs for [WINDOW_MS] from there. */
    private fun tap() = RawTap(write = { lines += it }, clock = { now }, wall = { 0L })

    /** A tap whose start window is over, so only `buf` lines appear. */
    private fun quietTap(): RawTap = tap().also { now = WINDOW_MS + 5_000 }

    private fun rec(can: Int, sub: Int, ch: Int, counter: Int, vararg payload: Int): ByteArray =
        byteArrayOf(
            (can ushr 24).toByte(), (can ushr 16).toByte(), (can ushr 8).toByte(), can.toByte(),
            sub.toByte(), ch.toByte(),
            (counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte(),
        ) + payload.map { it.toByte() }.toByteArray()

    private fun cells(page: Int, counter: Int, mv: Int = 3300) =
        rec(0x43D, 0, 2, counter, page, mv and 0xFF, mv ushr 8, mv and 0xFF, mv ushr 8, mv and 0xFF, mv ushr 8, 0)

    private fun buf() = lines.filter { " buf " in it }
    private fun win() = lines.filter { " win " in it }
    private fun stat() = lines.filter { " bufstat " in it }

    @After
    fun forget() = FidRawTap.reset()

    @Test
    fun envelopeIsReadForStreamFidsOnly() {
        val r = rec(0x43D, 0, 2, 1, 0, 1, 2, 3, 4, 5, 6, 7)
        assertEquals(RawRecord(0x43D, 0, 2, 8), parseEnvelope(gb, r))
        assertEquals(RawRecord(0x43D, 0, 2, 8), parseEnvelope(0x99000020.toInt(), r))
        assertNull(parseEnvelope(0x12345678, r))
        assertNull(parseEnvelope(gb, ByteArray(9)))
        assertNull(parseEnvelope(gb, rec(-0x80000000, 0, 0, 0)))
        assertEquals(RawRecord(0x7FF, 3, 4, 0), parseEnvelope(gb, rec(0x7FF, 3, 4, 0)))
    }

    @Test
    fun bufLineCarriesTheWholeBufferInHex() {
        val t = quietTap()
        t.onBuffer(gb, rec(0x43D, 0, 2, 7, 0x01, 0x10, 0x0E, 0x12, 0x0E, 0x14, 0x0E, 0x00))
        assertEquals(listOf("00:00:00.000 buf fid=9900001F n=18 0000043D00020000000701100E120E140E00"), lines)
    }

    @Test
    fun counterOnlyChangeWritesNothing() {
        val t = quietTap()
        t.onBuffer(gb, cells(0, counter = 1))
        now += 1_000
        t.onBuffer(gb, cells(0, counter = 2))
        t.onBuffer(gb, cells(0, counter = 3))
        assertEquals(1, buf().size)
    }

    @Test
    fun everyBatteryPageIsItsOwnKeyAndChangesLandAfterTheFastGap() {
        val t = quietTap()
        t.onBuffer(gb, cells(0, 1))
        t.onBuffer(gb, cells(1, 2))
        t.onBuffer(gb, cells(2, 3))
        assertEquals(3, buf().size)
        now += 100
        t.onBuffer(gb, cells(0, 4, mv = 3301)) // too soon after page 0's line
        assertEquals(3, buf().size)
        now += FAST_GAP_MS
        t.onBuffer(gb, cells(0, 5, mv = 3301)) // compared with the last WRITTEN page 0: changed
        assertEquals(4, buf().size)
        assertTrue(buf().last().endsWith("0000043D00020000000500E50CE50CE50C00"))
    }

    @Test
    fun otherFramesGetALineAtMostEverySlowGapAndKeepTheLastValue() {
        val t = quietTap()
        t.onBuffer(gb, rec(0x324, 0, 1, 1, 80))
        now += 3_000
        t.onBuffer(gb, rec(0x324, 0, 1, 2, 81)) // changed, but within the gap
        assertEquals(1, buf().size)
        now += SLOW_GAP_MS
        t.onBuffer(gb, rec(0x324, 0, 1, 3, 81)) // still differs from the written 80
        now += 1_000
        t.onBuffer(gb, rec(0x324, 0, 1, 4, 81))
        assertEquals(2, buf().size)
        assertTrue(buf().last().endsWith("00000324000100000003" + "51"))
    }

    @Test
    fun aRouteIsSubAndChannelToo() {
        val t = quietTap()
        t.onBuffer(gb, rec(0x324, 0, 1, 1, 80))
        t.onBuffer(gb, rec(0x324, 0, 2, 1, 80))
        t.onBuffer(gb, rec(0x324, 1, 1, 1, 80))
        assertEquals(3, buf().size)
    }

    @Test
    fun windowWritesEveryBufferForFifteenSecondsEveryTenMinutes() {
        val t = tap()
        now = 1_000
        t.onBuffer(gb, cells(0, 1))
        t.onBuffer(gb, cells(0, 2))
        assertEquals(2, win().size)
        assertEquals(1, buf().size)
        now = WINDOW_MS + 1
        t.onBuffer(gb, cells(0, 3))
        assertEquals(2, win().size)
        now = PERIOD_MS + 1_000
        t.onBuffer(gb, cells(0, 4))
        assertEquals(3, win().size)
        assertEquals("00:00:00.000 win fid=9900001F n=18 0000043D00020000000400E40CE40CE40C00", win().last())
    }

    @Test
    fun bufstatSumsUpThePeriodWhenItEnds() {
        val t = quietTap()
        t.onBuffer(gb, cells(0, 1))
        t.onBuffer(gb, cells(0, 2))
        t.onBuffer(gb, cells(0, 3))
        t.onBuffer(0x99000020.toInt(), rec(0x324, 0, 1, 1, 80))
        assertEquals(0, stat().size)
        now = PERIOD_MS + WINDOW_MS + 1
        t.onBuffer(gb, cells(0, 4))
        assertEquals(
            listOf(
                "00:00:00.000 bufstat fid=9900001F can=0x43D sub=0 ch=2 n=18 count=3 lines=1",
                "00:00:00.000 bufstat fid=99000020 can=0x324 sub=0 ch=1 n=11 count=1 lines=1",
            ),
            stat(),
        )
        now = 2 * PERIOD_MS + WINDOW_MS + 1
        t.onBuffer(gb, cells(0, 5))
        // the second period saw only one 0x43D buffer, and nothing of the other route
        assertEquals("00:00:00.000 bufstat fid=9900001F can=0x43D sub=0 ch=2 n=18 count=1 lines=0", stat().last())
        assertEquals(3, stat().size)
    }

    @Test
    fun anyOtherBufferFidIsKeyedByTheFidAlone() {
        val t = quietTap()
        t.onBuffer(1234, "ABC".toByteArray())
        t.onBuffer(1234, "ABC".toByteArray())
        assertEquals(listOf("00:00:00.000 buf fid=000004D2 n=3 414243"), lines)
        now = PERIOD_MS + WINDOW_MS + 1
        t.onBuffer(1234, "ABD".toByteArray())
        assertEquals("00:00:00.000 bufstat fid=000004D2 can=- sub=- ch=- n=3 count=2 lines=1", stat().single())
    }

    @Test
    fun keysAndRoutesAreCappedAndTheOverflowIsCounted() {
        val t = quietTap()
        for (i in 0 until MAX_KEYS) t.onBuffer(gb, rec(0x100 + i, 0, 0, 0, 1))
        assertEquals(MAX_KEYS, buf().size)
        t.onBuffer(gb, rec(0x100 + MAX_KEYS, 0, 0, 0, 1))
        assertEquals(MAX_KEYS, buf().size)
        now = PERIOD_MS + WINDOW_MS + 1
        t.onBuffer(gb, rec(0x100, 0, 0, 1, 1))
        assertEquals("00:00:00.000 bufstat dropped keys=1 routes=1", stat().last())
    }

    @Test
    fun longBuffersAreCutInTheLine() {
        assertEquals(MAX_HEX_BYTES * 2 + 1, hexOf(ByteArray(200)).length)
        assertTrue(hexOf(ByteArray(200)).endsWith("+"))
        assertEquals("00FF7F80", hexOf(byteArrayOf(0, -1, 127, -128)))
    }

    @Test
    fun garbageNeverThrows() {
        val t = tap()
        val rnd = Random(42)
        repeat(5_000) {
            now += rnd.nextLong(0, 2_000)
            val fid = if (rnd.nextBoolean()) gb else rnd.nextInt()
            t.onBuffer(fid, rnd.nextBytes(rnd.nextInt(0, 300)))
        }
    }

    @Test
    fun tapObjectWritesIntoTheRecorderFileAndIgnoresEmptyBuffers() {
        FidRawTap.onBuffer(gb, cells(0, 1)) // no file yet: nothing, no throw
        val file = tmp.newFile("bydmate_fidrec_test.txt")
        val log = FidRecLog(file)
        FidRawTap.attach(log)
        FidRawTap.onBuffer(gb, null)
        FidRawTap.onBuffer(gb, ByteArray(0))
        FidRawTap.onBuffer(gb, cells(0, 1))
        log.close("end")
        val text = file.readLines()
        assertTrue(text.first().startsWith("fidscan raw: "))
        assertEquals(1, text.count { " win fid=9900001F n=18 " in it })
        assertEquals(1, text.count { " buf fid=9900001F n=18 " in it })
        assertEquals("end", text.last())
    }
}
