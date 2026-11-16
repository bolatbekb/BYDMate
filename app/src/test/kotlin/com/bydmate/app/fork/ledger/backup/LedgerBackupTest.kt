package com.bydmate.app.fork.ledger.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.data.backup.BackupFixtures
import com.bydmate.app.data.backup.BackupManager
import com.bydmate.app.data.backup.BackupPart
import com.bydmate.app.data.backup.PostRestoreCheck
import com.bydmate.app.data.local.database.AppDatabase
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.util.AppStrings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import android.os.Environment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LedgerBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appDatabase = mockk<AppDatabase>(relaxed = true)
    private lateinit var liveDb: File
    private lateinit var ledger: LedgerDatabase

    private fun manager() = BackupManager(context, appDatabase, AppStrings(context), listOf("automation"))

    private fun openLedger(): LedgerDatabase =
        Room.databaseBuilder(context, LedgerDatabase::class.java, LedgerDatabase.FILE_NAME)
            .allowMainThreadQueries()
            .addCallback(LedgerDatabase.seedCallback())
            .build()

    @Before
    fun setUp() {
        every { appDatabase.openHelper.writableDatabase } returns mockk<SupportSQLiteDatabase>(relaxed = true)
        every { appDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", null) } answers { BackupFixtures.checkpointCursor() }
        liveDb = BackupFixtures.createCurrentDb(context, "bydmate.db")
        BackupFixtures.withDb(liveDb) { BackupFixtures.seedCurrent(it, "live", 1L) }
        ledger = openLedger()
        LedgerBackup.provider = { ledger }
    }

    @After
    fun tearDown() {
        LedgerBackup.provider = null
        ledger.close()
    }

    private fun refuel(liters: Double = 30.0) = RefuelEntity(
        date = 1000L, odometerKm = 12000.0, liters = liters, totalCost = 60.0, pricePerLiter = 2.0,
        fullTank = true, carFuelTotalL = null, tankPercentBefore = null, tankPercentAfter = null,
        lat = null, lon = null, place = null, note = null,
        source = "manual", status = "confirmed", importKey = null,
    )

    private fun refuels(): List<RefuelEntity> = runBlocking { ledger.refuelDao().getAllAsc() }

    /** A copy of [zip] where `fork_ledger.db` holds [ledgerBytes]. */
    private fun withLedgerEntry(zip: File, ledgerBytes: ByteArray): File {
        val out = File(tmp.root, "patched-${System.nanoTime()}.zip")
        ZipFile(zip).use { src ->
            ZipOutputStream(FileOutputStream(out)).use { z ->
                for (entry in src.entries()) {
                    if (entry.name == LedgerBackup.ENTRY_DB) continue
                    z.putNextEntry(ZipEntry(entry.name))
                    src.getInputStream(entry).use { it.copyTo(z) }
                    z.closeEntry()
                }
                z.putNextEntry(ZipEntry(LedgerBackup.ENTRY_DB)); z.write(ledgerBytes); z.closeEntry()
            }
        }
        return out
    }

    /** Refusal leaves bydmate.db, the ledger, the restore marker and the temp files as they were. */
    private fun assertRefusedUntouched(zip: File, expectedMessage: String) {
        val liveBefore = liveDb.readBytes()
        val refuelsBefore = refuels()
        val error = assertThrows(IllegalStateException::class.java) { manager().restore(zip) }
        assertEquals(expectedMessage, error.message)
        assertArrayEquals(liveBefore, liveDb.readBytes())
        assertEquals(refuelsBefore, refuels())
        val state = context.getSharedPreferences(PostRestoreCheck.PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse(state.getBoolean(PostRestoreCheck.KEY_PENDING, false))
        assertTrue(liveDb.parentFile!!.list()!!.none { it.endsWith(".tmp") })
    }

    @Test
    fun exportContainsLedgerEntries() {
        runBlocking { ledger.refuelDao().insert(refuel()) }

        val zip = manager().export(BackupPart.ALL)

        assertEquals(1, JSONObject(BackupFixtures.entryBytes(zip, LedgerBackup.ENTRY_META)!!.decodeToString()).getInt("schema"))
        val entry = BackupFixtures.openEntryDb(zip, LedgerBackup.ENTRY_DB, tmp.root)
        BackupFixtures.withDb(entry) { db ->
            assertEquals(LedgerDatabase.SCHEMA_VERSION, db.version)
            assertEquals(1, BackupFixtures.count(db, "refuels"))
        }
    }

    @Test
    fun partialExportWithoutTablesHasNoLedger() {
        val zip = manager().export(setOf(BackupPart.SETTINGS))

        assertNull(BackupFixtures.entryBytes(zip, LedgerBackup.ENTRY_DB))
        assertNull(BackupFixtures.entryBytes(zip, LedgerBackup.ENTRY_META))
    }

    @Test
    fun roundTripRestoresRefuels() {
        runBlocking { ledger.refuelDao().insert(refuel(liters = 41.5)) }
        val zip = manager().export(BackupPart.ALL)
        runBlocking { ledger.refuelDao().delete(refuels().single()) }
        assertTrue(refuels().isEmpty())

        manager().restore(zip)

        ledger.close()
        ledger = openLedger()
        assertEquals(41.5, refuels().single().liters, 1e-9)
        assertTrue(liveDb.parentFile!!.list()!!.none { it.endsWith(".tmp") })
    }

    @Test
    fun restoreArchiveWithoutLedgerKeepsCurrentLedger() {
        runBlocking { ledger.refuelDao().insert(refuel()) }
        val zip = BackupFixtures.archive(liveDb.readBytes(), BackupPart.ALL)

        manager().restore(zip)

        assertEquals(1, refuels().size)
    }

    @Test
    fun restoreRejectsCorruptLedgerBeforeAnyChange() {
        runBlocking { ledger.refuelDao().insert(refuel()) }
        val zip = withLedgerEntry(manager().export(BackupPart.ALL), "not a database at all".repeat(200).toByteArray())

        assertRefusedUntouched(zip, AppStrings(context).get(com.bydmate.app.R.string.ledger_backup_error_corrupt))
    }

    @Test
    fun restoreRejectsNewerLedgerSchema() {
        runBlocking { ledger.refuelDao().insert(refuel()) }
        val zip = manager().export(BackupPart.ALL)
        val newer = BackupFixtures.openEntryDb(zip, LedgerBackup.ENTRY_DB, tmp.root)
        SQLiteDatabase.openDatabase(newer.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 2 }
        val patched = withLedgerEntry(zip, newer.readBytes())

        assertRefusedUntouched(
            patched,
            AppStrings(context).get(com.bydmate.app.R.string.ledger_backup_error_newer, 2, LedgerDatabase.SCHEMA_VERSION),
        )
    }

    @Test
    fun settingsOnlyRestoreLeavesLedger() {
        runBlocking { ledger.refuelDao().insert(refuel()) }
        val zip = manager().export(BackupPart.ALL)
        runBlocking { ledger.refuelDao().delete(refuels().single()) }

        manager().restore(zip, setOf(BackupPart.SETTINGS))

        assertTrue(refuels().isEmpty())
        assertTrue(liveDb.parentFile!!.list()!!.none { it.endsWith(".tmp") })
    }

    @Test
    fun noProviderMeansNoOp() {
        runBlocking { ledger.refuelDao().insert(refuel()) }
        val withLedger = manager().export(BackupPart.ALL)
        assertNotNull(BackupFixtures.entryBytes(withLedger, LedgerBackup.ENTRY_DB))
        runBlocking { ledger.refuelDao().delete(refuels().single()) }
        LedgerBackup.provider = null

        val zip = manager().export(BackupPart.ALL)
        assertNull(BackupFixtures.entryBytes(zip, LedgerBackup.ENTRY_DB))
        assertNull(BackupFixtures.entryBytes(zip, LedgerBackup.ENTRY_META))

        manager().restore(withLedger)
        assertTrue(refuels().isEmpty())
    }

    @Test
    fun failedAuthorSwapLeavesLedgerUncommitted() {
        runBlocking { ledger.refuelDao().insert(refuel()) }
        val zip = manager().export(BackupPart.ALL)
        runBlocking { ledger.refuelDao().delete(refuels().single()) }
        val liveBefore = liveDb.readBytes()
        // The merge snapshot of bydmate.db can never fold its WAL: mergeAndSwap throws before its swap.
        every { appDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", null) } answers { BackupFixtures.checkpointCursor(busy = 1) }

        assertThrows(IllegalStateException::class.java) { manager().restore(zip, setOf(BackupPart.TABLES)) }

        assertArrayEquals(liveBefore, liveDb.readBytes())
        assertTrue(refuels().isEmpty())
        assertEquals(LedgerDatabase.SCHEMA_VERSION, ledger.openHelper.writableDatabase.version)

        // The temp file left behind does not get in the way of the next restore.
        every { appDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)", null) } answers { BackupFixtures.checkpointCursor() }
        manager().restore(zip)
        ledger.close()
        ledger = openLedger()
        assertEquals(1, refuels().size)
        assertTrue(liveDb.parentFile!!.list()!!.none { it.endsWith(".tmp") })
    }

    @Test
    fun busyLedgerAbortsExportWithoutArchive() {
        val support = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { support.path } returns context.getDatabasePath(LedgerDatabase.FILE_NAME).path
        every { support.query("PRAGMA wal_checkpoint(TRUNCATE)") } answers { BackupFixtures.checkpointCursor(busy = 1) }
        val busy = mockk<LedgerDatabase>(relaxed = true)
        every { busy.openHelper.writableDatabase } returns support
        LedgerBackup.provider = { busy }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val before = BackupManager.listBackupFiles(downloads).map { it.name }.toSet()

        val error = assertThrows(IllegalStateException::class.java) { manager().export(BackupPart.ALL) }

        assertEquals(AppStrings(context).get(com.bydmate.app.R.string.ledger_backup_error_busy), error.message)
        assertEquals(before, BackupManager.listBackupFiles(downloads).map { it.name }.toSet())
    }
}
