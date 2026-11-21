package com.bydmate.app.fork.fidscan

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// fork: pure file rules of the fid scan (no Android): the free-space minimum, which files are
// recording logs, and the zip of them that is sent to the developer.

/** A recording is not started, and a running one ends, below this much free space. */
const val MIN_FREE_BYTES = 5L * 1024 * 1024 * 1024

/** Deflater level 5: medium compression, quick on the head unit. */
const val ZIP_LEVEL = 5

fun hasRoom(availableBytes: Long): Boolean = availableBytes >= MIN_FREE_BYTES

/** The poll files of this feature and the push recorder files of the author. */
fun isScanLog(name: String): Boolean =
    (name.startsWith("fidscan-") && name.endsWith(".txt")) ||
        (name.startsWith("bydmate_fidrec_") && name.endsWith(".txt"))

fun zipName(stamp: String): String = "fidscan-logs-$stamp.zip"

/** One entry per file, named by the file name, in the given order. Returns the zip size.
 *  A throw removes the partial [target] and is rethrown. */
fun zipLogs(files: List<File>, target: File, level: Int = ZIP_LEVEL): Long {
    var done = false
    try {
        writeZip(files, target, level)
        done = true
        return target.length()
    } finally {
        if (!done) target.delete()
    }
}

private fun writeZip(files: List<File>, target: File, level: Int) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
        zip.setLevel(level.coerceIn(Deflater.NO_COMPRESSION, Deflater.BEST_COMPRESSION))
        for (f in files) {
            zip.putNextEntry(ZipEntry(f.name))
            FileInputStream(f).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
