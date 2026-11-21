package com.bydmate.app.fork.fidscan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.vehicle.DumpFidsResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test fun `a session status keeps what the zip button owns`() {
        val old = FidScanStatus(active = true, error = "old", packing = true, lastZip = "zip", packError = "pack")
        val merged = keepPackFields(old, FidScanStatus(fileName = "f", error = "new"))
        assertEquals(
            FidScanStatus(fileName = "f", error = "new", packing = true, lastZip = "zip", packError = "pack"),
            merged,
        )
    }

    @Test fun `withReason appends a message only when there is one`() {
        assertEquals("no access (EACCES)", withReason("no access", "EACCES"))
        assertEquals("no access", withReason("no access", null))
        assertEquals("no access", withReason("no access", " "))
        assertEquals("no access (a b)", withReason("no access", "a\nb"))
    }

    @Test fun `a dump read error carries its detail to the log`() {
        val e = assertThrows(IOException::class.java) {
            catalogDump(DumpFidsResult.ReadError("status=-1 at chunk 0"))
        }
        assertTrue(e.message.orEmpty().contains("status=-1 at chunk 0"))
        assertEquals("dump", catalogDump(DumpFidsResult.Success("dump")))
        assertNull(catalogDump(DumpFidsResult.BinderAbsent))
    }
}
