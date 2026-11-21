package com.bydmate.app.fork.fidscan

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// fork: the free-space rule, the log file filter and the zip of the logs.
class FidScanFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `hasRoom is true at the minimum and false one byte below`() {
        assertTrue(hasRoom(MIN_FREE_BYTES))
        assertFalse(hasRoom(MIN_FREE_BYTES - 1))
    }

    @Test fun `isScanLog accepts the two log kinds only`() {
        assertTrue(isScanLog("fidscan-20261121-150000.txt"))
        assertTrue(isScanLog("bydmate_fidrec_20261121_150000.txt"))
        assertFalse(isScanLog("fidscan-logs-20261121-150000.zip"))
        assertFalse(isScanLog("fid-dump-20261119-145140.txt"))
        assertFalse(isScanLog("bydmate_logs_20261119_144725.txt"))
    }

    @Test fun `zipName carries the stamp`() {
        assertEquals("fidscan-logs-20261121-150000.zip", zipName("20261121-150000"))
    }

    @Test fun `zipLogs keeps order and content and compresses`() {
        val a = tmp.newFile("a.txt").apply { writeText("V 1 A i=1 d=1.0\n".repeat(13_000)) }
        val b = tmp.newFile("b.txt").apply { writeText("small") }
        val target = File(tmp.root, "out.zip")
        val size = zipLogs(listOf(a, b), target)
        assertEquals(target.length(), size)
        assertTrue(size < a.length() + b.length())
        val names = mutableListOf<String>()
        val texts = mutableListOf<String>()
        ZipInputStream(FileInputStream(target)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                texts += z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(listOf("a.txt", "b.txt"), names)
        assertEquals(listOf(a.readText(), "small"), texts)
    }

    @Test fun `zipLogs with a missing input throws and leaves no target`() {
        val a = tmp.newFile("a.txt").apply { writeText("x") }
        val target = File(tmp.root, "out.zip")
        assertThrows(java.io.IOException::class.java) {
            zipLogs(listOf(a, File(tmp.root, "missing.txt")), target)
        }
        assertFalse(target.exists())
    }
}
