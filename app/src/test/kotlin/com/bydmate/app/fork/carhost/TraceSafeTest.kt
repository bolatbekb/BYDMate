package com.bydmate.app.fork.carhost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TraceSafeTest {
    private val pkg = "org.example.navi"

    @Test fun `the target package and foreign class names are masked`() {
        val line = "java.lang.IllegalStateException: Unknown host at org.example.navi.car.NaviSession\$1.run" +
            " via net.other.lib.Thing.call"
        val out = traceSafe(line, pkg)!!
        assertEquals("java.lang.IllegalStateException: Unknown host at <pkg>.car.NaviSession\$1.run via <pkg>", out)
        assertFalse(out.contains("example"))
        assertFalse(out.contains("other"))
    }

    @Test fun `own and platform names stay`() {
        val line = "androidx.car.app.serialization.BundlerException in android.os.Parcel from com.bydmate.app"
        assertEquals(line, traceSafe(line, pkg))
        assertEquals("androidx.car.app.action.NAVIGATE", traceSafe("androidx.car.app.action.NAVIGATE", pkg))
    }

    @Test fun `an obfuscated foreign class is masked, plain words and short names stay`() {
        assertEquals("NPE at <pkg>(Unknown Source)", traceSafe("NPE at a.b.c.d.b(Unknown Source)", pkg))
        assertEquals("host type app", traceSafe("host type app", pkg))
        assertEquals("v1.2 x.y", traceSafe("v1.2 x.y", pkg))
        assertNull(traceSafe(null, pkg))
    }
}
