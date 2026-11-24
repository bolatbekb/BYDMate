package com.bydmate.app.fork.carhost

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The connection state the car-app library reads (`CarConnection`, column `CarConnectionState`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarConnectionProbeProviderTest {
    private val uri = Uri.parse("content://androidx.car.app.connection")
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val name get() = ComponentName(ctx, CarConnectionProbeProvider::class.java)

    @After fun reset() = CarHostProbe.setConnectedForTest(null)

    private fun state(): Int {
        val provider = Robolectric.buildContentProvider(CarConnectionProbeProvider::class.java).create().get()
        val c = requireNotNull(provider.query(uri, arrayOf("CarConnectionState"), null, null, null))
        c.use {
            val col = it.getColumnIndex("CarConnectionState")
            assertTrue("column CarConnectionState", col >= 0)
            assertEquals(1, it.count)
            assertTrue(it.moveToNext())
            return it.getInt(col)
        }
    }

    @Test fun `not connected reads 0`() {
        CarHostProbe.setConnectedForTest(false)
        assertEquals(0, state())
    }

    @Test fun `no session at all reads 0`() {
        assertEquals(0, state())
    }

    @Test fun `enabled and a running session reads 2 (projection)`() {
        ctx.packageManager.setComponentEnabledSetting(
            name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
        )
        CarHostProbe.setConnectedForTest(true)
        assertEquals(2, state())
    }

    @Test fun `the provider is on only while some session runs`() {
        assertFalse(providerShouldBeEnabled(emptyList()))
        assertFalse(providerShouldBeEnabled(HostState.values().filter { it != HostState.RUNNING }))
        assertTrue(providerShouldBeEnabled(listOf(HostState.FAILED, HostState.RUNNING)))
        assertTrue(providerShouldBeEnabled(listOf(HostState.RUNNING)))
    }

    @Test fun `process start turns a provider left on back off`() {
        val pm = ctx.packageManager
        pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        CarHostProbe.resetOnStart(ctx)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(name))
    }

    @Test fun `stopAll with no sessions leaves the provider disabled`() {
        CarHostProbe.resetOnStart(ctx)
        CarHostProbe.stopAll()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, ctx.packageManager.getComponentEnabledSetting(name))
        assertEquals(0, state())
    }
}
