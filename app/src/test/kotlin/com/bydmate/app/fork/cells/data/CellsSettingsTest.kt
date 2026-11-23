package com.bydmate.app.fork.cells.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CellsSettingsTest {
    private lateinit var ctx: Context
    private var now = 1_000L

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        listOf(false, true).forEach {
            ctx.getSharedPreferences(CellsSettings.prefsName(it), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun settings(demo: Boolean = false) =
        CellsSettings(ctx.getSharedPreferences(CellsSettings.prefsName(demo), Context.MODE_PRIVATE)) { now }

    @Test fun `defaults are no car, collection off, 30 s, what to do`() {
        val c = settings().config.value
        assertEquals(CarProfile.NONE, c.profile)
        assertEquals(false, c.collect)
        assertEquals(30, c.intervalSec)
        assertEquals(CheckView.WHAT_TO_DO, c.view)
        assertEquals(0L, settings().lastFull100WallMs)
    }

    @Test fun `unknown profile id reads as NONE`() {
        ctx.getSharedPreferences(CellsSettings.prefsName(false), Context.MODE_PRIVATE)
            .edit().putString("car_profile", "zzz").commit()
        assertEquals(CarProfile.NONE, settings().config.value.profile)
        assertEquals(CarProfile.NONE, CarProfile.byId(null))
        assertEquals(CarProfile.LEOPARD5_318, CarProfile.byId("leopard5_318"))
    }

    @Test fun `interval outside the list reads as 30`() {
        ctx.getSharedPreferences(CellsSettings.prefsName(false), Context.MODE_PRIVATE)
            .edit().putInt("interval_sec", 7).commit()
        assertEquals(30, settings().config.value.intervalSec)
        val s = settings()
        s.setIntervalSec(300)
        assertEquals(300, s.config.value.intervalSec)
        s.setIntervalSec(11)
        assertEquals(30, s.config.value.intervalSec)
    }

    @Test fun `demo uses its own prefs file`() {
        assertNotEquals(CellsSettings.prefsName(true), CellsSettings.prefsName(false))
        val real = settings(false)
        real.setProfile(CarProfile.LEOPARD5_318)
        real.setCollect(true)
        val demo = settings(true)
        assertEquals(CarProfile.NONE, demo.config.value.profile)
        assertEquals(false, demo.config.value.collect)
        assertEquals(CarProfile.LEOPARD5_318, settings(false).config.value.profile)
    }

    @Test fun `switching collection off clears the anchor`() {
        val s = settings()
        s.setCollect(true)
        assertEquals(0L, s.anchorClearedAtMs)
        now = 5_000L
        s.setCollect(false)
        assertEquals(5_000L, s.anchorClearedAtMs)
        assertEquals(false, s.config.value.collect)
    }
}
