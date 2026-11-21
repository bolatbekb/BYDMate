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

// fork: the free-space rule, the log file filter, the header and time stamps, and the zip of the logs.
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

    @Test fun `wallTimeText carries the date`() {
        // Unit tests run in UTC (app/build.gradle.kts).
        assertEquals("1970-01-02 03:04:05", wallTimeText(((24 + 3) * 3600 + 4 * 60 + 5) * 1000L))
    }

    @Test fun `headerLine of a fresh and of a resumed file`() {
        assertEquals(
            "# bydmate fid scan versionName=4.5.0.1-fidscan started=2026-11-21 15:00:00 fingerprint=BYD/a:12/x",
            headerLine("4.5.0.1-fidscan", "2026-11-21 15:00:00", "BYD/a:12/x", resumed = false, usedBefore = 0L),
        )
        assertEquals(
            "# bydmate fid scan versionName=v started=s fingerprint=f resumed=true used_before=1234",
            headerLine("v", "s", "f", resumed = true, usedBefore = 1234L),
        )
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
