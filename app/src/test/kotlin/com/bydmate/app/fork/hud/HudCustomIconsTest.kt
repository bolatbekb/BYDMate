package com.bydmate.app.fork.hud

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HudCustomIconsTest {
    private class FakeAssets(val files: Map<String, ByteArray>) : HudCustomIcons.AssetSource {
        var lists = 0; var reads = 0
        override fun names(): List<String> { lists++; return files.keys.toList() }
        override fun read(name: String): ByteArray { reads++; return files.getValue(name) }
    }

    private val ours = byteArrayOf(1)
    private val builtin = byteArrayOf(2)
    private val nav = byteArrayOf(3)

    @Before fun reset() {
        HudCustomIcons.enabled = false
        HudCustomIcons.resetForTest(null)
    }

    @Test fun pickOrder() {
        val c = byteArrayOf(1); val b = byteArrayOf(2); val n = byteArrayOf(3)
        assertEquals(HudCustomIcons.Source.CUSTOM, HudCustomIcons.pick(5, { c }, { b }, n).second)
        assertArrayEquals(c, HudCustomIcons.pick(5, { c }, { b }, n).first)
        assertEquals(HudCustomIcons.Source.BYDMATE, HudCustomIcons.pick(5, { null }, { b }, n).second)
        assertArrayEquals(n, HudCustomIcons.pick(5, { null }, { null }, n).first)
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

    @Test fun switchOffReadsNoAssetsAndKeepsOldOrder() {
        val fake = FakeAssets(mapOf("0x2.png" to ours))
        HudCustomIcons.resetForTest(fake)
        HudCustomIcons.enabled = false
        assertArrayEquals(builtin, HudCustomIcons.pickIcon(2, { builtin }, nav))
        assertArrayEquals(builtin, HudCustomIcons.customOr(2) { builtin })
        assertArrayEquals(nav, HudCustomIcons.pickIcon(2, { null }, nav))
        assertEquals(0, fake.lists + fake.reads)
        assertTrue(HudCustomIcons.lines().contains("switch: off"))
    }

    @Test fun switchOnOurIconThenBuiltinThenNavigator() {
        val fake = FakeAssets(mapOf("0x2.png" to ours, "readme.txt" to byteArrayOf(9)))
        HudCustomIcons.resetForTest(fake)
        HudCustomIcons.enabled = true
        assertArrayEquals(ours, HudCustomIcons.pickIcon(2, { builtin }, nav))
        assertArrayEquals(builtin, HudCustomIcons.pickIcon(5, { builtin }, nav))
        assertArrayEquals(nav, HudCustomIcons.pickIcon(5, { null }, nav))
        assertArrayEquals(ours, HudCustomIcons.customOr(2) { builtin })
        assertEquals(1, fake.lists)   // loaded once
        assertEquals(1, fake.reads)   // only the png
        assertTrue(HudCustomIcons.lines().contains("icons loaded from navi_fork: 1"))
    }

    @Test fun shippedAssetsAreValid() {
        val dir = File("src/main/assets/navi_fork")
        val author = File("src/main/assets/navi").list().orEmpty().toSet()
        val files = dir.listFiles().orEmpty()
        assertTrue("navi_fork must not be empty", files.isNotEmpty())
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (f in files) {
            assertTrue("name ${f.name}", Regex("0x[0-9a-f]{1,2}\\.png").matches(f.name))
            assertTrue("${f.name} is not in the author's mapping", f.name in author)
            assertTrue("${f.name} > 48 KB", f.length() <= 48 * 1024)
            val b = f.readBytes()
            assertArrayEquals("${f.name} signature", sig, b.copyOf(8))
            assertEquals("${f.name} IHDR", "IHDR", String(b, 12, 4, Charsets.ISO_8859_1))
        }
    }
}
