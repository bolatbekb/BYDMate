package com.bydmate.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaActionsTest {
    private fun a(action: String, name: String? = null, active: Boolean? = null, mode: Int? = null) =
        MediaAction(action, name, active, mode)

    @Test fun openPlanCombinations() {
        assertEquals(OpenPlan.SESSION, openPlan(true, true))
        assertEquals(OpenPlan.SESSION, openPlan(true, false))
        assertEquals(OpenPlan.LAUNCH, openPlan(false, true))
        assertEquals(OpenPlan.NONE, openPlan(false, false))
    }

    @Test fun exactMatchWins() {
        val list = listOf(a("my_like_heart", "Мне нравится"), a("like", "Like"))
        assertEquals("like", pickLike(list)?.action)
    }

    @Test fun likeIsNotDislike() {
        val list = listOf(a("dislike", "Не нравится"), a("x", "Мне нравится"))
        assertEquals("x", pickLike(list)?.action)
        assertEquals("dislike", pickDislike(list)?.action)
    }

    @Test fun mneNravitsyaIsNotExcluded() {
        assertEquals("a1", pickLike(listOf(a("a1", "Мне нравится")))?.action)
    }

    @Test fun caseInsensitive() {
        assertEquals("LIKE", pickAction(listOf(a("LIKE")), "like", listOf("zzz"), emptyList())?.action)
    }

    @Test fun emptyListGivesNull() {
        assertNull(pickLike(emptyList()))
        assertNull(pickDislike(emptyList()))
        assertNull(pickShuffle(emptyList()))
        assertNull(pickRepeat(emptyList()))
    }

    @Test fun exclusionAfterQuoteCounts() {
        assertNull(pickLike(listOf(a("q", "«Не нравится»"))))
    }

    @Test fun likeOnlyPlayerHasNoDislike() {
        assertNull(pickDislike(listOf(a("x", "Мне нравится"))))
    }

    @Test fun dislikeNamedNeNravitsya() {
        val list = listOf(a("y", "Не нравится"))
        assertEquals("y", pickDislike(list)?.action)
        assertNull(pickLike(list))
    }

    @Test fun removeFromLikedIsLikeNotDislike() {
        val list = listOf(a("z", "Убрать из «Мне нравится»"))
        assertEquals("z", pickLike(list)?.action)
        assertNull(pickDislike(list))
    }

    @Test fun likedOfCases() {
        assertEquals(true, likedOf(a("like", "Like", active = true)))
        assertEquals(false, likedOf(a("like", "Убрать", active = false)))
        assertEquals(true, likedOf(a("like", "Убрать из «Мне нравится»")))
        assertEquals(true, likedOf(a("like", "Remove from Liked")))
        assertEquals(false, likedOf(a("like", "Мне нравится")))
        assertEquals(false, likedOf(a("like", "Like")))
        assertNull(likedOf(a("like", "Heart")))
        assertNull(likedOf(a("like", null)))
        assertNull(likedOf(null))
    }

    @Test fun shuffleAndRepeatState() {
        val list = listOf(a("shuffle", active = true), a("repeat", mode = 1))
        assertEquals(Triple(null, true, 1), stateOf(list))
        assertEquals(Triple<Boolean?, Boolean?, Int?>(null, null, null), stateOf(listOf(a("shuffle"), a("repeat"))))
        assertEquals(true, stateOf(listOf(a("like", active = true))).first)
    }
}
