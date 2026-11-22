package com.bydmate.app.fork.cells.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.domain.battery.BatteryState
import com.bydmate.app.domain.battery.BatteryStateRepository
import com.bydmate.app.fork.cells.collect.CellCollector
import com.bydmate.app.fork.cells.collect.ManualProblem
import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellsDatabase
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.fork.cells.verdict.DAY
import com.bydmate.app.fork.cells.verdict.NOON
import com.bydmate.app.fork.cells.verdict.cells
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
        assertEquals(ManualUi.Done(ManualResult.Failed(StreamState.CONNECTING)), vm.ui.value.manual)
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
