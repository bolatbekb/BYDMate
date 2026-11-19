package com.bydmate.app.ui.home

import com.bydmate.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class RetroTabsTest {
    @Test fun graphiteHomeMenuStartsWithItsOwnHomeKey() {
        assertEquals(Screen.entries.toList(), retroTabScreens(HomeStyle.GRAPHITE))
        assertEquals(Screen.Dashboard, retroTabScreens(HomeStyle.GRAPHITE).first())
    }

    @Test fun retroStandsKeepEveryTabButTheirOwn() {
        HomeStyle.entries.filter { it != HomeStyle.STANDARD && it != HomeStyle.GRAPHITE }.forEach {
            assertEquals(it.name, Screen.entries.filter { s -> s != Screen.Dashboard }, retroTabScreens(it))
        }
    }
}
