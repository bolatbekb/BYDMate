package com.bydmate.app.fork.cells.data

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException

/** [block], or null when it fails (SQLite error, closed database, anything else); [onFailure] gets the error. Only cancellation passes. */
@Suppress("TooGenericExceptionCaught") // why: the battery check must never crash the app; failures are logged
internal inline fun <T> cellsDb(onFailure: (Exception) -> Unit, block: () -> T): T? =
    try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: SQLiteException) {
        onFailure(e)
        null
    } catch (e: IllegalStateException) {
        onFailure(e)
        null
    } catch (e: Exception) {
        onFailure(e)
        null
    }
