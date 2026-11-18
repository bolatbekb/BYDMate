package com.bydmate.app.ui.home.graphite

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.local.database.AppDatabase
import com.bydmate.app.data.local.entity.TripEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TripBackfillTest {
    private val now = 1_790_000_000_000L
    private val h = 3_600_000L
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun teardown() { db.close() }

    private fun est(start: Long = now - 2 * h, end: Long = now - h, km: Double = 40.0, litres: Double? = 2.0, ice: Double? = 38.0) =
        TripEstimate(start, end, km, 5.0, litres, ice, null, ice, null)

    private fun row(id: Long = 1, start: Long = now - 2 * h, end: Long? = now - h, km: Double? = 40.0) =
        TripEntity(id = id, startTs = start, endTs = end, distanceKm = km)

    private fun TripEntity.with(fuel: Double? = null, hev: Double? = null) = copy(fuelLiters = fuel, hevKm = hev)

    @Test fun `null litres and hev km are filled`() {
        assertEquals(listOf(Fill(1, 2.0, 38.0)), TripBackfill.plan(listOf(row()), listOf(est()), now))
    }

    @Test fun `non-null fields stay untouched`() {
        assertEquals(listOf(Fill(1, null, 38.0)), TripBackfill.plan(listOf(row().with(fuel = 1.5)), listOf(est()), now))
        assertEquals(listOf(Fill(1, 2.0, null)), TripBackfill.plan(listOf(row().with(hev = 3.0)), listOf(est()), now))
        assertTrue(TripBackfill.plan(listOf(row().with(1.5, 3.0)), listOf(est()), now).isEmpty())
    }

    @Test fun `hev km and litres scale by the row's share`() {
        val f = TripBackfill.plan(listOf(row(km = 10.0)), listOf(est()), now).single()
        assertEquals(0.5, f.fuelLiters!!, 1e-9)
        assertEquals(9.5, f.hevKm!!, 1e-9)
    }

    @Test fun `share is capped at one`() {
        val f = TripBackfill.plan(listOf(row(km = 80.0)), listOf(est()), now).single()
        assertEquals(2.0, f.fuelLiters!!, 1e-9)
    }

    @Test fun `two fragments split one estimate by km`() {
        val a = row(1, start = now - 2 * h, end = now - 90 * 60_000L, km = 10.0)
        val b = row(2, start = now - 80 * 60_000L, end = now - h, km = 30.0)
        val fills = TripBackfill.plan(listOf(a, b), listOf(est()), now)
        assertEquals(0.5, fills[0].fuelLiters!!, 1e-9)
        assertEquals(1.5, fills[1].fuelLiters!!, 1e-9)
        assertEquals(2.0, fills.sumOf { it.fuelLiters!! }, 1e-9)
    }

    @Test fun `the estimate with the largest overlap wins`() {
        val big = est(now - 2 * h, now - h, litres = 3.0)
        val small = est(now - 2 * h - 10 * 60_000L, now - 2 * h + 60_000L + 40_000L, litres = 9.0)
        assertEquals(3.0, TripBackfill.plan(listOf(row()), listOf(small, big), now).single().fuelLiters!!, 1e-9)
    }

    @Test fun `no overlap gives nothing`() {
        assertTrue(TripBackfill.plan(listOf(row()), listOf(est(now - 10 * h, now - 9 * h)), now).isEmpty())
    }

    @Test fun `overlap of 30 s or less gives nothing`() {
        val e = est(now - h - 30_000L, now)
        assertTrue(TripBackfill.plan(listOf(row()), listOf(e), now).isEmpty())
    }

    @Test fun `estimate without litres fills only hev km`() {
        assertEquals(listOf(Fill(1, null, 38.0)), TripBackfill.plan(listOf(row()), listOf(est(litres = null)), now))
    }

    @Test fun `estimate without ice km fills only litres`() {
        assertEquals(listOf(Fill(1, 2.0, null)), TripBackfill.plan(listOf(row()), listOf(est(ice = null)), now))
    }

    @Test fun `zero estimate km gives nothing`() {
        assertTrue(TripBackfill.plan(listOf(row()), listOf(est(km = 0.0)), now).isEmpty())
    }

    @Test fun `row without usable km is skipped`() {
        assertTrue(TripBackfill.plan(listOf(row(km = null), row(2, km = 0.0)), listOf(est()), now).isEmpty())
    }

    @Test fun `rows older than 31 days are ignored`() {
        val old = now - 32 * 24 * h
        assertTrue(TripBackfill.plan(listOf(row(start = old, end = old + h)), listOf(est(old, old + h)), now).isEmpty())
    }

    @Test fun `open-ended row inside the estimate is filled`() {
        assertEquals(1, TripBackfill.plan(listOf(row(end = null, km = 20.0)), listOf(est()), now).size)
    }

    // ---- writer on a real in-memory Room ----

    @Test fun `writer sets the two fields and nothing else`() = runBlocking {
        val dao = db.tripDao()
        val full = TripEntity(startTs = now - 2 * h, endTs = now - h, distanceKm = 40.0, kwhConsumed = 7.0, socStart = 80,
            socEnd = 60, tempAvgC = 5.0, avgSpeedKmh = 50.0, cost = 1.1, odometerStartKm = 100.0, odometerEndKm = 140.0,
            source = "energydata", bydId = 77L, fuelCost = null)
        val id = dao.insert(full)
        val n = TripBackfill.pass(dao, listOf(dao.getById(id)!!), listOf(est()), now)
        assertEquals(1, n)
        val after = dao.getById(id)!!
        assertEquals(2.0, after.fuelLiters!!, 1e-9)
        assertEquals(38.0, after.hevKm!!, 1e-9)
        assertEquals(full.copy(id = id, fuelLiters = 2.0, hevKm = 38.0), after)
    }

    @Test fun `an author write between plan and write is kept`() = runBlocking {
        val dao = db.tripDao()
        val id = dao.insert(row(0))
        val fills = TripBackfill.plan(listOf(dao.getById(id)!!), listOf(est()), now)
        dao.update(dao.getById(id)!!.copy(fuelLiters = 1.7))
        assertEquals(1, TripBackfill.apply(dao, fills, now))
        val after = dao.getById(id)!!
        assertEquals(1.7, after.fuelLiters!!, 1e-9)
        assertEquals(38.0, after.hevKm!!, 1e-9)
    }

    @Test fun `author writing both fields means no update`() = runBlocking {
        val dao = db.tripDao()
        val id = dao.insert(row(0))
        val fills = TripBackfill.plan(listOf(dao.getById(id)!!), listOf(est()), now)
        dao.update(dao.getById(id)!!.copy(fuelLiters = 1.7, hevKm = 5.0))
        val logged = mutableListOf<String>()
        assertEquals(0, TripBackfill.apply(dao, fills, now) { _, name, _ -> logged += name })
        assertEquals(5.0, dao.getById(id)!!.hevKm!!, 1e-9)
        assertTrue(logged.isEmpty())
    }

    @Test fun `a second pass writes nothing`() = runBlocking {
        val dao = db.tripDao()
        val id = dao.insert(row(0))
        val e = listOf(est())
        assertEquals(1, TripBackfill.pass(dao, listOf(dao.getById(id)!!), e, now))
        val once = dao.getById(id)
        assertEquals(0, TripBackfill.pass(dao, listOf(dao.getById(id)!!), e, now))
        assertEquals(once, dao.getById(id))
    }

    @Test fun `a fully recorded row is unchanged`() = runBlocking {
        val dao = db.tripDao()
        val id = dao.insert(row(0).with(1.0, 2.0))
        assertEquals(0, TripBackfill.pass(dao, listOf(dao.getById(id)!!), listOf(est()), now))
        assertEquals(1.0, dao.getById(id)!!.fuelLiters!!, 1e-9)
        assertEquals(2.0, dao.getById(id)!!.hevKm!!, 1e-9)
    }

    @Test fun `a missing row is skipped and each write is logged`() = runBlocking {
        val dao = db.tripDao()
        val id = dao.insert(row(0))
        val logged = mutableListOf<List<Pair<String, Any?>>>()
        val n = TripBackfill.apply(dao, listOf(Fill(999, 1.0, 1.0), Fill(id, 2.0, 38.0)), now) { _, _, f -> logged += f }
        assertEquals(1, n)
        assertEquals(1, logged.size)
        assertEquals(id, logged[0].first { it.first == "id" }.second)
        assertNull(dao.getById(999))
    }
}
