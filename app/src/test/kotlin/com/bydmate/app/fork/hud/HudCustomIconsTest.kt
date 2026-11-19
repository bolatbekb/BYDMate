package com.bydmate.app.fork.hud

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HudCustomIconsTest {
    private val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun png(w: Int, h: Int, extra: Int = 0): ByteArray {
        fun be(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        return sig + be(13) + "IHDR".toByteArray() + be(w) + be(h) + ByteArray(5 + extra)
    }

    @Before fun reset() {
        HudCustomIcons.resetCounters()
        HudCustomIcons.enabled = false
        HudCustomIcons.scannerOverride = null
    }

    @Test fun nameParsing() {
        assertEquals(2, HudIconFiles.parseName("0x2.png"))
        assertEquals(0x1a, HudIconFiles.parseName("0x1a.png"))
        assertEquals(0x1a, HudIconFiles.parseName("0x1A.png"))
        assertEquals(0xff, HudIconFiles.parseName("0xFF.PNG"))
        assertNull(HudIconFiles.parseName("0x0.png"))
        assertNull(HudIconFiles.parseName("0x100.png"))
        assertNull(HudIconFiles.parseName("0x2.jpg"))
        assertNull(HudIconFiles.parseName("2.png"))
        assertNull(HudIconFiles.parseName("0xzz.png"))
    }

    @Test fun pngSizeParsed() {
        assertEquals(38 to 41, HudIconFiles.pngSize(png(38, 41)))
        assertNull(HudIconFiles.pngSize(ByteArray(40)))
        assertNull(HudIconFiles.pngSize(png(1, 1).copyOf(12)))
        assertNull(HudIconFiles.pngSize(ByteArray(0)))
    }

    @Test fun validateLimits() {
        assertNull(HudIconFiles.reject("0x1.png", 100, png(38, 41)))
        assertEquals("name", HudIconFiles.reject("x.png", 100, png(38, 41)))
        assertEquals("too_big", HudIconFiles.reject("0x1.png", 256 * 1024 + 1, png(38, 41)))
        assertNull(HudIconFiles.reject("0x1.png", 256 * 1024, png(38, 41)))
        assertEquals("not_png", HudIconFiles.reject("0x1.png", 100, ByteArray(40)))
        assertEquals("bad_size", HudIconFiles.reject("0x1.png", 100, png(0, 10)))
        assertEquals("bad_size", HudIconFiles.reject("0x1.png", 100, png(513, 10)))
        assertNull(HudIconFiles.reject("0x1.png", 100, png(512, 512)))
    }

    @Test fun pickOrder() {
        val c = byteArrayOf(1); val b = byteArrayOf(2); val n = byteArrayOf(3)
        assertEquals(HudCustomIcons.Source.CUSTOM, HudCustomIcons.pick(5, { c }, { b }, n).second)
        assertArrayEquals(c, HudCustomIcons.pick(5, { c }, { b }, n).first)
        assertEquals(HudCustomIcons.Source.BYDMATE, HudCustomIcons.pick(5, { null }, { b }, n).second)
        assertArrayEquals(n, HudCustomIcons.pick(5, { null }, { null }, n).first)
        assertEquals(HudCustomIcons.Source.NAVIGATOR, HudCustomIcons.pick(5, { null }, { null }, n).second)
        assertEquals(HudCustomIcons.Source.NAVIGATOR, HudCustomIcons.pick(0, { c }, { b }, n).second)
        assertNull(HudCustomIcons.pick(0, { c }, { b }, null).first)
    }

    @Test fun counters() {
        HudCustomIcons.pick(1, { byteArrayOf(1) }, { null }, null)
        HudCustomIcons.pick(1, { byteArrayOf(1) }, { null }, null)
        HudCustomIcons.pick(1, { null }, { byteArrayOf(2) }, null)
        HudCustomIcons.pick(0, { null }, { null }, byteArrayOf(3))
        assertEquals(listOf(2, 1, 1), HudCustomIcons.counters())
    }

    private class Clock(var now: Long = 1_000_000L)

    private fun scanner(dir: File, clock: Clock, reads: IntArray) = HudCustomIcons.Scanner(
        dir = dir, clock = { clock.now },
        reader = { f -> reads[0]++; f.readBytes() }, log = { },
    )

    @Test fun scanAddChangeRemoveAndThrottle() {
        val dir = Files.createTempDirectory("hudicons").toFile()
        try {
            val clock = Clock(); val reads = IntArray(1)
            val s = scanner(dir, clock, reads)
            assertNull(s.iconFor(0))
            assertNull(s.iconFor(2))
            File(dir, "0x2.png").writeBytes(png(10, 10))
            File(dir, "junk.txt").writeBytes(ByteArray(3))
            File(dir, "0x3.png").writeBytes(ByteArray(50))
            // within 30 s: nothing is seen, no reads
            clock.now += 29_000
            assertNull(s.iconFor(2)); assertEquals(0, reads[0])
            clock.now += 2_000
            assertNotNull(s.iconFor(2)); assertEquals(2, reads[0])
            assertNull(s.iconFor(3))
            assertEquals(setOf(2), s.codes())
            assertTrue(s.rejected().containsAll(listOf("junk.txt: name", "0x3.png: not_png")))
            // unchanged rescan re-reads nothing
            clock.now += 31_000
            s.iconFor(2); assertEquals(2, reads[0])
            // change
            val f = File(dir, "0x2.png"); f.writeBytes(png(20, 20, 7)); f.setLastModified(f.lastModified() + 5000)
            clock.now += 31_000
            assertEquals(f.length().toInt(), s.iconFor(2)!!.size); assertEquals(3, reads[0])
            // remove
            f.delete()
            clock.now += 31_000
            assertNull(s.iconFor(2))
        } finally { dir.deleteRecursively() }
    }

    @Test fun missingDirIsEmpty() {
        val s = scanner(File("/nonexistent/hud_x"), Clock(), IntArray(1))
        assertNull(s.iconFor(2))
        assertTrue(s.codes().isEmpty())
        assertTrue(s.lines().any { it.contains("exists=false") })
    }

    @Test fun capsAt64Files() {
        val dir = Files.createTempDirectory("hudicons").toFile()
        try {
            for (i in 1..70) File(dir, "0x${i.toString(16)}.png").writeBytes(png(5, 5))
            val s = scanner(dir, Clock(), IntArray(1))
            s.iconFor(1)
            assertEquals(64, s.codes().size)
            assertEquals(6, s.rejected().count { it.endsWith("too_many") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun linesSection() {
        val l = HudCustomIcons.lines()
        assertEquals("--- hud icons (fork) ---", l.first())
        assertTrue(l.any { it.startsWith("sources used:") })
    }

    @Test fun switchOffSkipsScansAndKeepsOldOrder() {
        val dir = Files.createTempDirectory("hudicons").toFile()
        try {
            File(dir, "0x2.png").writeBytes(png(10, 10))
            val reads = IntArray(1)
            HudCustomIcons.scannerOverride = scanner(dir, Clock(), reads)
            val builtin = byteArrayOf(9)
            HudCustomIcons.enabled = false
            assertArrayEquals(builtin, HudCustomIcons.pickIcon(2, { builtin }, null))
            assertArrayEquals(builtin, HudCustomIcons.customOr(2) { builtin })
            assertEquals(0, reads[0])
            assertEquals(listOf(0, 1, 0), HudCustomIcons.counters())
            assertTrue(HudCustomIcons.lines().contains("switch: off"))
            HudCustomIcons.enabled = true
            assertEquals(png(10, 10).size, HudCustomIcons.pickIcon(2, { builtin }, null)!!.size)
            assertEquals(1, reads[0])
            assertTrue(HudCustomIcons.lines().contains("switch: on"))
        } finally { dir.deleteRecursively() }
    }
}
