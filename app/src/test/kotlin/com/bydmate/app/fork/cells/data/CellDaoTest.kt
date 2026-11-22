package com.bydmate.app.fork.cells.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.collect.RelaxUpdate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class CellDaoTest {
    private lateinit var db: CellsDatabase
    private lateinit var repo: CellRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CellsDatabase::class.java).allowMainThreadQueries().build()
        repo = CellRepository(db.dao()) { File("none.db") }
    }

    @After fun tearDown() = db.close()

    @Suppress("LongParameterList") // why: test builder with defaults
    private fun row(
        ts: Long, measure: String? = null, restPeriod: Long? = null, soc: Int? = 50, currentA: Double? = 1.5,
        relax: String = "NOT_NEEDED",
    ) = CellSnapshotEntity(
        ts = ts, elapsedMs = ts * 2, profile = "leopard5_318", measure = measure, restPeriod = restPeriod,
        relax = relax, relaxDropMv = null, soc = soc, currentA = currentA, packV = 580, charging = false,
        restMin = 70, daysSinceFull = 3, vminMv = 3300, vmaxMv = 3310, spreadMv = 10, probeMinC = 20, probeMaxC = 25,
        flagConsistency = null, flagTempDiff = 0, flagUnbalanced = null,
        cells = CellBlob.encodeCells(IntArray(184) { 3300 + it % 11 }),
        probes = CellBlob.encodeProbes(IntArray(24) { 20 + it % 6 }),
    )

    @Test fun `measurements flow is limited and skips plain snapshots`() = runBlocking {
        repeat(70) { repo.insert(row(1000L + it, measure = "AFTER_PARKING")) }
        repeat(100) { repo.insert(row(5000L + it)) }
        val list = repo.measurements().first()
        assertEquals(60, list.size)
        assertTrue(list.all { it.measure != null })
        assertEquals(1069L, list.first().ts)
        assertTrue(list.zipWithNext().all { (a, b) -> a.ts > b.ts })
    }

    @Test fun `updateRelax changes only that row`() = runBlocking {
        val a = repo.insert(row(1, "AFTER_PARKING", relax = "PENDING"))!!
        val b = repo.insert(row(2, "AFTER_PARKING", relax = "PENDING"))!!
        repo.updateRelax(RelaxUpdate(a, Relax.RELAXING, 5))
        val ea = repo.entity(a)!!
        assertEquals("RELAXING", ea.relax)
        assertEquals(5, ea.relaxDropMv)
        assertEquals("PENDING", repo.entity(b)!!.relax)
        assertEquals(2, repo.entities(listOf(a, b)).size)
    }

    @Test fun `anchor is the last row unless older than the clear mark`() = runBlocking {
        assertNull(repo.anchor(0))
        repo.insert(row(100, soc = 80))
        repo.insert(row(200, soc = 90))
        val a = repo.anchor(0)!!
        assertEquals(200L, a.wallMs)
        assertEquals(400L, a.elapsedMs)
        assertEquals(90, a.soc)
        assertNotNull(repo.anchor(200))
        assertNull(repo.anchor(201))
    }

    @Test fun `stats count rest, after charge and load`() = runBlocking {
        repo.insert(row(1, "AFTER_PARKING", restPeriod = 1))
        repo.insert(row(2, "REST_IN_SESSION", restPeriod = 1))
        repo.insert(row(3, "MANUAL", restPeriod = 3))
        repo.insert(row(4, "MANUAL"))
        repo.insert(row(5, "AFTER_CHARGE"))
        repo.insert(row(6, "LOAD"))
        repo.insert(row(7, "LOAD"))
        repeat(5) { repo.insert(row(10L + it)) }
        val s = repo.stats().first()
        assertEquals(12, s.row.snapshots)
        assertEquals(1L, s.row.firstTs)
        assertEquals(3, s.row.rest)
        assertEquals(1, s.row.afterCharge)
        assertEquals(2, s.row.load)
    }

    @Test fun `stats of an empty table are zero`() = runBlocking {
        val s = repo.stats().first()
        assertEquals(0, s.row.snapshots)
        assertEquals(0, s.row.rest)
        assertNull(s.row.firstTs)
    }

    @Test fun `between queries filter by kind and time`() = runBlocking {
        repo.insert(row(10, "AFTER_PARKING"))
        repo.insert(row(20, "LOAD"))
        repo.insert(row(30))
        assertEquals(1, repo.measurementsBetween(setOf(com.bydmate.app.fork.cells.collect.MeasureKind.LOAD), 0, 100).size)
        assertEquals(3, repo.summariesBetween(0, 100).size)
        assertEquals(1, repo.summariesBetween(25, 100).size)
    }

    @Test fun `clear empties the table`() = runBlocking {
        repo.insert(row(1, "LOAD"))
        assertTrue(repo.clear())
        assertEquals(0, repo.stats().first().row.snapshots)
        assertNull(repo.anchor(0))
    }

    @Test fun `export streams 1 200 rows in pages`() = runBlocking {
        repeat(1200) { repo.insert(row(1_700_000_000_000L + it * 1000L, soc = if (it % 2 == 0) null else 50)) }
        val dir = File.createTempFile("cells", "").let { it.delete(); it.mkdirs(); it }
        val f = repo.exportCsv(dir, 1_700_000_000_000L)!!
        assertTrue(f.name.matches(Regex("bydmate-cells-\\d{8}-\\d{6}\\.csv")))
        val text = f.readText()
        assertTrue(text.startsWith("﻿time;measure;"))
        val lines = text.trimEnd('\n').split('\n')
        assertEquals(1201, lines.size)
        val head = lines[0].removePrefix("﻿").split(';')
        assertEquals(184, head.count { Regex("c\\d+").matches(it) })
        assertEquals(24, head.count { Regex("p\\d+").matches(it) })
        assertEquals(head.size, lines[1].split(';').size)
        dir.deleteRecursively(); Unit
    }
}
