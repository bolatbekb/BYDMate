package com.bydmate.app.fork.cells.verdict

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellsDatabase
import com.bydmate.app.fork.cells.ui.views.Advice
import com.bydmate.app.fork.cells.ui.views.adviceOf
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The owner's Leopard 5 on 2026-11-23 (the app's own CSV export, the five measurement rows): a healthy pack, 3–4 mV at
 * rest, cell 92 lowest by ~2 mV, both BMS flags «1» in every row. The verdict must call it healthy and never look at the
 * flags. The rest periods are the collector's: the first session began 19:53:46, the parking began 20:14:43.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OwnerPackVerdictTest {
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

    private fun wall(t: String): Long = LocalDateTime.parse(t.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val periods = mapOf(
        "2026-11-23 19:54:07" to wall("2026-11-23 19:53:46"),
        "2026-11-23 20:08:10" to wall("2026-11-23 20:08:08"),
        "2026-11-23 21:15:07" to wall("2026-11-23 20:14:43"),
        "2026-11-23 21:20:13" to wall("2026-11-23 20:14:43"),
    )

    /** The rows of the fixture as the collector stored them; ids by time. */
    private fun load(): Map<String, Long> = runBlocking {
        val lines = javaClass.getResourceAsStream("/fork/cells/owner-measures-20261123.csv")!!.bufferedReader().readLines()
        val head = lines.first().split(';')
        lines.drop(1).filter { it.isNotBlank() }.associate { line ->
            val f = line.split(';')
            fun v(name: String) = f[head.indexOf(name)].ifEmpty { null }
            val cells = head.indices.filter { Regex("c\\d+").matches(head[it]) }.map { f[it].toInt() }.toIntArray()
            val probes = head.indices.filter { Regex("p\\d+").matches(head[it]) }.map { f[it].toInt() }.toIntArray()
            val time = v("time")!!
            val e = CellSnapshotEntity(
                ts = wall(time), elapsedMs = wall(time), profile = "leopard5_318", measure = v("measure"), restPeriod = periods[time],
                relax = v("relax")!!, relaxDropMv = null, soc = v("soc")?.toInt(), currentA = v("current_a")?.replace(',', '.')?.toDouble(),
                packV = v("pack_v")?.toInt(), charging = v("charging") == "1", restMin = v("rest_min")?.toInt(),
                daysSinceFull = v("days_since_full")?.toInt(), vminMv = cells.min(), vmaxMv = cells.max(), spreadMv = cells.max() - cells.min(),
                probeMinC = probes.min(), probeMaxC = probes.max(), flagConsistency = v("flag_consistency")?.toInt(),
                flagTempDiff = v("flag_temp_diff")?.toInt(), flagUnbalanced = v("flag_unbalanced")?.toInt(),
                cells = CellBlob.encodeCells(cells), probes = CellBlob.encodeProbes(probes),
            )
            time to repo.insert(e)!!
        }
    }

    @Test fun `the owner's healthy pack is never «следить» or «в сервис»`() = runBlocking {
        val ids = load()
        val all = ids.mapValues { (_, id) -> loader.evaluate(id, 0L)!! }
        assertTrue(all.values.none { it.level == Level.WATCH || it.level == Level.SERVICE })
        assertTrue(all.values.all { it.laggards.isEmpty() })
        // the BMS flags are 1 in every row and change nothing
        assertTrue(db.dao().page(0, 10).all { it.flagConsistency == 1 && it.flagTempDiff == 1 })
    }

    @Test fun `the honest rest measurements say all is well`() = runBlocking {
        val ids = load()
        val parked = loader.evaluate(ids.getValue("2026-11-23 21:15:07"), 0L)!!
        assertEquals(Level.OK, parked.level)
        assertFalse(parked.preliminary)
        assertEquals(3, parked.spreadMv)
        assertEquals(92, parked.lowestCell)
        assertEquals(Advice.NOTHING_TO_DO, adviceOf(parked))
        val headline = loader.evaluate(loader.latestRestId()!!, 0L)!!
        assertEquals(ids.getValue("2026-11-23 21:20:13"), headline.input.id)
        assertEquals(Level.OK, headline.level)
        assertFalse(headline.preliminary)
        assertEquals(4, headline.spreadMv)
    }

    @Test fun `the other measurements are preliminary or without a verdict, never worse`() = runBlocking {
        val ids = load()
        val first = loader.evaluate(ids.getValue("2026-11-23 19:54:07"), 0L)!!
        assertEquals(Level.OK, first.level)
        assertTrue(first.preliminary)
        val afterDrive = loader.evaluate(ids.getValue("2026-11-23 20:08:10"), 0L)!!
        assertEquals(Level.NONE, afterDrive.level)
        assertTrue(Reason.REST_SHORT in afterDrive.reasons)
        val load = loader.evaluate(ids.getValue("2026-11-23 19:57:33"), 0L)!!
        assertEquals(Level.OK, load.level)
        assertEquals(7, load.spreadMv)
    }
}
