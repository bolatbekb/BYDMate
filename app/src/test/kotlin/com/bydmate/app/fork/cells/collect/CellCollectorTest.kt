package com.bydmate.app.fork.cells.collect

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.demo.DemoCar
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellSource
import com.bydmate.app.fork.cells.data.CellsDatabase
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.SourceReply
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.MAX_CELL_PAGES
import com.bydmate.app.fork.cells.stream.MAX_PROBE_PAGES
import com.bydmate.app.fork.cells.stream.feedFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor

private const val EL0 = 100_000_000L
private const val WALL0 = 1_790_000_000_000L
private const val MIN = 60_000L
private const val HOUR = 60 * MIN
private const val DAY = 24 * HOUR

private val FIXTURE: CellStreamSnapshot by lazy { feedFixture().snapshot(true, CellTx.REG_OK, 0L) }

/** The fixture pass as the helper answers at [now]: pages moved so the newest is at [pagesAt] ([flat]: all of them). */
private fun pass(now: Long, pagesAt: Long = now, flat: Boolean = false, edit: (IntArray) -> Unit = {}): CellStreamSnapshot {
    val d = pagesAt - maxOf(FIXTURE.cellPageMs.max(), FIXTURE.probePageMs.max())
    fun LongArray.moved() = LongArray(size) { if (this[it] == 0L) 0L else if (flat) pagesAt else this[it] + d }
    return FIXTURE.copy(
        nowMs = now, listeningSinceMs = EL0, lastFrameMs = pagesAt,
        cellMv = FIXTURE.cellMv.copyOf().also(edit), cellPageMs = FIXTURE.cellPageMs.moved(),
        probePageMs = FIXTURE.probePageMs.moved(),
    )
}

private class FakeSource : CellSource {
    var clock: () -> Long = { EL0 }
    val listens = mutableListOf<Boolean>()
    val snapshotsAt = mutableListOf<Long>()
    var listenReply: SourceReply<CellListenReply> = SourceReply.Ok(CellListenReply(CellTx.REG_OK, "OK"))
    var reply: (Long) -> SourceReply<CellStreamSnapshot> = { SourceReply.Ok(pass(it)) }
    var throwNext = false
    val calls: Int get() = listens.size + snapshotsAt.size

    /** Like the daemon: «off» answers REG_OFF when the helper is there. */
    override suspend fun listen(on: Boolean): SourceReply<CellListenReply> {
        listens += on
        val r = listenReply
        return if (!on && r is SourceReply.Ok) SourceReply.Ok(CellListenReply(CellTx.REG_OFF, "")) else r
    }

