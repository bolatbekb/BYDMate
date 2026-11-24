package com.bydmate.app.fork.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Release selection by the «nightly» opt-in: stable (/releases/latest) vs the full list. */
class ReleasePickerTest {

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        apk: String? = "https://example.invalid/BYDMate-$tag.apk",
        body: String = "notes $tag",
    ): String {
        val assets = if (apk == null) "[]" else
            """[{"name":"BYDMate-$tag.apk","browser_download_url":"$apk"}]"""
        return """{"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,""" +
            """"body":"$body","assets":$assets}"""
    }

    private fun array(vararg releases: String): String = releases.joinToString(",", "[", "]")

    // ---- nightly OFF: /releases/latest single object ----

    @Test
    fun `fromLatest selects the single latest object`() {
        val picked = ReleasePicker.fromLatest(release("v4.5.0.4"))
        assertEquals("4.5.0.4", picked.version)
        assertEquals("https://example.invalid/BYDMate-v4.5.0.4.apk", picked.downloadUrl)
        assertEquals("notes v4.5.0.4", picked.releaseNotes)
    }

    @Test
    fun `fromLatest throws on no apk asset (current behaviour)`() {
        assertThrows(IllegalStateException::class.java) {
            ReleasePicker.fromLatest(release("4.5.0.4", apk = null))
        }
    }

    @Test
    fun `fromLatest throws on malformed JSON`() {
        assertThrows(Exception::class.java) { ReleasePicker.fromLatest("not json") }
    }

    // ---- nightly ON: /releases array ----

    @Test
    fun `fromList picks a prerelease newer than the latest stable`() {
        val json = array(
            release("4.5.0.6", prerelease = true),
            release("4.5.0.5", prerelease = false),
        )
        val picked = ReleasePicker.fromList(json)!!
        assertEquals("4.5.0.6", picked.version)
        assertEquals("https://example.invalid/BYDMate-4.5.0.6.apk", picked.downloadUrl)
    }

    @Test
    fun `fromList excludes drafts even when they are the newest`() {
        val json = array(
            release("4.6.0.0", draft = true, prerelease = true),
            release("4.5.0.5", prerelease = false),
        )
        val picked = ReleasePicker.fromList(json)!!
        assertEquals("4.5.0.5", picked.version)
    }

    @Test
    fun `fromList picks the newest by version regardless of list order`() {
        val json = array(
            release("4.5.0.4"),
            release("4.5.0.10"),
            release("4.5.0.9"),
        )
        assertEquals("4.5.0.10", ReleasePicker.fromList(json)!!.version)
    }

    @Test
    fun `fromList returns null on an empty array`() {
        assertNull(ReleasePicker.fromList("[]"))
    }

    @Test
    fun `fromList returns null when nothing installable has an apk`() {
        val json = array(
            release("4.5.0.6", prerelease = true, apk = null),
            release("4.5.0.5", apk = null),
        )
        assertNull(ReleasePicker.fromList(json))
    }

    @Test
    fun `fromList skips a release without an apk and keeps the newest installable one`() {
        val json = array(
            release("4.5.0.6", prerelease = true, apk = null),
            release("4.5.0.5"),
        )
        assertEquals("4.5.0.5", ReleasePicker.fromList(json)!!.version)
    }

    @Test
    fun `fromList throws on malformed JSON`() {
        assertThrows(Exception::class.java) { ReleasePicker.fromList("not json") }
    }
}
