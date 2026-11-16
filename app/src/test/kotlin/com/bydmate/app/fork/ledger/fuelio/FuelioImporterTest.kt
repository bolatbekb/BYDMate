package com.bydmate.app.fork.ledger.fuelio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.fork.ledger.data.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FuelioImporterTest {

    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var importer: FuelioImporter
    private val file: FuelioFile by lazy {
        FuelioCsv.parse(javaClass.getResourceAsStream("/fuelio/fuelio_sample.csv")!!.bufferedReader().readText())
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, LedgerDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(LedgerDatabase.seedCallback())
            .build()
        repo = LedgerRepository(db)
        importer = FuelioImporter(repo)
    }

    @After fun tearDown() = db.close()

    @Test fun importsRefuelsAsConfirmedFuelio() = runBlocking {
        val res = importer.apply(file)
        assertEquals(FuelioResult(5, 1), res)
        val all = repo.refuels.getAllAsc()
        assertEquals(5, all.size)
        assertEquals(true, all.all { it.source == "fuelio" && it.status == "confirmed" })
        assertNull(all[0].carFuelTotalL)
        assertEquals("Sampletown", all.first { it.missedBefore }.place)
    }

    @Test fun reimportAddsNothing() = runBlocking {
        importer.apply(file)
        assertEquals(FuelioResult(0, 0), importer.apply(file))
        assertEquals(FuelioPreview(0, 5, 0, 1, emptyList()), importer.preview(file))
    }

    @Test fun previewBeforeImport() = runBlocking {
        assertEquals(FuelioPreview(5, 0, 1, 0, emptyList()), importer.preview(file))
    }

    @Test fun mapsServiceToBuiltinCategory() = runBlocking {
        importer.apply(file)
        val service = repo.categories.getByBuiltinKey("service")!!
        val e = repo.expenses.observeAll().first().single()
        assertEquals(service.id, e.categoryId)
        assertEquals("Oil change", e.title)
        assertEquals("fuelio", e.source)
    }

    @Test fun unknownCategoryIsCreatedOnce() = runBlocking {
        val cost = { id: String ->
            FuelioCost("t", LocalDate.of(2025, 6, 1), null, 10, null, 5.0, id)
        }
        val f = FuelioFile(null, emptyList(), mapOf(10 to "Charging Gear﻿"), listOf(cost("a"), cost("b")))
        assertEquals(listOf("Charging Gear"), importer.preview(f).newCategories)
        importer.apply(f)
        importer.apply(f.copy(costs = listOf(cost("c"))))
        val created = repo.categories.getAll().filter { it.name == "Charging Gear" }
        assertEquals(1, created.size)
        assertNull(created.single().builtinKey)
        assertEquals(3, repo.expenses.observeAll().first().count { it.categoryId == created.single().id })
    }

    @Test fun datesAreLocalNoon() = runBlocking {
        importer.apply(file)
        val first = repo.refuels.getAllAsc().first()
        val local = Instant.ofEpochMilli(first.date).atZone(ZoneId.systemDefault())
        assertEquals(LocalDate.of(2025, 1, 10), local.toLocalDate())
        assertEquals(LocalTime.NOON, local.toLocalTime())
    }

    @Test fun identicalRowsInOneFileAreCountedAndWrittenOnce() = runBlocking {
        val r = file.refuels[0].copy(uniqueId = null)
        val c = FuelioCost("t", LocalDate.of(2025, 6, 1), null, 1, null, 5.0, null)
        val f = FuelioFile(null, listOf(r, r), mapOf(1 to "Service"), listOf(c, c))
        assertEquals(FuelioPreview(1, 1, 1, 1, emptyList()), importer.preview(f))
        assertEquals(FuelioResult(1, 1), importer.apply(f))
        assertEquals(1, repo.refuels.getAllAsc().size)
        assertEquals(1, repo.expenses.observeAll().first().size)
    }
}
