package com.bydmate.app.fork.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LedgerDaoTest {

    private lateinit var db: LedgerDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(LedgerDatabase.seedCallback())
            .build()
    }

    @After fun tearDown() = db.close()

    private fun refuel(
        date: Long = 1000L,
        importKey: String? = null,
        status: String = "confirmed",
        cost: Double? = 10.0,
    ) = RefuelEntity(
        date = date, odometerKm = null, liters = 30.0, totalCost = cost, pricePerLiter = null,
        fullTank = true, carFuelTotalL = null, tankPercentBefore = null, tankPercentAfter = null,
        lat = null, lon = null, place = null, note = null,
        source = "manual", status = status, importKey = importKey,
    )

    @Test fun freshDatabaseHasTwelveBuiltinCategories() = runBlocking {
        val keys = db.categoryDao().getAll().map { it.builtinKey }
        assertEquals(
            listOf(
                "service", "repair", "tires", "wash", "accessories", "insurance",
                "registration", "parking", "tolls", "fines", "tuning", "other",
            ),
            keys,
        )
    }

    @Test fun duplicateImportKeyIsIgnored() = runBlocking {
        val dao = db.refuelDao()
        val first = dao.insert(refuel(importKey = "fuelio:1"))
        val second = dao.insert(refuel(date = 2000L, importKey = "fuelio:1"))
        assertEquals(true, first > 0)
        assertEquals(-1L, second)
        assertEquals(1, dao.getAllAsc().size)
    }

    @Test fun nullImportKeysDoNotCollide() = runBlocking {
        val dao = db.refuelDao()
        val a = dao.insert(refuel(date = 1L))
        val b = dao.insert(refuel(date = 2L))
        assertEquals(true, a > 0 && b > 0)
        assertEquals(2, dao.getAllAsc().size)
    }

    @Test fun openDraftReturnsLatestDraft() = runBlocking {
        val dao = db.refuelDao()
        assertNull(dao.openDraft())
        dao.insert(refuel(date = 1L, status = "draft"))
        val latest = dao.insert(refuel(date = 2L, status = "draft"))
        dao.insert(refuel(date = 3L, status = "confirmed"))
        assertEquals(latest, dao.openDraft()?.id)
    }

    @Test fun sumCostCoversHalfOpenRange() = runBlocking {
        val dao = db.refuelDao()
        dao.insert(refuel(date = 99L, cost = 1.0))
        dao.insert(refuel(date = 100L, cost = 10.0))
        dao.insert(refuel(date = 199L, cost = 100.0))
        dao.insert(refuel(date = 200L, cost = 1000.0))
        assertEquals(110.0, dao.sumCost(100L, 200L), 0.0001)
    }

    @Test fun sumCostSkipsDrafts() = runBlocking {
        val dao = db.refuelDao()
        dao.insert(refuel(date = 100L, cost = 10.0))
        dao.insert(refuel(date = 150L, cost = 500.0, status = "draft"))
        assertEquals(10.0, dao.sumCost(100L, 200L), 0.0001)
    }
}
