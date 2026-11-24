package com.bydmate.app.fork.update

import android.content.Context
import java.io.File

/** A downloaded update sitting in the cache, ready to install. */
data class ReadyApk(val version: String, val file: File)

/**
 * The single-file APK cache for background updates. Keeps only one file — the latest downloaded
 * version — in a private cache sub-directory. The previous APK is deleted when a new download
 * starts ([begin]); the installed one is dropped on the next open ([readyApk] removes an APK whose
 * version is no longer newer than the running app). fork-local.
 *
 * Downloads over any network land here, not in public Downloads, so nothing is user-visible and
 * nothing survives a cache clear. The dir is wired into the FileProvider via `res/xml/file_paths`.
 */
class UpdateApkStore(private val dir: File) {

    companion object {
        /** Sub-directory of the app cache that holds the one ready APK. */
        const val SUBDIR = "update"
        private val NAME = Regex("^BYDMate-(\\d+(?:\\.\\d+)*)\\.apk$")

        fun forContext(context: Context): UpdateApkStore =
            UpdateApkStore(File(context.cacheDir, SUBDIR))
    }

    private fun apkName(version: String) = "BYDMate-$version.apk"
    private fun partName(version: String) = "BYDMate-$version.apk.part"

    /** Start a fresh download: wipe the dir (single-file store) and return the `.part` target. */
    fun begin(version: String): File {
        dir.mkdirs()
        wipe()
        return File(dir, partName(version))
    }

    /** Promote the finished `.part` to the final `.apk` atomically; returns the `.apk`. */
    fun complete(version: String): File {
        val part = File(dir, partName(version))
        val apk = File(dir, apkName(version))
        if (apk.exists()) apk.delete()
        part.renameTo(apk)
        return apk
    }

    /**
     * The single ready APK, if one is present AND strictly newer than [currentVersion]. An APK
     * whose version is no longer newer is the one just installed (or a stale leftover): it is
     * deleted here and `null` returned. A half-downloaded `.part` is not «ready» — it is ignored.
     */
    fun readyApk(currentVersion: String): ReadyApk? {
        val apk = dir.listFiles()?.firstOrNull { it.isFile && NAME.matches(it.name) } ?: return null
        val version = NAME.matchEntire(apk.name)?.groupValues?.get(1) ?: return null
        return if (Versions.isNewer(version, currentVersion)) {
            ReadyApk(version, apk)
        } else {
            apk.delete()
            null
        }
    }

    /** Delete everything in the store (both `.apk` and `.part`). Never throws. */
    fun wipe() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }
}