    override suspend fun snapshot(): SourceReply<CellStreamSnapshot> {
        val now = clock()
        snapshotsAt += now
        if (throwNext) {
            throwNext = false
            error("binder broke")
        }
        return reply(now)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CellCollectorTest {
    private lateinit var db: CellsDatabase
    private lateinit var repo: CellRepository
    private lateinit var settings: CellsSettings
    private val live = MutableStateFlow<DiParsData?>(null)
    private val source = FakeSource()
    private val base: DiParsData = DemoCar.snapshot().copy(speed = 0, gear = 1, powerState = 1, chargingStatus = 0)
    private var flags: BmsFlags? = BmsFlags(0, 1, 1)
    private var flagsFail = false
    private var seq = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Room runs inline, so the test scheduler alone drives every step.
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, CellsDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(direct).setTransactionExecutor(direct).build()
        repo = CellRepository(db.dao()) { File("none.db") }
        val prefs = ctx.getSharedPreferences("cells-collector-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = CellsSettings(prefs) { WALL0 }
    }

    @After fun tearDown() = db.close()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) { body() }

    private fun TestScope.collector(): CellCollector {
        source.clock = { EL0 + testScheduler.currentTime }
        val c = CellCollector(
            settings, source, repo, live,
            flags = { check(!flagsFail) { "batch read broke" }; flags },
            scope = backgroundScope,
            wall = { WALL0 + testScheduler.currentTime },
            elapsed = { EL0 + testScheduler.currentTime },
        )
        c.start()
        c.start()
        runCurrent()
        return c
    }

    private fun el(sec: Long) = EL0 + sec * 1000

    /** One live sample at [sec] seconds of test time (every sample differs, so the state flow passes it on). */
    private fun TestScope.sample(sec: Long, current: Double? = 0.0, soc: Int? = 56, power: Int = 1) {
        val d = sec * 1000 - testScheduler.currentTime
        if (d > 0) advanceTimeBy(d)
        runCurrent()
        live.value = base.copy(soc = soc, hvCurrent = current, hvVoltage = 601, powerState = power, mileage = (seq++).toDouble())
        runCurrent()
    }

    private fun on() {
        settings.setProfile(CarProfile.LEOPARD5_318)
        settings.setCollect(true)
    }

    private suspend fun rows(): List<CellSnapshotEntity> = db.dao().page(0, 1_000)

    private fun row(agoMs: Long) = CellSnapshotEntity(
        ts = WALL0 - agoMs, elapsedMs = EL0 - agoMs, profile = "leopard5_318", measure = null, restPeriod = null,
        relax = "NOT_NEEDED", relaxDropMv = null, soc = 56, currentA = 0.0, packV = 600, charging = false,
        restMin = null, daysSinceFull = null, vminMv = 3270, vmaxMv = 3278, spreadMv = 8, probeMinC = 19, probeMaxC = 20,
        flagConsistency = null, flagTempDiff = null, flagUnbalanced = null,
        cells = CellBlob.encodeCells(IntArray(184) { 3275 }), probes = CellBlob.encodeProbes(IntArray(24) { 20 }),
    )

    @Test fun `nothing happens without a car profile`() = test {
        settings.setCollect(true)
        val c = collector()
        for (t in 0L..9L) sample(t)
        c.setWindowOpen(true)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(ManualResult.Failed(StreamState.IDLE), c.takeManual())
        assertEquals(0, source.calls)
        assertEquals(StreamState.IDLE, c.stream.value.state)
        assertTrue(rows().isEmpty())
    }

    @Test fun `nothing happens with collection off`() = test {
        settings.setProfile(CarProfile.LEOPARD5_318)
        collector()
        for (t in 0L..9L) sample(t)
        assertEquals(0, source.calls)
        assertTrue(rows().isEmpty())
    }

    @Test fun `nothing happens with the car off`() = test {
        on()
        collector()
        for (t in 0L..9L) sample(t, power = 0)
        assertEquals(0, source.calls)
    }

    @Test fun `wake stores an after-parking measurement with rest from the last snapshot`() = test {
        val priorId = repo.insert(row(9 * HOUR))!!
        on()
        collector()
        sample(0)
        val rs = rows()
        assertEquals(2, rs.size)
        val r = rs.last()
        assertEquals("AFTER_PARKING", r.measure)
        assertEquals(540, r.restMin)
        assertEquals(repo.entity(priorId)!!.ts, r.restPeriod)
        assertEquals("NOT_NEEDED", r.relax)
        assertEquals(listOf(true), source.listens)
    }

    @Test fun `scheduled snapshot carries the passport`() = test {
        on()
        settings.lastFull100WallMs = WALL0 - 2 * DAY - HOUR
        val c = collector()
        sample(0, current = 3.1)
        sample(30, current = 3.1)
        val rs = rows()
        assertEquals(listOf("AFTER_PARKING", null), rs.map { it.measure })
        val r = rs.last()
        assertEquals(56, r.soc)
        assertEquals(3.1, r.currentA!!, 1e-9)
        assertEquals(601, r.packV)
        assertFalse(r.charging)
        assertEquals(19, r.probeMinC)
        assertEquals(20, r.probeMaxC)
        assertEquals(listOf(0, 1, 1), listOf(r.flagConsistency, r.flagTempDiff, r.flagUnbalanced))
        assertEquals(2, r.daysSinceFull)
        assertEquals(listOf(3271, 3278, 7), listOf(r.vminMv, r.vmaxMv, r.spreadMv))
        assertEquals(FIXTURE.cellMv.copyOf(184).toList(), CellBlob.decodeCells(r.cells).toList())
        assertEquals(FIXTURE.probeC.copyOf(24).toList(), CellBlob.decodeProbes(r.probes).toList())
        assertEquals(listOf(WALL0 + 30_000, el(30)), listOf(r.ts, r.elapsedMs))
        assertEquals("leopard5_318", r.profile)
        assertEquals(StreamState.OK, c.stream.value.state)
    }

    @Test fun `mismatch stores nothing and reports MISMATCH`() = test {
        on()
        source.reply = { SourceReply.Ok(pass(it).copy(declaredCells = 120, declaredProbes = 16)) }
        val c = collector()
        sample(0)
        sample(5)
        assertEquals(StreamStatus(StreamState.MISMATCH, 120, 16), c.stream.value)
        assertTrue(rows().isEmpty())
    }

    @Test fun `no stream stores nothing and asks once a minute`() = test {
        on()
        source.reply = { now ->
            SourceReply.Ok(
                pass(now).copy(
                    lastFrameMs = 0L, listeningSinceMs = el(0),
                    cellPageMs = LongArray(MAX_CELL_PAGES), probePageMs = LongArray(MAX_PROBE_PAGES),
                ),
            )
        }
        val c = collector()
        for (t in 0L..70L step 5) sample(t)
        assertEquals(StreamState.NO_STREAM, c.stream.value.state)
        for (t in 75L..250L step 5) sample(t)
        val slow = source.snapshotsAt.filter { it >= el(60) }
        assertTrue(slow.size >= 3)
        assertTrue(slow.zipWithNext().all { (a, b) -> b - a >= MIN })
        assertTrue(rows().isEmpty())
        // Never a frame this session: the window says the car does not send the data.
        assertEquals(0L, c.lastFrameMs.value)
    }

    @Test fun `load measurement uses only pages inside the window`() = test {
        on()
        collector()
        sample(0)
        for (t in 1L..7L) sample(t, current = 60.0)
        // The episode began at 1 s and has held 7 s at 8 s; this pass is fresh but older than the episode.
        source.reply = { now -> SourceReply.Ok(pass(now, pagesAt = el(1) - 500, flat = true)) }
        val before = source.snapshotsAt.size
        sample(8, current = 60.0)
        assertEquals(before + 1, source.snapshotsAt.size)
        assertEquals(listOf("AFTER_PARKING"), rows().map { it.measure })

        source.reply = { SourceReply.Ok(pass(it)) }
        sample(9, current = 60.0)
        val load = rows().last()
        assertEquals("LOAD", load.measure)
        assertEquals(60.0, load.currentA!!, 1e-9)
        assertNull(load.restPeriod)
        sample(10, current = 60.0)
        assertEquals(1, rows().count { it.measure == "LOAD" })
    }

    @Test fun `regeneration is no load`() = test {
        on()
        collector()
        sample(0)
        for (t in 1L..20L) sample(t, current = -60.0)
        assertEquals(0, rows().count { it.measure == "LOAD" })
    }

    @Test fun `relax outcome is written to the after-parking row`() = test {
        repo.insert(row(2 * HOUR))
        on()
        source.reply = { now -> SourceReply.Ok(pass(now) { it[0] = if (now >= el(300)) 3415 else 3420 }) }
        collector()
        for (t in 0L..300L step 10) sample(t)
        val wake = rows()[1]
        // Vmax above 3.40 V at wake: the head unit slept through the charge.
        assertEquals("AFTER_CHARGE", wake.measure)
        assertEquals(3420, wake.vmaxMv)
        assertEquals("RELAXING", wake.relax)
        assertEquals(5, wake.relaxDropMv)
        assertEquals(3415, rows().last().vmaxMv)
    }

    @Test fun `a forgotten listener is re-sent`() = test {
        on()
        source.reply = { SourceReply.Ok(pass(it).copy(listening = false, registration = CellTx.REG_OFF)) }
        collector()
        sample(0)
        assertEquals(listOf(true, true), source.listens)
    }

    @Test fun `listens while the window is open even with collection off, stops when closed`() = test {
        settings.setProfile(CarProfile.LEOPARD5_318)
        val c = collector()
        sample(0)
        assertEquals(0, source.calls)
        c.setWindowOpen(true)
        runCurrent()
        assertEquals(listOf(true), source.listens)
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(listOf(true, true), source.listens)
        // The window sees the stream state without collecting.
        assertEquals(StreamState.OK, c.stream.value.state)
        c.setWindowOpen(false)
        runCurrent()
        assertEquals(listOf(true, true, false), source.listens)
        val calls = source.calls
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(calls, source.calls)
        assertTrue(rows().isEmpty())
    }

    @Test fun `manual snapshot reports bad conditions honestly`() = test {
        repo.insert(row(5 * MIN))
        on()
        val c = collector()
        sample(0)
        sample(1, current = 40.0)
        val r = c.takeManual() as ManualResult.Stored
        assertFalse(r.conditionsOk)
        assertEquals(listOf(ManualProblem.CURRENT, ManualProblem.SHORT_REST), r.problems)
        val e = repo.entity(r.id)!!
        assertEquals("MANUAL", e.measure)
        assertNull(e.restPeriod)
        assertEquals(40.0, e.currentA!!, 1e-9)
    }

    @Test fun `manual snapshot at rest is a rest measurement`() = test {
        val priorId = repo.insert(row(2 * HOUR))!!
        on()
        val c = collector()
        sample(0)
        sample(5)
        assertEquals(ManualResult.Stored(3, true, emptyList()), c.takeManual())
        val e = repo.entity(3)!!
        assertEquals("MANUAL", e.measure)
        assertEquals(repo.entity(priorId)!!.ts, e.restPeriod)
        assertEquals(120, e.restMin)
    }

    @Test fun `manual snapshot with collection off has unknown rest and lets the listener go`() = test {
        settings.setProfile(CarProfile.LEOPARD5_318)
        val c = collector()
        sample(0)
        val r = c.takeManual() as ManualResult.Stored
        assertEquals(listOf(ManualProblem.UNKNOWN_REST), r.problems)
        assertNull(repo.entity(r.id)!!.restPeriod)
        assertEquals(listOf(true, false), source.listens)
    }

    @Test fun `manual snapshot without the helper fails at once`() = test {
        settings.setProfile(CarProfile.LEOPARD5_318)
        source.listenReply = SourceReply.HelperMissing
        source.reply = { SourceReply.HelperMissing }
        val c = collector()
        val start = testScheduler.currentTime
        assertEquals(ManualResult.Failed(StreamState.HELPER_MISSING), c.takeManual())
        assertTrue(testScheduler.currentTime - start < 2_000)
        assertTrue(rows().isEmpty())
    }

    @Test fun `manual snapshot gives up after 12 s`() = test {
        settings.setProfile(CarProfile.LEOPARD5_318)
        source.reply = { now -> SourceReply.Ok(pass(now, pagesAt = now - 20_000)) }
        val c = collector()
        assertEquals(ManualResult.Failed(StreamState.CONNECTING), c.takeManual())
        assertEquals(12, source.snapshotsAt.size)
        assertEquals(listOf(true, false), source.listens)
        assertTrue(rows().isEmpty())
    }

    @Test fun `days since full is null when the clock went back`() = test {
        on()
        settings.lastFull100WallMs = WALL0 + 3 * DAY
        collector()
        sample(0)
        assertNull(rows().single().daysSinceFull)
    }

    @Test fun `SOC 100 updates the last full charge`() = test {
        on()
        collector()
        sample(0, soc = 99)
        assertEquals(0L, settings.lastFull100WallMs)
        sample(5, soc = 100)
        assertEquals(WALL0 + 5_000, settings.lastFull100WallMs)
    }

    @Test fun `a throwing source never escapes`() = test {
        on()
        source.throwNext = true
        flagsFail = true
        collector()
        sample(0)
        assertTrue(rows().isEmpty())
        sample(1)
        val r = rows().single()
        assertEquals("AFTER_PARKING", r.measure)
        // A failed flag read only leaves the flags empty.
        assertEquals(listOf(null, null, null), listOf(r.flagConsistency, r.flagTempDiff, r.flagUnbalanced))
    }

    @Test fun `a cell or probe outside the sane range is not stored`() = test {
        on()
        source.reply = { SourceReply.Ok(pass(it) { mv -> mv[5] = 4_100 }) }
        collector()
        sample(0)
        source.reply = { SourceReply.Ok(pass(it).let { s -> s.copy(probeC = s.probeC.copyOf().also { p -> p[3] = -41 }) }) }
        sample(1)
        assertTrue(rows().isEmpty())
        source.reply = { SourceReply.Ok(pass(it)) }
        sample(2)
        assertEquals(1, rows().size)
    }

    @Test fun `frames seen this session are told from frames never seen`() = test {
        on()
        val c = collector()
        sample(0)
        assertEquals(el(0), c.lastFrameMs.value)
        // The stream stops at 0 s.
        source.reply = { now -> SourceReply.Ok(pass(el(0)).copy(nowMs = now)) }
        for (t in 30L..90L step 30) sample(t)
        assertEquals(StreamState.NO_STREAM, c.stream.value.state)
        assertEquals(el(0), c.lastFrameMs.value)

        // Switching collection off ends the session and lets the listener go.
        settings.setCollect(false)
        runCurrent()
        assertEquals(false, source.listens.last())
        assertEquals(StreamState.IDLE, c.stream.value.state)
        assertEquals(0L, c.lastFrameMs.value)
    }
}
