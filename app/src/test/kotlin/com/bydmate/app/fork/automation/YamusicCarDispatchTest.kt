package com.bydmate.app.fork.automation

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.automation.ActionDispatcher
import com.bydmate.app.data.automation.testDispatcher
import com.bydmate.app.data.local.LocalePreferences
import com.bydmate.app.data.local.entity.ActionDef
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.data.vehicle.VehicleApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class YamusicCarDispatchTest {
    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private val dispatcher: ActionDispatcher

    init {
        LocalePreferences(ApplicationProvider.getApplicationContext()).setLanguage("ru")
        every { context.packageManager } returns packageManager
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        dispatcher = testDispatcher(
            mockk<VehicleApi>(relaxed = true), mockk<HelperClient>(relaxed = true), context,
            appStrings = com.bydmate.app.util.AppStrings(ApplicationProvider.getApplicationContext()),
        )
    }

    private fun action(payload: String?) = ActionDef("", "x", "yamusic_car", payload)

    @Test fun `installed player gets a VIEW intent for its package`() {
        every { packageManager.getPackageInfo("app.yamusic.car", 0) } returns PackageInfo()
        val captured = slot<Intent>()
        every { context.startActivity(capture(captured)) } returns Unit
        val result = runBlocking { dispatcher.dispatch(action("""{"command":"wave_artist","id":"ab","show":true}"""), null) }
        assertTrue(result.success)
        assertEquals(Intent.ACTION_VIEW, captured.captured.action)
        assertEquals("yamusiccar://play/wave?station=artist:ab&ui=show", captured.captured.data.toString())
        assertEquals("app.yamusic.car", captured.captured.`package`)
        assertTrue(captured.captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun `missing player is a failed result and nothing starts`() {
        every { packageManager.getPackageInfo("app.yamusic.car", 0) } throws PackageManager.NameNotFoundException()
        val result = runBlocking { dispatcher.dispatch(action("""{"command":"next"}"""), null) }
        assertFalse(result.success)
        assertEquals("Я.Музыка Car не установлена", result.reason)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test fun `bad command or id is a failed result, no crash`() {
        every { packageManager.getPackageInfo("app.yamusic.car", 0) } returns PackageInfo()
        listOf("""{"command":"bogus"}""", """{"command":"wave_track","id":"a b"}""", "garbage", null).forEach {
            val result = runBlocking { dispatcher.dispatch(action(it), null) }
            assertFalse(it, result.success)
            assertTrue(it, !result.reason.isNullOrBlank())
        }
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
