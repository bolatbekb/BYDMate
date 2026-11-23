package com.bydmate.app.fork.cells.demo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellsDatabase
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.ui.views.EqMode
import com.bydmate.app.fork.cells.ui.views.EqualizationLoader
import com.bydmate.app.fork.cells.ui.views.equalizationModel
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.fork.cells.verdict.Laggard
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.Reason
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
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DemoCellsSeederTest {
    private lateinit var db: CellsDatabase
    private lateinit var repo: CellRepository
    private lateinit var settings: CellsSettings
    private val now = 1_800_000_000_000L
    private val utc = ZoneId.of("UTC")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CellsDatabase::class.java).allowMainThreadQueries().build()
        repo = CellRepository(db.dao()) { File("none.db") }
        settings = CellsSettings(ctx.getSharedPreferences("seed_test", Context.MODE_PRIVATE))
    }

    @After fun tearDown() = db.close()

    @Test fun `seeder fills an empty database once`() = runBlocking {
        assertTrue(DemoCellsSeeder.seedIfEmpty(repo, settings, now, utc) > 600)
        assertEquals(0, DemoCellsSeeder.seedIfEmpty(repo, settings, now, utc))
        assertTrue(settings.lastFull100WallMs in (now - 4 * 86_400_000L)..now)
    }

    @Test fun `seeded history reads as watch with laggard 92 and base growth`() = runBlocking {
        DemoCellsSeeder.seedIfEmpty(repo, settings, now, utc)
        val loader = CellHistoryLoader(repo)
        val e = loader.evaluate(loader.latestRestId()!!, now)!!
        assertEquals(Level.WATCH, e.level)
        assertTrue(e.laggards.toString(), Laggard(92, 6, 6) in e.laggards)
        assertTrue(e.reasons.toString(), Reason.BASE_GROWTH in e.reasons)
        assertTrue(e.spreadMv.toString(), e.spreadMv in 40..50)
    }

    @Test fun `the latest after-charge measurement has an equalization curve with the previous charge`() = runBlocking {
        DemoCellsSeeder.seedIfEmpty(repo, settings, now, utc)
        val latest = repo.measurementsBetweenLimit(setOf(MeasureKind.AFTER_CHARGE), 0, now, 1).first()
        val window = repo.summariesBetween(latest.ts - 18 * 3_600_000L, latest.ts)
        assertTrue(window.count { it.charging } == 1)
        val e = CellHistoryLoader(repo).evaluate(latest.id, now)!!
        val q = equalizationModel(e, EqualizationLoader(repo).load(e))
        assertEquals(EqMode.AFTER_CHARGE, q.mode)
        assertFalse(q.awakeAxis)
        assertTrue("${q.points.size}", q.points.size >= 20)
        // the top cells come down after the charge end
        assertTrue("${q.points.first().mv} -> ${q.points.last().mv}", q.points.first().mv > q.points.last().mv + 50)
        assertTrue("${q.previous.size}", q.previous.size >= 20)
    }

    @Test fun `seeded history has an after-charge measurement at the top and loads`() = runBlocking {
        DemoCellsSeeder.seedIfEmpty(repo, settings, now, utc)
        val all = repo.summariesBetween(0, now)
        val charge = all.filter { it.measure == MeasureKind.AFTER_CHARGE.name }
        assertTrue(charge.isNotEmpty() && charge.all { it.soc == 100 && it.vmaxMv > 3400 && it.daysSinceFull == 0 })
        assertTrue(all.any { it.measure == MeasureKind.LOAD.name })
        assertTrue(CellHistoryLoader(repo).latestLoadId() != null)
        val wall = all.filter { it.measure == MeasureKind.AFTER_PARKING.name }.maxBy { it.ts }
        assertEquals(7 * 3_600_000L + 30 * 60_000L, wall.ts % 86_400_000L)
    }
}
