package com.bydmate.app.fork.automation

import com.bydmate.app.data.automation.ActionValidationError
import com.bydmate.app.data.automation.RuleDraftValidator
import com.bydmate.app.data.automation.RuleJournal
import com.bydmate.app.data.local.entity.ActionDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YamusicCarTest {
    private fun action(payload: String?) = ActionDef("", "x", "yamusic_car", payload)

    @Test fun `every command maps to its exact address`() {
        val fixed = mapOf(
            "wave" to "yamusiccar://play/wave",
            "likes" to "yamusiccar://play/likes",
            "likes_shuffle" to "yamusiccar://play/likes?shuffle=1",
            "resume" to "yamusiccar://play/resume",
            "disk" to "yamusiccar://play/disk",
            "play" to "yamusiccar://control/play",
            "pause" to "yamusiccar://control/pause",
            "toggle" to "yamusiccar://control/toggle",
            "stop" to "yamusiccar://control/stop",
            "next" to "yamusiccar://control/next",
            "prev" to "yamusiccar://control/prev",
            "shuffle_on" to "yamusiccar://control/shuffle?on=1",
            "shuffle_off" to "yamusiccar://control/shuffle?on=0",
            "repeat_off" to "yamusiccar://control/repeat?mode=off",
            "repeat_all" to "yamusiccar://control/repeat?mode=all",
            "repeat_one" to "yamusiccar://control/repeat?mode=one",
            "open" to "yamusiccar://open",
        )
        fixed.forEach { (cmd, uri) -> assertEquals(cmd, uri, yamusicCarUri(cmd, null, false)) }
        assertEquals("yamusiccar://play/wave?station=artist:A-1_b", yamusicCarUri("wave_artist", "A-1_b", false))
        assertEquals("yamusiccar://play/wave?station=track:123", yamusicCarUri("wave_track", "123", false))
        assertEquals("yamusiccar://play/wave?station=album:x", yamusicCarUri("wave_album", "x", false))
        assertEquals("yamusiccar://play/wave?station=playlist:12345_3", yamusicCarUri("wave_playlist", "12345_3", false))
    }

    @Test fun `every command in the list is built`() {
        YAMUSIC_CAR_COMMANDS.forEach { c ->
            val id = when (c.id) { "wave_playlist" -> "1_2"; else -> if (c.idKind == YamusicCarId.NONE) null else "abc" }
            assertEquals(c.id, true, yamusicCarUri(c.id, id, false) != null)
        }
        assertEquals(21, YAMUSIC_CAR_COMMANDS.size)
    }

    @Test fun `show appends ui=show with the right separator and never to open`() {
        assertEquals("yamusiccar://play/wave?ui=show", yamusicCarUri("wave", null, true))
        assertEquals("yamusiccar://play/likes?shuffle=1&ui=show", yamusicCarUri("likes_shuffle", null, true))
        assertEquals("yamusiccar://play/wave?station=artist:a&ui=show", yamusicCarUri("wave_artist", "a", true))
        assertEquals("yamusiccar://control/next?ui=show", yamusicCarUri("next", null, true))
        assertEquals("yamusiccar://open", yamusicCarUri("open", null, true))
    }

    @Test fun `bad ids give null`() {
        val long65 = "a".repeat(65)
        listOf(null, "", " ", "a b", long65, "Иван", "a:b", "a/b").forEach {
            assertNull("artist $it", yamusicCarUri("wave_artist", it, false))
        }
        assertEquals("yamusiccar://play/wave?station=track:${"a".repeat(64)}", yamusicCarUri("wave_track", "a".repeat(64), false))
    }

    @Test fun `bad playlist ids give null`() {
        assertNull(yamusicCarUri("wave_playlist", "abc_3", false))
        assertNull(yamusicCarUri("wave_playlist", "123_1234567890123", false))
        assertNull(yamusicCarUri("wave_playlist", "123_", false))
        assertNull(yamusicCarUri("wave_playlist", "_3", false))
        assertNull(yamusicCarUri("wave_playlist", "123", false))
        assertNull(yamusicCarUri("wave_playlist", null, false))
        assertEquals("yamusiccar://play/wave?station=playlist:1_123456789012", yamusicCarUri("wave_playlist", "1_123456789012", false))
    }

    @Test fun `unknown command gives null`() {
        assertNull(yamusicCarUri("nope", null, false))
        assertNull(yamusicCarUri("", null, false))
    }

    @Test fun `payload is turned into the address`() {
        assertEquals("yamusiccar://play/wave?station=artist:ab&ui=show",
            yamusicCarUriFromPayload("""{"command":"wave_artist","id":"ab","show":true}"""))
        assertEquals("yamusiccar://play/wave?station=playlist:7_9",
            yamusicCarUriFromPayload("""{"command":"wave_playlist","uid":"7","kind":"9"}"""))
        assertNull(yamusicCarUriFromPayload("""{"command":"wave_artist"}"""))
        assertNull(yamusicCarUriFromPayload("not json"))
        assertNull(yamusicCarUriFromPayload(null))
    }

    @Test fun `validator rejects a bad id and accepts a good one`() {
        assertEquals(ActionValidationError.YamusicCarInvalid(1),
            RuleDraftValidator.validateActions(listOf(action("""{"command":"wave_artist","id":"a b"}"""))))
        assertEquals(ActionValidationError.YamusicCarInvalid(1),
            RuleDraftValidator.validateActions(listOf(action("""{"command":"bogus"}"""))))
        assertNull(RuleDraftValidator.validateActions(listOf(action("""{"command":"wave_artist","id":"ab"}"""))))
        assertNull(RuleDraftValidator.validateActions(listOf(action("""{"command":"next","show":true}"""))))
    }

    @Test fun `journal shows the command only, never an id`() {
        val shown = RuleJournal.shownPayload(action("""{"command":"wave_artist","id":"SECRET","show":true}"""))
        assertEquals("""{"command":"wave_artist"}""", shown)
    }
}
