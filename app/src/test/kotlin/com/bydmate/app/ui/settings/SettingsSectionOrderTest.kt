package com.bydmate.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSectionOrderTest {
    @Test
    fun appearanceComesRightBeforeApp() {
        val names = SettingsSection.values().map { it.name }
        assertEquals(names.indexOf("APP") - 1, names.indexOf("APPEARANCE"))
    }
}
