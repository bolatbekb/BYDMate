package com.bydmate.app.fork.cells.data

import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import com.bydmate.app.fork.cells.helper.CellTapDaemon
import com.bydmate.app.fork.cells.helper.FakeGbDevice
import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellReading
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.fixtureLines
import com.bydmate.app.fork.cells.stream.hex
import com.bydmate.app.fork.cells.stream.msOfDay
import com.bydmate.app.fork.cells.stream.readingOf
import com.bydmate.app.helper.HelperBinderProtocol
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app adapter against the real daemon handler, and every way the binder can let it down. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HelperCellSourceTest {

    private var now = 1_000L
    private val device = FakeGbDevice()

    @Before fun setUp() {
        CellTapDaemon.resetForTest()
        CellTapDaemon.autoTick = false
        CellTapDaemon.clock = { now }
        CellTapDaemon.deviceFactory = { device }
    }

    @After fun tearDown() = CellTapDaemon.resetForTest()

    @Test fun `snapshot round trips through the binder to the daemon`() = runBlocking {
        val binder = daemonBinder()
        val source = HelperCellSource({ binder })

        val listen = source.listen(true) as SourceReply.Ok
        assertEquals(CellTx.REG_OK, listen.value.registration)
        feedFixtureThroughDevice()

        val snap = source.snapshot() as SourceReply.Ok
        assertTrue(snap.value.listening)
        assertEquals(1_000L, snap.value.listeningSinceMs)
        assertTrue(readingOf(snap.value, 184, 24) is CellReading.Complete)

        assertEquals(SourceReply.Ok(CellListenReply(CellTx.REG_OFF, "")), source.listen(false))
        assertEquals(1, device.unregisterCalls)
    }

    @Test fun `no binder is HelperMissing`() = runBlocking {
        assertEquals(SourceReply.HelperMissing, HelperCellSource({ null }).snapshot())
        assertEquals(SourceReply.HelperMissing, HelperCellSource({ null }).listen(true))
        val dead = mockk<IBinder>(relaxed = true)
        every { dead.isBinderAlive } returns false
        assertEquals(SourceReply.HelperMissing, HelperCellSource({ dead }).snapshot())
        assertEquals(SourceReply.HelperMissing, HelperCellSource({ error("lookup failed") }).snapshot())
    }

    @Test fun `transact false is HelperOutdated`() = runBlocking {
        val old = binderAnswering { _, _, _ -> false }
        assertEquals(SourceReply.HelperOutdated, HelperCellSource({ old }).snapshot())
        assertEquals(SourceReply.HelperOutdated, HelperCellSource({ old }).listen(true))
    }

    @Test fun `a daemon that does not know the code is HelperOutdated`() = runBlocking {
        val older = binderAnswering { _, _, reply -> reply?.writeInt(CellTx.STATUS_UNKNOWN_CODE); true }
        assertEquals(SourceReply.HelperOutdated, HelperCellSource({ older }).snapshot())
    }

    @Test fun `a throwing binder is Failed and never throws`() = runBlocking {
        val broken = binderAnswering { _, _, _ -> throw IllegalStateException("boom") }
        val reply = HelperCellSource({ broken }).snapshot()
        assertTrue(reply.toString(), reply is SourceReply.Failed && reply.detail.contains("boom"))
    }

    @Test fun `a failed status or an unreadable reply is Failed`() = runBlocking {
        val failing = binderAnswering { _, _, reply -> reply?.writeInt(CellTx.STATUS_FAILED); true }
        assertTrue(HelperCellSource({ failing }).snapshot() is SourceReply.Failed)
        val short = binderAnswering { _, _, reply -> reply?.writeInt(CellTx.STATUS_OK); reply?.writeLong(1L); true }
        assertTrue(HelperCellSource({ short }).snapshot() is SourceReply.Failed)
        val empty = binderAnswering { _, _, _ -> true }
        assertTrue(HelperCellSource({ empty }).listen(true) is SourceReply.Failed)
    }

    @Test fun `a dead binder is dropped and looked up again`() = runBlocking {
        var lookups = 0
        val dying = binderAnswering { _, _, _ -> throw DeadObjectException() }
        val source = HelperCellSource({ lookups++; dying })

        assertEquals(SourceReply.HelperMissing, source.snapshot())
        assertEquals(SourceReply.HelperMissing, source.snapshot())
        assertEquals(2, lookups)
    }

    @Test fun `a live binder is looked up once`() = runBlocking {
        var lookups = 0
        val binder = daemonBinder()
        val source = HelperCellSource({ lookups++; binder })
        source.listen(true); source.snapshot(); source.snapshot()
        assertEquals(1, lookups)
    }

    /** A binder that hands every call to the daemon's handler, as HelperDaemon's dispatch line does. */
    private fun daemonBinder(): IBinder = binderAnswering { code, data, reply ->
        data.setDataPosition(0)
        data.enforceInterface(HelperBinderProtocol.DESCRIPTOR)
        CellTapDaemon.onTransact(code, data, reply)
        true
    }

    private fun binderAnswering(answer: (Int, Parcel, Parcel?) -> Boolean): IBinder {
        val binder = mockk<IBinder>(relaxed = true)
        every { binder.isBinderAlive } returns true
        every { binder.transact(any(), any(), any(), any()) } answers {
            val reply = thirdArg<Parcel?>()
            answer(firstArg(), secondArg(), reply).also { reply?.setDataPosition(0) }
        }
        return binder
    }

    private fun feedFixtureThroughDevice() {
        fixtureLines().forEach { line ->
            val parts = line.trim().split(" ")
            now = msOfDay(parts[0])
            device.fire(parts[2].removePrefix("fid=").toLong(16).toInt(), hex(parts[4]))
        }
    }
}
