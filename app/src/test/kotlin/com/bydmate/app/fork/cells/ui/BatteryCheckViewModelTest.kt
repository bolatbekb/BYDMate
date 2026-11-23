package com.bydmate.app.fork.cells.ui

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.domain.battery.BatteryState
import com.bydmate.app.domain.battery.BatteryStateRepository
import com.bydmate.app.fork.cells.collect.CellCollector
import com.bydmate.app.fork.cells.collect.ManualProblem
import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.collect.RelaxUpdate
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellsDatabase
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.CheckView
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.ui.views.EqMode
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.fork.cells.verdict.DAY
import com.bydmate.app.fork.cells.verdict.NOON
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.cells
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BatteryCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: CellsDatabase
    private lateinit var repo: CellRepository
    private lateinit var settings: CellsSettings
    private val stream = MutableStateFlow(StreamStatus(StreamState.OK))
    private val frames = MutableStateFlow(0L)
    private val live = MutableStateFlow<DiParsData?>(null)
    private val collector = mockk<CellCollector>(relaxed = true) {
        every { stream } returns this@BatteryCheckViewModelTest.stream
        every { lastFrameMs } returns frames
    }
    private val battery = mockk<BatteryStateRepository> {
        coEvery { refresh() } returns BatteryState(56f, 96f, null, null, autoserviceAvailable = true)
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val direct = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, CellsDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor(direct).setTransactionExecutor(direct).build()
        repo = CellRepository(db.dao()) { File("none.db") }
        val prefs = ctx.getSharedPreferences("battery-check-vm-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = CellsSettings(prefs) { NOON }
        settings.setProfile(CarProfile.LEOPARD5_318)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = BatteryCheckViewModel(settings, repo, collector, CellHistoryLoader(repo), battery, live).also { it.io = dispatcher }

    private fun TestScope.watch(vm: BatteryCheckViewModel) {
        backgroundScope.launch { vm.ui.collect {} }
        backgroundScope.launch { vm.entry.collect {} }
    }

    private fun row(ts: Long, measure: String?, period: Long?, spread: Int = 6) = CellSnapshotEntity(
        ts = ts, elapsedMs = ts, profile = "leopard5_318", measure = measure, restPeriod = period, relax = "NOT_NEEDED", relaxDropMv = null,
        soc = 50, currentA = 0.5, packV = 600, charging = false, restMin = if (period != null) 70 else null, daysSinceFull = 3,
        vminMv = 3275 - spread, vmaxMv = 3275, spreadMv = spread, probeMinC = 20, probeMaxC = 21, flagConsistency = null, flagTempDiff = null,
        flagUnbalanced = null, cells = CellBlob.encodeCells(cells(spread)), probes = CellBlob.encodeProbes(IntArray(24) { 20 }),
    )

    @Test fun `opening listens and closing stops`() = runTest(dispatcher) {
        val vm = vm()
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        verify(exactly = 1) { collector.setWindowOpen(true) }
        assertTrue(vm.open.value)
        vm.setVisible(false)
        verify(exactly = 1) { collector.setWindowOpen(false) }
        vm.setVisible(true)
        verify(exactly = 2) { collector.setWindowOpen(true) }
        vm.closeWindow()
        advanceUntilIdle()
        verify(exactly = 2) { collector.setWindowOpen(false) }
        assertFalse(vm.open.value)
        // a hidden screen of a closed window does not listen
        vm.setVisible(true)
        verify(exactly = 2) { collector.setWindowOpen(true) }
    }

    @Test fun `latest rest measurement is selected by default`() = runTest(dispatcher) {
        val rest = repo.insert(row(NOON - DAY, "AFTER_PARKING", 100, spread = 6))!!
        repo.insert(row(NOON, "LOAD", null, spread = 40))
        val vm = vm()
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        val ui = vm.ui.value
        assertEquals(rest, ui.selected?.input?.id)
        assertEquals(2, ui.picker.size)
        assertEquals(40, ui.latestLoad?.spreadMv)
        assertEquals(listOf(6), ui.history.map { it.spreadMv })
        assertEquals(96f, ui.header.sohPercent)
        assertTrue(ui.picker.all { it.level != null })
        assertEquals(mapOf(rest to Level.OK), ui.levels.filterKeys { it == rest })
        assertEquals(EntryKind.RESULT, vm.entry.value.kind)
        assertEquals(6, vm.entry.value.spreadMv)
    }

    @Test fun `without a rest measurement the newest one is selected`() = runTest(dispatcher) {
        repo.insert(row(NOON - DAY, "LOAD", null, spread = 30))
        val newest = repo.insert(row(NOON, "LOAD", null, spread = 45))!!
        val vm = vm()
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        assertEquals(newest, vm.ui.value.selected?.input?.id)
    }

    @Test fun `no measurement at all leaves nothing selected`() = runTest(dispatcher) {
        val vm = vm()
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        assertEquals(null, vm.ui.value.selected)
        assertTrue(vm.ui.value.picker.isEmpty())
        assertFalse(vm.ui.value.loading)
        assertEquals(EntryKind.COLLECT_OFF, vm.entry.value.kind)
    }

    @Test fun `manual result reaches the ui`() = runTest(dispatcher) {
        val id = repo.insert(row(NOON, "MANUAL", null))!!
        val stored = ManualResult.Stored(id, false, listOf(ManualProblem.UNKNOWN_REST))
        coEvery { collector.takeManual() } coAnswers {
            delay(3_000)
            stored
        }
        val vm = vm()
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        vm.takeManual()
        runCurrent()
        assertEquals(ManualUi.Taking, vm.ui.value.manual)
        advanceUntilIdle()
        assertEquals(ManualUi.Done(stored), vm.ui.value.manual)
        assertEquals(id, vm.ui.value.selected?.input?.id)
    }

    @Test fun `a failing manual snapshot never escapes`() = runTest(dispatcher) {
        coEvery { collector.takeManual() } throws IllegalStateException("binder died")
        val vm = vm()
        watch(vm)
        vm.openWindow()
        vm.takeManual()
        advanceUntilIdle()
        assertEquals(ManualUi.Error, vm.ui.value.manual)
    }

    @Test fun `one manual snapshot at a time`() = runTest(dispatcher) {
        val stored = ManualResult.Stored(1, true, emptyList())
        coEvery { collector.takeManual() } coAnswers {
            delay(3_000)
            stored
        }
        val vm = vm()
        watch(vm)
        vm.openWindow()
        vm.takeManual()
        runCurrent()
        vm.takeManual()
        vm.takeManual()
        // closing while it runs keeps «Снимаем замер…» for the next opening
        vm.closeWindow()
        vm.openWindow()
        runCurrent()
        assertEquals(ManualUi.Taking, vm.ui.value.manual)
        vm.takeManual()
        advanceUntilIdle()
        coVerify(exactly = 1) { collector.takeManual() }
        assertEquals(ManualUi.Done(stored), vm.ui.value.manual)
        // done: the next tap takes another one
        vm.takeManual()
        advanceUntilIdle()
        coVerify(exactly = 2) { collector.takeManual() }
    }

    @Test fun `levels are cached by measurement and reopening evaluates only the shown ones`() = runTest(dispatcher) {
        val ids = (1..4).map { repo.insert(row(NOON - (5 - it) * DAY, "AFTER_PARKING", it.toLong(), spread = 5 + it))!! }
        val load = repo.insert(row(NOON, "LOAD", null, spread = 40))!!
        val loader = spyk(CellHistoryLoader(repo))
        val vm = BatteryCheckViewModel(settings, repo, collector, loader, battery, live).also { it.io = dispatcher }
        backgroundScope.launch { vm.ui.collect {} }
        vm.openWindow()
        advanceUntilIdle()
        // selected + latest load, then the three other rest measurements once
        coVerify(exactly = 5) { loader.evaluate(any(), any()) }
        assertEquals((ids + load).toSet(), vm.ui.value.levels.keys)
        assertTrue(vm.ui.value.picker.all { it.level != null })
        vm.closeWindow()
        vm.openWindow()
        advanceUntilIdle()
        // only the selected one and the latest load again: the picker levels come from the cache
        coVerify(exactly = 7) { loader.evaluate(any(), any()) }
        assertTrue(vm.ui.value.picker.all { it.level != null })
        // a relax update makes that measurement new to the cache
        repo.updateRelax(RelaxUpdate(ids[0], Relax.RELAXING, 6))
        vm.closeWindow()
        vm.openWindow()
        advanceUntilIdle()
        coVerify(exactly = 10) { loader.evaluate(any(), any()) }
    }

    @Test fun `a failing database gives an empty window and a valid row`() = runTest(dispatcher) {
        val boom = SQLiteException("disk I/O error")
        val broken = mockk<CellRepository> {
            every { measurements(any()) } returns flow { throw boom }
            every { latestRestKey() } returns flow { throw boom }
            coEvery { measurementsBetweenLimit(any(), any(), any(), any()) } throws boom
            coEvery { measurementsBetween(any(), any(), any()) } throws boom
            coEvery { latestRestId() } throws boom
            coEvery { latestLoadId() } throws boom
            coEvery { entity(any()) } throws boom
            coEvery { entities(any()) } throws boom
        }
        val vm = BatteryCheckViewModel(settings, broken, collector, CellHistoryLoader(broken), battery, live).also { it.io = dispatcher }
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        assertTrue(vm.ui.value.picker.isEmpty())
        assertEquals(null, vm.ui.value.selected)
        assertTrue(vm.ui.value.history.isEmpty())
        assertFalse(vm.ui.value.loading)
        assertEquals(EntryKind.COLLECT_OFF, vm.entry.value.kind)
        settings.setCollect(true)
        advanceUntilIdle()
        assertEquals(EntryKind.NO_MEASUREMENTS, vm.entry.value.kind)
    }

    @Test fun `equalization is read only for its view and the selected measurement`() = runTest(dispatcher) {
        val ids = (1..3).map { repo.insert(row(NOON - (4 - it) * DAY, "AFTER_PARKING", it.toLong(), spread = 5 + it))!! }
        repo.insert(row(NOON - 20 * DAY, "AFTER_PARKING", 99L, spread = 30))
        val vm = vm()
        watch(vm)
        vm.openWindow()
        advanceUntilIdle()
        assertEquals(null, vm.ui.value.equalization)
        settings.setView(CheckView.EQUALIZATION)
        advanceUntilIdle()
        val d = vm.ui.value.equalization!!
        assertEquals(EqMode.PARKINGS, d.mode)
        // two weeks up to the selected measurement, oldest first
        assertEquals(ids, d.points.map { it.id })
        vm.select(ids[0])
        advanceUntilIdle()
        assertEquals(listOf(ids[0]), vm.ui.value.equalization!!.points.map { it.id })
    }

    @Test fun `enable collection switches the setting on`() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.config.value.collect)
        vm.enableCollection()
        assertTrue(settings.config.value.collect)
        assertTrue(vm.config.value.collect)
    }

    @Test fun `details and help open and close`() = runTest(dispatcher) {
        val vm = vm()
        watch(vm)
        vm.openWindow()
        vm.showDetails(DetailsTab.PROBES)
        vm.showHelp(true)
        advanceUntilIdle()
        assertEquals(DetailsTab.PROBES, vm.ui.value.details)
        assertTrue(vm.ui.value.helpOpen)
        vm.closeWindow()
        advanceUntilIdle()
        assertEquals(null, vm.ui.value.details)
        assertFalse(vm.ui.value.helpOpen)
    }
}
