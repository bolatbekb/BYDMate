package com.bydmate.app.fork.fidscan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// fork: the saved state of the fid scan (resume after a quickboot kill) and the idle status.
@RunWith(RobolectricTestRunner::class)
class FidScanStateTest {

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(FID_SCAN_PREFS, Context.MODE_PRIVATE)

    @Test fun `defaults are inactive and zero`() {
        val p = prefs().also { it.edit().clear().commit() }
        assertEquals(PersistedScan(active = false, usedBytes = 0L), persistedState(p))
    }

    @Test fun `save and read round trip`() {
        val p = prefs()
        savePersisted(p, active = true, usedBytes = 123_456_789_012L)
        assertEquals(PersistedScan(true, 123_456_789_012L), persistedState(p))
        savePersisted(p, active = false, usedBytes = 0L)
        assertEquals(PersistedScan(false, 0L), persistedState(p))
    }

    @Test fun `idle status is off with no file and no error`() {
        val s = FidScanStatus()
        assertFalse(s.active)
        assertNull(s.fileName)
        assertNull(s.error)
        assertEquals(0L, s.usedBytes)
        assertEquals(0, s.sweeps)
        assertFalse(s.pushRunning)
        assertTrue(FID_SCAN_PREFS.isNotEmpty())
    }
}
