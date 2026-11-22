package com.bydmate.app.fork.cells.verdict

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellsDatabase
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CellHistoryLoaderTest {
    private lateinit var db: CellsDatabase
    private lateinit var repo: CellRepository
    private lateinit var loader: CellHistoryLoader

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CellsDatabase::class.java).allowMainThreadQueries().build()
        repo = CellRepository(db.dao()) { File("none.db") }
        loader = CellHistoryLoader(repo)
    }

    @After fun tearDown() = db.close()

    private fun row(ts: Long, measure: String?, period: Long?, spread: Int = 10) = CellSnapshotEntity(
        ts = ts, elapsedMs = ts, profile = "p", measure = measure, restPeriod = period, relax = "NOT_NEEDED", relaxDropMv = null,
        soc = 50, currentA = 0.5, packV = 580, charging = false, restMin = 70, daysSinceFull = 3, vminMv = 3275 - spread,
        vmaxMv = 3275, spreadMv = spread, probeMinC = 20, probeMaxC = 22, flagConsistency = null, flagTempDiff = null,
        flagUnbalanced = null, cells = CellBlob.encodeCells(cells(spread)), probes = CellBlob.encodeProbes(IntArray(24) { 20 }),
    )

    @Test fun `history keeps the latest measurement per rest period`() = runBlocking {
        repo.insert(row(NOON - 3 * DAY, "AFTER_PARKING", 100, spread = 95))
        repo.insert(row(NOON - 3 * DAY + 3_600_000, "REST_IN_SESSION", 100, spread = 20))
        repo.insert(row(NOON - 2 * DAY, "AFTER_PARKING", 200, spread = 50))
        repo.insert(row(NOON - 1 * DAY, "LOAD", null, spread = 60))
        val cur = repo.insert(row(NOON, "AFTER_PARKING", 300, spread = 90))!!
        val e = loader.evaluate(cur, NOON)!!
        assertEquals(90, e.spreadMv)
        assertEquals(50, e.previousSpreadMv)
        // the 95 mV reading of period 100 was replaced by the later 20 mV one, so the service condition is met once only
        assertEquals(Level.WATCH, e.level)
        val load = loader.latestLoadId()!!
        assertEquals(60, loader.evaluate(load, NOON)!!.spreadMv)
    }

    @Test fun `a wall clock behind the measurement does not lose history`() = runBlocking {
        repo.insert(row(NOON - DAY, "AFTER_PARKING", 200, spread = 95))
        val cur = repo.insert(row(NOON, "AFTER_PARKING", 300, spread = 90))!!
        val e = loader.evaluate(cur, NOON - 10 * DAY)!!
        assertEquals(Level.SERVICE, e.level)
        assertTrue(Reason.SERVICE_TWICE in e.reasons)
    }

    @Test fun `latestRestId skips load and manual under current`() = runBlocking {
        val rest = repo.insert(row(1000, "AFTER_PARKING", 1))!!
        repo.insert(row(2000, "LOAD", null))
        repo.insert(row(3000, "MANUAL", null))
        assertEquals(rest, loader.latestRestId())
        val manual = repo.insert(row(4000, "MANUAL", 4))!!
        assertEquals(manual, loader.latestRestId())
        val load = repo.insert(row(5000, "LOAD", null))
        assertEquals(load, loader.latestLoadId())
    }

    @Test fun `unknown id or plain snapshot gives null`() = runBlocking {
        val plain = repo.insert(row(1000, null, null))!!
        assertNull(loader.evaluate(plain, NOON))
        assertNull(loader.evaluate(999, NOON))
        assertNull(loader.latestLoadId())
    }
}
