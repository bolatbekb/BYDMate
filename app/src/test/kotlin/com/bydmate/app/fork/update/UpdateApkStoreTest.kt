package com.bydmate.app.fork.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Single-file cache: previous APK dropped on a new download, installed/stale one dropped on open. */
class UpdateApkStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = UpdateApkStore(File(tmp.root, UpdateApkStore.SUBDIR))

    private fun writeApk(file: File, bytes: Int = 1) {
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }

    @Test
    fun `begin wipes any previous file and targets a fresh part`() {
        val store = store()
        val dir = File(tmp.root, UpdateApkStore.SUBDIR)
        writeApk(File(dir, "BYDMate-4.5.0.3.apk"))

        val part = store.begin("4.5.0.4")

        assertEquals("BYDMate-4.5.0.4.apk.part", part.name)
        assertFalse("previous APK must be deleted", File(dir, "BYDMate-4.5.0.3.apk").exists())
        // begin only clears the store and hands back the target; the download then writes the part.
        assertTrue("store is empty until the download writes", dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `complete promotes part to the final apk`() {
        val store = store()
        val part = store.begin("4.5.0.4")
        part.writeBytes(ByteArray(3))

        val apk = store.complete("4.5.0.4")

        assertEquals("BYDMate-4.5.0.4.apk", apk.name)
        assertTrue(apk.exists())
        assertFalse(part.exists())
    }

    @Test
    fun `readyApk returns a newer downloaded version`() {
        val store = store()
        store.begin("4.5.0.4").writeBytes(ByteArray(3))
        store.complete("4.5.0.4")

        val ready = store.readyApk(currentVersion = "4.5.0.3")

        assertEquals("4.5.0.4", ready?.version)
        assertTrue(ready!!.file.exists())
    }

    @Test
    fun `readyApk deletes an already-installed apk and returns null`() {
        val store = store()
        store.begin("4.5.0.4").writeBytes(ByteArray(3))
        val apk = store.complete("4.5.0.4")

        // Next open after the user installed 4.5.0.4: the running version is now equal.
        val ready = store.readyApk(currentVersion = "4.5.0.4")

        assertNull(ready)
        assertFalse("installed APK must be deleted", apk.exists())
    }

    @Test
    fun `readyApk deletes an older stale apk`() {
        val store = store()
        store.begin("4.5.0.2").writeBytes(ByteArray(3))
        val apk = store.complete("4.5.0.2")

        val ready = store.readyApk(currentVersion = "4.5.0.4")

        assertNull(ready)
        assertFalse(apk.exists())
    }

    @Test
    fun `a half-downloaded part is not ready`() {
        val store = store()
        store.begin("4.5.0.4").writeBytes(ByteArray(3)) // never completed

        assertNull(store.readyApk(currentVersion = "4.5.0.3"))
    }

    @Test
    fun `no directory yet is not ready`() {
        assertNull(store().readyApk(currentVersion = "4.5.0.3"))
    }
}
