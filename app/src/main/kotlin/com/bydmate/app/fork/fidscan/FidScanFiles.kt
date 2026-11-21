package com.bydmate.app.fork.fidscan

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// fork: pure file rules of the fid scan (no Android): the free-space minimum, which files are
// recording logs, the poll file's header and time stamps, and the zip of them that is sent to
// the developer.

/** A recording is not started, and a running one ends, below this much free space. */
const val MIN_FREE_BYTES = 5L * 1024 * 1024 * 1024

/** Deflater level 5: medium compression, quick on the head unit. */
const val ZIP_LEVEL = 5

fun hasRoom(availableBytes: Long): Boolean = availableBytes >= MIN_FREE_BYTES

/** The poll files of this feature and the push recorder files of the author. */
fun isScanLog(name: String): Boolean =
    (name.startsWith("fidscan-") && name.endsWith(".txt")) ||
        (name.startsWith("bydmate_fidrec_") && name.endsWith(".txt"))

/** A file the owner may pick as the parameter list: any `.txt` that is not a recording log. */
fun isListCandidate(name: String): Boolean = name.endsWith(".txt", ignoreCase = true) && !isScanLog(name)

/** The list file's text; an [IOException] with a short reason when it is missing or too big. */
internal fun readListFile(file: File): ScanListFile {
    if (!file.isFile) throw IOException("file not found: ${file.name}")
    if (file.length() > MAX_LIST_BYTES) throw IOException("file too big: ${file.name}")
    return ScanListFile(file.absolutePath, file.readText(Charsets.UTF_8))
}

fun zipName(stamp: String): String = "fidscan-logs-$stamp.zip"

/** Wall time of a log line: the date too, a recording spans days. */
internal fun wallTimeText(wallMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(wallMs))

/** First line of a poll file; a resumed file says so and how many bytes the recording had used. */
internal fun headerLine(versionName: String, started: String, fingerprint: String, resumed: Boolean, usedBefore: Long): String {
    val head = "# bydmate fid scan versionName=$versionName started=$started fingerprint=$fingerprint"
    return if (resumed) "$head resumed=true used_before=$usedBefore" else head
}

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
