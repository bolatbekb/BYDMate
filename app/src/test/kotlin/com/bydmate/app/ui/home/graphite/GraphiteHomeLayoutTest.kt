package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteHomeLayoutTest {
    @Test fun bottomCardsShareOneHeight() {
        assertEquals(366f, bottomCardHeight(hasPmRow = true))
        assertEquals(290f, bottomCardHeight(hasPmRow = false))
    }

    @Test fun swapPutsMediaLeft() {
        assertEquals(SideCard.RANGE to SideCard.MEDIA, topCards(mediaLeft = false))
        assertEquals(SideCard.MEDIA to SideCard.RANGE, topCards(mediaLeft = true))
    }

    @Test fun sideColumnsKeepTheCentreWidth() {
        assertEquals(790f + 820f, 2 * SIDE_COLUMN)
    }
}
