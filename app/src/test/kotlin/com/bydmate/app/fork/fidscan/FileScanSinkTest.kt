package com.bydmate.app.fork.fidscan

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// fork: a failing file must be seen by the loop (ioError), not swallowed.
@RunWith(RobolectricTestRunner::class)
class FileScanSinkTest {

    private class Failing : OutputStream() {
        override fun write(b: Int) = throw IOException("No space left on device")
        override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("No space left on device")
        override fun flush() = throw IOException("No space left on device")
    }

    @Test fun `a healthy sink counts bytes and has no io error`() {
        val sink = FileScanSink(ByteArrayOutputStream())
        sink.write("V 1 A i=1 d=1.0")
        sink.flush()
        assertEquals(16L, sink.bytes)
        assertNull(sink.ioError)
    }

    @Test fun `a failing write is stored and rethrown and bytes stop growing`() {
        val sink = FileScanSink(Failing())
        try {
            sink.write("x")
            throw AssertionError("expected IOException")
        } catch (e: IOException) {
            assertEquals("No space left on device", e.message)
        }
        assertNotNull(sink.ioError)
        assertEquals(0L, sink.bytes)
    }

    @Test fun `a failing flush is stored`() {
        val sink = FileScanSink(Failing())
        sink.flush()
        assertTrue(sink.ioError?.message == "No space left on device")
        sink.close() // must not throw
    }
}
