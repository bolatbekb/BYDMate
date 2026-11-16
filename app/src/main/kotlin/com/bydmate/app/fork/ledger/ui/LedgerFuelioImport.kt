package com.bydmate.app.fork.ledger.ui

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Environment
import android.util.Log
import com.bydmate.app.R
import com.bydmate.app.fork.ledger.fuelio.FuelioCsv
import com.bydmate.app.fork.ledger.fuelio.FuelioFile
import com.bydmate.app.fork.ledger.fuelio.FuelioFormatException
import com.bydmate.app.fork.ledger.fuelio.FuelioImporter
import com.bydmate.app.fork.ledger.fuelio.FuelioPreview
import com.bydmate.app.fork.ledger.fuelio.FuelioResult
import com.bydmate.app.util.appLocalizedContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface FuelioImportState {
    data object Idle : FuelioImportState
    data class Preview(val preview: FuelioPreview) : FuelioImportState
    data class Done(val result: FuelioResult) : FuelioImportState
    data class Error(val message: String) : FuelioImportState
}

/**
 * Settings › Expenses › import from Fuelio: [preview] reads and parses a CSV, nothing written;
 * [commit] writes the previewed file through [write], the view model's write (refused while driving).
 */
internal class LedgerFuelioImport(
    private val scope: CoroutineScope,
    private val importer: FuelioImporter,
    private val appContext: Context,
    private val write: (suspend () -> Unit) -> Job,
) {
    private val _state = MutableStateFlow<FuelioImportState>(FuelioImportState.Idle)
    val state: StateFlow<FuelioImportState> = _state.asStateFlow()
    private var pending: FuelioFile? = null
    private var previewJob: Job? = null

    /** `*.csv` in [dir] (File API, SAF fails on some firmwares), newest first. */
    fun listCsvFiles(
        dir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    ): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".csv", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }

    /** Reads and parses [file] off the main thread; nothing is written until [commit]. */
    fun preview(file: File): Job {
        previewJob?.cancel()
        return scope.launch { runPreview(file) }.also { previewJob = it }
    }

    @Suppress("TooGenericExceptionCaught") // why: the ledger must never crash the UI; an unknown failure reads as a read error, as before
    private suspend fun runPreview(file: File) {
        pending = null
        try {
            val parsed = withContext(Dispatchers.IO) {
                if (file.length() > MAX_IMPORT_BYTES) throw FuelioTooBig()
                FuelioCsv.parse(file.readText())
            }
            val preview = importer.preview(parsed)
            pending = parsed
            _state.value = FuelioImportState.Preview(preview)
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: FuelioTooBig) {
            failed("fuelio preview", ex, R.string.ledger_import_err_too_big)
        } catch (ex: FuelioFormatException) {
            failed("fuelio preview", ex, R.string.ledger_import_err_format)
        } catch (ex: IOException) {
            failed("fuelio preview", ex, R.string.ledger_import_err_read)
        } catch (ex: SecurityException) {
            failed("fuelio preview", ex, R.string.ledger_import_err_read)
        } catch (ex: SQLiteException) {
            failed("fuelio preview", ex, R.string.ledger_import_err_read)
        } catch (ex: IllegalStateException) {
            failed("fuelio preview", ex, R.string.ledger_import_err_read)
        } catch (ex: Exception) {
            failed("fuelio preview", ex, R.string.ledger_import_err_read)
        }
    }

    /** Writes the previewed file; a write, so refused while driving. */
    @Suppress("TooGenericExceptionCaught") // why: the ledger must never crash the UI; an unknown failure keeps the file and reads as a read error, as before
    fun commit(): Job = write {
        // take the file first: a second tap while this commit runs finds nothing and does nothing
        val file = pending ?: return@write
        pending = null
        try {
            _state.value = FuelioImportState.Done(importer.apply(file))
        } catch (ce: CancellationException) {
            pending = file
            throw ce
        } catch (ex: SQLiteException) {
            pending = file
            failed("fuelio import", ex, R.string.ledger_import_err_read)
        } catch (ex: IllegalStateException) {
            pending = file
            failed("fuelio import", ex, R.string.ledger_import_err_read)
        } catch (ex: Exception) {
            pending = file
            failed("fuelio import", ex, R.string.ledger_import_err_read)
        }
    }

    fun reset() {
        previewJob?.cancel()
        pending = null
        _state.value = FuelioImportState.Idle
    }

    private fun failed(what: String, ex: Exception, message: Int) {
        Log.w(TAG, "$what failed: ${ex.message}", ex)
        _state.value = FuelioImportState.Error(appContext.appLocalizedContext().getString(message))
    }

    private class FuelioTooBig : Exception()

    private companion object {
        const val MAX_IMPORT_BYTES = 5L * 1024 * 1024
        const val TAG = "LedgerViewModel"
    }
}
