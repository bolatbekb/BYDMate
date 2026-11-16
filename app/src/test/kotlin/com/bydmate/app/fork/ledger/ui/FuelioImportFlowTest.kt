package com.bydmate.app.fork.ledger.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.local.dao.ChargeDao
import com.bydmate.app.data.local.dao.ChargeSummary
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.local.dao.TripSummary
import com.bydmate.app.data.local.dao.CounterSnapshotDao
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.domain.LedgerPrefill
import com.bydmate.app.fork.ledger.domain.car
import com.bydmate.app.fork.ledger.fuelio.FuelioResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FuelioImportFlowTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    private val carData = MutableStateFlow<DiParsData?>(null)
    private val vms = mutableListOf<LedgerViewModel>()
    private val sample: String =
        javaClass.getResourceAsStream("/fuelio/fuelio_sample.csv")!!.bufferedReader().readText()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LedgerDatabase::class.java)
            .allowMainThreadQueries().addCallback(LedgerDatabase.seedCallback()).build()
        repo = LedgerRepository(db)
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(): LedgerViewModel {
        val charge = mockk<ChargeDao>()
        val trip = mockk<TripDao>()
        val settings = mockk<SettingsRepository>()
        coEvery { charge.getPeriodSummary(any(), any()) } returns ChargeSummary(0, 0.0, 0.0)
        coEvery { charge.getInRangeAsc(any(), any()) } returns emptyList()
        coEvery { trip.getPeriodSummary(any(), any()) } returns TripSummary(0.0, 0.0)
        coEvery { trip.getWithEnergyInRange(any(), any()) } returns emptyList()
        coEvery { settings.getCurrency() } returns SettingsRepository.Currency("BYN", "BYN")
        return LedgerViewModel(
            repo, LedgerStateBuilder(repo, charge, trip, settings), LedgerPrefill(mockk<CounterSnapshotDao>()), carData,
            ApplicationProvider.getApplicationContext(),
        ).also { vms += it }
    }

    private fun csv(text: String, name: String = "f.csv") = tmp.newFile(name).also { it.writeText(text) }

    @Test fun previewShowsCountsBeforeWriting() = runBlocking {
        val vm = vm()
        vm.fuelio.preview(csv(sample)).join()
        val st = vm.fuelio.state.value as FuelioImportState.Preview
        assertEquals(5, st.preview.newRefuels)
        assertEquals(1, st.preview.newCosts)
        assertTrue(repo.refuels.getAllAsc().isEmpty())
    }

    @Test fun applyWritesAndReportsResult() = runBlocking {
        val vm = vm()
        vm.fuelio.preview(csv(sample)).join()
        vm.fuelio.commit().join()
        assertEquals(FuelioImportState.Done(FuelioResult(5, 1)), vm.fuelio.state.value)
        assertEquals(5, repo.refuels.getAllAsc().size)
    }

    @Test fun applyRefusedWhileDriving() = runBlocking {
        val vm = vm()
        vm.fuelio.preview(csv(sample)).join()
        carData.value = car(mileage = 1000.0).copy(speed = 40, gear = 4)
        vm.fuelio.commit().join()
        assertTrue(vm.fuelio.state.value is FuelioImportState.Preview)
        assertTrue(repo.refuels.getAllAsc().isEmpty())
    }

    @Test fun brokenFileShowsError() = runBlocking {
        val vm = vm()
        vm.fuelio.preview(csv("hello, this is not fuelio\n1,2,3")).join()
        assertTrue(vm.fuelio.state.value is FuelioImportState.Error)
        assertTrue(repo.refuels.getAllAsc().isEmpty())
    }

    @Test fun fileOver5MbRejected() = runBlocking {
        val big = tmp.newFile("big.csv")
        RandomAccessFile(big, "rw").use { it.setLength(5L * 1024 * 1024 + 1) }
        val vm = vm()
        vm.fuelio.preview(big).join()
        assertTrue(vm.fuelio.state.value is FuelioImportState.Error)
    }

    @Test fun listsOnlyCsvNewestFirstFromGivenDir() {
        val a = csv("x", "a.csv").also { it.setLastModified(1_000) }
        val b = csv("x", "B.CSV").also { it.setLastModified(2_000) }
        csv("x", "c.zip")
        assertEquals(listOf(b, a), vm().fuelio.listCsvFiles(tmp.root))
        assertTrue(vm().fuelio.listCsvFiles(File(tmp.root, "missing")).isEmpty())
    }

    @Test fun secondTapOnImportIsIgnored() = runBlocking {
        val text = """
            "## CostCategories"
            "CostTypeID","Name"
            "10","Charging Gear"
            "## Costs"
            "CostTitle","Date","Cost","CostTypeID","isTemplate","isIncome","UniqueId"
            "a","2025-02-20","10","10","0","0","1"
            "b","2025-02-21","20","10","0","0","2"
        """.trimIndent()
        val vm = vm()
        vm.fuelio.preview(csv(text)).join()
        val first = vm.fuelio.commit()
        val second = vm.fuelio.commit()
        first.join(); second.join()
        assertEquals(FuelioImportState.Done(FuelioResult(0, 2)), vm.fuelio.state.value)
        assertEquals(1, repo.categories.getAll().count { it.name == "Charging Gear" })
    }
}
