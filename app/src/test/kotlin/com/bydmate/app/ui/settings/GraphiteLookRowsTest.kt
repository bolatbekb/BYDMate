package com.bydmate.app.ui.settings

import com.bydmate.app.ui.home.HomeStyle
import com.bydmate.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fork: the Graphite look rows in Settings › Внешний вид. They show with the Graphite home or the
 * «Graphite» skin, which follows them; «Плеер слева» moves the Graphite home's player only.
 */
class GraphiteLookRowsTest {
    @Test fun theLookShowsWithTheGraphiteHomeOrTheGraphiteSkin() {
        Skins.ALL.forEach { assertTrue(it.id, graphiteLookShown(HomeStyle.GRAPHITE, it)) }
        HomeStyle.entries.forEach { assertTrue(it.name, graphiteLookShown(it, Skins.GRAPHITE_HOME)) }
        HomeStyle.entries.filter { it != HomeStyle.GRAPHITE }.forEach { home ->
            Skins.ALL.filter { !it.isGraphite }.forEach { assertFalse("$home ${it.id}", graphiteLookShown(home, it)) }
        }
    }

    @Test fun mediaLeftShowsWithTheGraphiteHomeOnly() {
        assertEquals(setOf(HomeStyle.GRAPHITE), HomeStyle.entries.filter { mediaLeftShown(it) }.toSet())
    }
}
