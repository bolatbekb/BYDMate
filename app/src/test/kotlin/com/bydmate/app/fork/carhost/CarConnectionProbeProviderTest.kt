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

    @After fun reset() = CarHostProbe.clearForTest()

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

    @Test fun `the provider is on only while some session runs and is not being stopped`() {
        assertFalse(providerShouldBeEnabled(emptyMap(), emptySet()))
        val notRunning = HostState.values().filter { it != HostState.RUNNING }
        assertFalse(providerShouldBeEnabled(notRunning.associateBy { it.name }, emptySet()))
        assertTrue(providerShouldBeEnabled(mapOf("a" to HostState.FAILED, "b" to HostState.RUNNING), emptySet()))
        assertTrue(providerShouldBeEnabled(mapOf("b" to HostState.RUNNING), setOf("a")))
        assertFalse(providerShouldBeEnabled(mapOf("b" to HostState.RUNNING), setOf("b")))
        assertFalse(
            providerShouldBeEnabled(mapOf("a" to HostState.RUNNING, "b" to HostState.RUNNING), setOf("a", "b")),
        )
    }

    @Test fun `stopAll with two running sessions keeps the provider off while the stops drain`() {
        CarHostProbe.resetOnStart(ctx)
        val a = CarAppTarget("p.a", "S", "A")
        val b = CarAppTarget("p.b", "S", "B")
        CarHostProbe.putRowForTest(a, HostState.RUNNING)
        CarHostProbe.putRowForTest(b, HostState.RUNNING)
        assertTrue(CarHostProbe.anyConnected)
        CarHostProbe.stopAll()
        assertFalse(CarHostProbe.anyConnected)
        assertEquals(0, state())
        // A's thread handles its stop first; B still reads RUNNING.
        CarHostProbe.applyState(a.pkg, HostState.DISCONNECTED)
        assertEquals(HostState.RUNNING, CarHostProbe.states.value.getValue(b.pkg).state)
        assertFalse(CarHostProbe.anyConnected)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, ctx.packageManager.getComponentEnabledSetting(name))
        CarHostProbe.applyState(b.pkg, HostState.DISCONNECTED)
        assertFalse(CarHostProbe.anyConnected)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, ctx.packageManager.getComponentEnabledSetting(name))
    }

    @Test fun `process start turns a provider left on back to the manifest default`() {
        val pm = ctx.packageManager
        pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        CarHostProbe.resetOnStart(ctx)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(name))
    }

    @Test fun `every provider flip reaches every discovered navigator, not only those with a row`() {
        CarHostProbe.resetOnStart(ctx)
        CarHostProbe.rememberTargets(listOf("p.x", "p.y"))
        CarHostProbe.putRowForTest(CarAppTarget("p.a", "S", "A"), HostState.RUNNING)
        val app = ctx as android.app.Application
        val sent = { org.robolectric.Shadows.shadowOf(app).broadcastIntents
            .filter { it.action == CarConnectionProbeProvider.ACTION_UPDATED }.map { it.`package` } }
        val already = sent().size
        CarHostProbe.stopAll()
        assertEquals(setOf("p.x", "p.y", "p.a"), sent().drop(already).toSet())
    }

    @Test fun `stopAll with no sessions leaves the provider at the manifest default (disabled)`() {
        CarHostProbe.resetOnStart(ctx)
        CarHostProbe.stopAll()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, ctx.packageManager.getComponentEnabledSetting(name))
        assertEquals(0, state())
    }
}
