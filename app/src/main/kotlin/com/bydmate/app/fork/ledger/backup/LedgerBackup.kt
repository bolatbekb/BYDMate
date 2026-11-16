package com.bydmate.app.fork.ledger.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.bydmate.app.R
import com.bydmate.app.data.backup.BackupPart
import com.bydmate.app.fork.ledger.data.LedgerDatabase
import com.bydmate.app.util.AppStrings
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Carries the fork ledger database (`fork_ledger.db`) inside the author's backup zip, as part of
 * [BackupPart.TABLES]. The author's restore skips unknown zip entries, so archives stay readable
 * by the author's app and older fork builds.
 *
 * [provider] is set by the app only outside demo mode; while it is null every hook is a no-op.
 */
object LedgerBackup {

    private const val TAG = "LedgerBackup"

    @Volatile var provider: (() -> LedgerDatabase)? = null

    const val ENTRY_DB = "fork_ledger.db"
    const val ENTRY_META = "fork_ledger.json"

    /** Largest `fork_ledger.db` a restore reads; a real ledger is well under a megabyte. */
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    private const val RESTORE_TMP = "fork_ledger.db.restore.tmp"

    private val REQUIRED_TABLES = listOf("refuels", "expense_categories", "expenses", "ledger_settings")

    /** Self-contained bytes of the live ledger, ready to go into the zip. */
    class Snapshot internal constructor(private val bytes: ByteArray) {
        fun writeTo(zip: ZipOutputStream) {
            zip.putNextEntry(ZipEntry(ENTRY_DB))
            zip.write(bytes)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_META))
            zip.write(JSONObject().put("schema", LedgerDatabase.SCHEMA_VERSION).toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * Snapshot for an export of [parts]; null without [BackupPart.TABLES] or a provider. Taken
     * before the zip is opened, so a failure leaves no half-written archive behind.
     *
     * Same approach as the author's export: `PRAGMA wal_checkpoint(TRUNCATE)` must report busy=0,
     * then the file is read inside a write transaction (no writer can commit meanwhile) and only
     * if the WAL stayed empty. Three attempts, then [IllegalStateException]: the export fails whole.
     */
    fun snapshot(parts: Set<BackupPart>, strings: AppStrings): Snapshot? {
        if (BackupPart.TABLES !in parts) return null
        val db = provider?.invoke() ?: return null
        val support = db.openHelper.writableDatabase
        val dbFile = File(requireNotNull(support.path) { "ledger database has no file" })
        val walFile = File(dbFile.path + "-wal")
        for (attempt in 1..3) {
            val checkpointed = try {
                support.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c -> c.moveToFirst() && c.getInt(0) == 0 }
            } catch (e: Exception) {
                Log.w(TAG, "wal_checkpoint failed: ${e.message}")
                false
            }
            if (!checkpointed) continue
            support.beginTransaction()
            try {
                if (walFile.length() == 0L) return Snapshot(dbFile.readBytes())
            } finally {
                support.endTransaction()
            }
        }
        throw IllegalStateException(strings.get(R.string.ledger_backup_error_busy))
    }

    /**
     * Validated ledger from [archive], waiting in a temp file next to the live one.
     * Null when [selected] lacks [BackupPart.TABLES], there is no provider, or the archive has no
     * ledger (made by the author's app or before the ledger existed): the live ledger stays.
     *
     * Throws [IllegalStateException] — temp file deleted, nothing changed — when the entry is
     * oversized, duplicated, not an intact SQLite file, not a ledger, or of a newer schema.
     * Must run before the first destructive step of the restore.
     */
    fun prepareRestore(context: Context, archive: File, selected: Set<BackupPart>): PreparedLedger? {
        if (BackupPart.TABLES !in selected) return null
        val provider = provider ?: return null
        val strings = AppStrings(context)

        val target = context.getDatabasePath(LedgerDatabase.FILE_NAME)
        val dir = target.parentFile!!.apply { mkdirs() }
        val tmp = File(dir, RESTORE_TMP)
        deleteWithSideFiles(tmp)
        try {
            val found = try {
                ZipInputStream(archive.inputStream()).use { zip -> copyLedgerEntry(zip, tmp, strings) }
            } catch (e: IOException) {
                throw corrupt(strings, e)
            }
            if (!found) {
                deleteWithSideFiles(tmp)
                return null
            }
            validate(tmp, strings)
        } catch (e: Throwable) {
            deleteWithSideFiles(tmp)
            throw e
        }
        return PreparedLedger(tmp, target, provider)
    }

    /** Writes the single `fork_ledger.db` entry to [tmp]; false when the archive has none. */
    private fun copyLedgerEntry(zip: ZipInputStream, tmp: File, strings: AppStrings): Boolean {
        var found = false
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == ENTRY_DB) {
                if (found) throw corrupt(strings)
                found = true
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ENTRY_BYTES) throw corrupt(strings)
                        out.write(buf, 0, n)
                    }
                }
            }
            zip.closeEntry()
        }
        return found
    }

    private fun corrupt(strings: AppStrings, cause: Throwable? = null) =
        IllegalStateException(strings.get(R.string.ledger_backup_error_corrupt), cause)

    private fun validate(file: File, strings: AppStrings) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            throw corrupt(strings, e)
        }
        val problem: IllegalStateException? = try {
            val version = db.version
            val names = REQUIRED_TABLES.joinToString(",") { "'$it'" }
            val tables = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ($names)", null,
            ).use { it.moveToFirst(); it.getInt(0) }
            val intact = db.rawQuery("PRAGMA quick_check", null).use { c ->
                c.moveToFirst() && c.getString(0).equals("ok", ignoreCase = true)
            }
            when {
                !intact -> corrupt(strings)
                version > LedgerDatabase.SCHEMA_VERSION -> IllegalStateException(
                    strings.get(R.string.ledger_backup_error_newer, version, LedgerDatabase.SCHEMA_VERSION),
                )
                version < 1 || tables < REQUIRED_TABLES.size -> corrupt(strings)
                db.rawQuery("PRAGMA foreign_key_check", null).use { it.count } > 0 -> corrupt(strings)
                else -> null
            }
        } catch (e: SQLiteException) {
            corrupt(strings, e)
        } finally {
            db.close()
        }
        if (problem != null) throw problem
    }

    /** A validated ledger temp file; exactly one of [commit] or [discard] is called. */
    class PreparedLedger internal constructor(
        private val tmp: File,
        private val target: File,
        private val provider: () -> LedgerDatabase,
    ) {
        /**
         * Swaps the temp file in over the live ledger. Runs after the author's swap, past the
         * point of no return, so it never throws: a failure is logged and the live ledger stays.
         */
        fun commit() {
            try {
                provider().close()
            } catch (e: Throwable) {
                Log.w(TAG, "closing the ledger before restore failed", e)
            }
            try {
                if (!tmp.renameTo(target)) {
                    Log.w(TAG, "ledger swap failed: rename ${tmp.name} -> ${target.name}")
                    deleteWithSideFiles(tmp)
                    return
                }
                // The restored file is self-contained: the WAL was folded in at export time.
                File(target.path + "-wal").delete()
                File(target.path + "-shm").delete()
                File(target.path + "-journal").delete()
                deleteWithSideFiles(tmp)
            } catch (e: Throwable) {
                Log.w(TAG, "ledger swap failed", e)
            }
        }

        fun discard() {
            deleteWithSideFiles(tmp)
        }
    }

    private fun deleteWithSideFiles(file: File) {
        listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
    }
}
