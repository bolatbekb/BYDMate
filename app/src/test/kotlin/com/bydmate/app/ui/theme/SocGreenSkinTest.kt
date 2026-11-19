package com.bydmate.app.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import com.bydmate.app.ui.components.socColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * fork: the SOC gauge's normal level (SocGreen) follows the skin accent (the owner's request); the
 * consumption traffic light keeps socGreen. Classic is unchanged: its accent is the author's green.
 */
class SocGreenSkinTest {
    private val authorGreen = Color(0xFF4ADE80) // the author's SocGreen and AccentGreen

    @Test fun classicGaugeGreenIsTheAuthors() {
        assertEquals(authorGreen, Skins.CLASSIC.palette.accentGreen)
        assertEquals(Skins.CLASSIC.palette.socGreen, Skins.CLASSIC.palette.accentGreen)
        // no test selects another skin: the tokens read Classic here
        assertEquals(Skins.CLASSIC, SkinState.selected)
        assertEquals(authorGreen, SocGreen)
        assertEquals(authorGreen, socColor(80))
        assertEquals(authorGreen, ConsumptionGood)
    }

    @Test fun theGaugeTakesTheAccentAndTheConsumptionLightKeepsSocGreen() {
        val cockpit = Skins.COCKPIT.palette
        assertNotEquals("Cockpit's accent is not its socGreen", cockpit.accentGreen, cockpit.socGreen)
        // SkinState.selected has a private setter (Skins.select needs a Context): set its state directly
        @Suppress("UNCHECKED_CAST")
        val state = SkinState::class.java.getDeclaredField("selected\$delegate")
            .apply { isAccessible = true }.get(SkinState) as MutableState<Skin>
        val before = state.value
        try {
            state.value = Skins.COCKPIT
            assertEquals(cockpit.accentGreen, SocGreen)
            assertEquals(cockpit.accentGreen, socColor(80))
            assertEquals(cockpit.socGreen, ConsumptionGood)
            assertEquals("the warnings stay", cockpit.socYellow, SocYellow)
            assertEquals(cockpit.socRed, SocRed)
        } finally {
            state.value = before
        }
    }
}
