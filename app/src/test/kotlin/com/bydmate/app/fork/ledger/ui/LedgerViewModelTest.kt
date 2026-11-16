package com.bydmate.app.fork.ledger.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.local.dao.ChargeDao
import com.bydmate.app.data.local.dao.ChargeSummary
import com.bydmate.app.data.local.dao.CounterSnapshotDao
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.local.dao.TripSummary
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.LedgerPrefill
import com.bydmate.app.fork.ledger.domain.car
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LedgerViewModelTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    private val chargeDao = mockk<ChargeDao>()
    private val tripDao = mockk<TripDao>()
    private val settings = mockk<SettingsRepository>()
    private val snaps = mockk<CounterSnapshotDao>()
    private val carData = MutableStateFlow<DiParsData?>(null)
    private val now = System.currentTimeMillis()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LedgerDatabase::class.java)
            .allowMainThreadQueries().addCallback(LedgerDatabase.seedCallback()).build()
        repo = LedgerRepository(db)
        coEvery { chargeDao.getPeriodSummary(any(), any()) } returns ChargeSummary(1, 50.0, 0.0)
        coEvery { chargeDao.getInRangeAsc(any(), any()) } returns emptyList()
        coEvery { tripDao.getPeriodSummary(any(), any()) } returns TripSummary(0.0, 0.0)
        coEvery { tripDao.getWithEnergyInRange(any(), any()) } returns emptyList()
        coEvery { settings.getCurrency() } returns SettingsRepository.Currency("BYN", "BYN")
        coEvery { snaps.nearest(any(), any()) } returns null
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = LedgerViewModel(repo, chargeDao, tripDao, settings, LedgerPrefill(snaps), carData)

    private fun refuel(
        date: Long, odo: Double?, liters: Double, cost: Double? = null, full: Boolean = true,
        car: Double? = null, status: String = "confirmed",
    ) = RefuelEntity(
        date = date, odometerKm = odo, liters = liters, totalCost = cost, pricePerLiter = null,
        fullTank = full, carFuelTotalL = car, tankPercentBefore = null, tankPercentAfter = null,
        lat = null, lon = null, place = null, note = null,
        source = if (status == "draft") "detected" else "manual", status = status, importKey = null,
    )

    private fun <T> until(what: String, get: () -> T, cond: (T) -> Boolean): T = runBlocking {
        repeat(100) { val v = get(); if (cond(v)) return@runBlocking v; delay(50) }
        throw AssertionError("timed out waiting for $what, last=${get()}")
    }

    @Test fun writesBlockedWhileDriving() = runBlocking {
        carData.value = car(mileage = 1000.0).copy(speed = 40, gear = 4)
        val vm = vm()
        until("driving state", { vm.state.value }) { it.loaded && !it.parked }
        vm.saveRefuel(refuel(now, 1000.0, 30.0, 60.0)).join()
        vm.saveExpense(
            ExpenseEntity(date = now, odometerKm = null, categoryId = 1, amount = 5.0, title = null, note = null, source = "manual", importKey = null)
        ).join()
        assertTrue(repo.refuels.getAllAsc().isEmpty())
        assertTrue(db.expenseDao().sumByCategory(0, Long.MAX_VALUE).isEmpty())
    }

    @Test fun confirmDraftTurnsDraftIntoConfirmed() = runBlocking {
        val id = repo.refuels.insert(refuel(now, 1000.0, 20.0, full = false, car = 55.0, status = "draft"))
        val vm = vm()
        val draft = repo.refuels.getById(id)!!
        vm.confirmDraft(draft.copy(liters = 25.0, totalCost = 62.5, pricePerLiter = 2.5)).join()
        val saved = repo.refuels.getById(id)!!
        assertEquals("confirmed", saved.status)
        assertEquals(25.0, saved.liters, 0.0)
        assertEquals(55.0, saved.carFuelTotalL!!, 0.0)
        assertEquals(1, repo.refuels.getAllAsc().size)
    }

    @Test fun monthRowsUseChargeCostsAndReceipts() = runBlocking {
        coEvery { chargeDao.getPeriodSummary(any(), any()) } returns ChargeSummary(3, 120.0, 5000.0)
        repo.refuels.insert(refuel(now, 1000.0, 5000.0, cost = 14302.05))
        val wash = repo.categories.getByBuiltinKey("wash")!!
        repo.expenses.insert(
            ExpenseEntity(date = now, odometerKm = null, categoryId = wash.id, amount = 2000.0, title = null, note = null, source = "manual", importKey = null)
        )
        val vm = vm()
        val st = until("month rows", { vm.state.value }) { it.months.isNotEmpty() }
        val row = st.months.first()
        assertEquals(YearMonth.now(), row.month)
        assertEquals(5000.0, row.electricity, 0.001)
        assertEquals(14302.05, row.fuel, 0.001)
        assertEquals(2000.0, row.byCategory[wash.id]!!, 0.001)
        assertEquals(21302.05, row.total, 0.001)
        assertEquals(21302.05, st.totals!!.total, 0.001)
        assertEquals("BYN", st.currencySymbol)
    }

    @Test fun segmentsExcludeDrafts() = runBlocking {
        val day = 86_400_000L
        repo.refuels.insert(refuel(now - 3 * day, 1000.0, 40.0, cost = 100.0, car = 100.0))
        repo.refuels.insert(refuel(now - 2 * day, 1200.0, 30.0, full = true, car = 112.0, status = "draft"))
        repo.refuels.insert(refuel(now - day, 1500.0, 35.0, cost = 90.0, car = 135.0))
        val vm = vm()
        val st = until("segments", { vm.state.value }) { it.refuels.size == 3 }
        assertEquals(1, st.segments.size)
        val seg = st.segments.single()
        assertEquals(500.0, seg.distanceKm!!, 0.0)
        assertEquals(35.0, seg.litersBought, 0.0)
    }
}
