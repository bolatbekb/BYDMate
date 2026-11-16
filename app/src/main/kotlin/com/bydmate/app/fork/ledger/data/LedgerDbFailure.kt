package com.bydmate.app.fork.ledger.data

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException

/**
 * [block], or null when a database fails under it: an SQLite error (disk full, a constraint, a
 * damaged file) or a closed database (a restore swapped the file); [onFailure] gets the error.
 * Cancellation and any other exception pass through.
 */
internal inline fun <T> unlessDbFails(onFailure: (RuntimeException) -> Unit, block: () -> T): T? =
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
    }
