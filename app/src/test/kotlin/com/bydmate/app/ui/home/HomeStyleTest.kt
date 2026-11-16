package com.bydmate.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeStyleTest {
    @Test fun idsRoundTrip() {
        for (s in HomeStyle.entries) assertSame(s, HomeStyle.fromId(s.id))
    }

    @Test fun unknownIsStandard() {
        assertSame(HomeStyle.STANDARD, HomeStyle.fromId(null))
        assertSame(HomeStyle.STANDARD, HomeStyle.fromId("radio"))
    }

    @Test fun theRemovedRsStackOpensCDrive() {
        assertSame(HomeStyle.CDRIVE, HomeStyle.fromId("hybrid"))
    }

    @Test fun rsIsGone() {
        assertEquals(listOf("standard", "kenwood", "onkyo", "cdrive", "graphite"), HomeStyle.entries.map { it.id })
    }
}
