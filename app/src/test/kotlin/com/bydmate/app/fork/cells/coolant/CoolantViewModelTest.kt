package com.bydmate.app.fork.cells.coolant

import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.CheckView
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoolantViewModelTest {
    private class FakeReader : CoolantReader {
        var calls = 0
        override suspend fun readC(): Int? { calls++; return 80 }
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun settings(profile: CarProfile): CellsSettings = mockk {
        every { config } returns MutableStateFlow(CellsConfig(profile, false, 30, CheckView.WHAT_TO_DO))
    }

    @Test fun `no polling without Leopard 5`() = runTest {
        val reader = FakeReader()
        val vm = CoolantViewModel(settings(CarProfile.NONE), reader)
        val job = backgroundScope.launch { vm.tempC.collect {} }
        runCurrent(); advanceTimeBy(20_000)
        assertEquals(0, reader.calls)
        job.cancel()
    }

    @Test fun `polls every 5 s while shown`() = runTest {
        val reader = FakeReader()
        val vm = CoolantViewModel(settings(CarProfile.LEOPARD5_318), reader)
        backgroundScope.launch { vm.tempC.collect {} }
        runCurrent(); advanceTimeBy(9_999)
        assertEquals(2, reader.calls)
        advanceTimeBy(2)
        assertEquals(3, reader.calls)
    }
}
