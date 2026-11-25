package com.bydmate.app.fork.cells.ui

import android.content.Context
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellStats
import com.bydmate.app.fork.cells.data.CellStatsRow
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.CheckView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CellsSettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val stats = CellStats(CellStatsRow(3, null, 0, 0, 0), 10L)

    @Test fun `stats follow the collection toggle, not the car model`() = runTest {
        // Decoupled from the model: with no database file, stats collect while the toggle is on,
        // regardless of the chosen profile (NONE here).
        val config = MutableStateFlow(CellsConfig(CarProfile.NONE, false, 30, CheckView.WHAT_TO_DO))
        val settings = mockk<CellsSettings> { every { this@mockk.config } returns config }
        val repo = mockk<CellRepository> { every { stats() } returns flowOf(stats) }
        val vm = CellsSettingsViewModel(settings, repo, mockk<Context>(relaxed = true))
        val job = backgroundScope.launch { vm.stats.collect {} }
        runCurrent()
        verify(exactly = 0) { repo.stats() }
        assertNull(vm.stats.value)

        config.value = CellsConfig(CarProfile.NONE, true, 30, CheckView.WHAT_TO_DO)
        runCurrent()
        verify(exactly = 1) { repo.stats() }
        assertEquals(stats, vm.stats.value)
        job.cancel()
    }
}
