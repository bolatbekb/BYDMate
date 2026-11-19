package com.bydmate.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class RetroTabsTest {
    /** The app's bottom bar stays under the standard and the Graphite home; the retro stands draw their own tabs. */
    @Test fun onlyTheRetroStandsDrawTheirOwnTabs() {
        assertEquals(
            setOf(HomeStyle.KENWOOD, HomeStyle.ONKYO, HomeStyle.CDRIVE),
            HomeStyle.entries.filter { drawsOwnTabs(it) }.toSet(),
        )
    }
}
