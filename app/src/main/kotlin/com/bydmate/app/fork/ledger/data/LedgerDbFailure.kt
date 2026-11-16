package com.bydmate.app.fork.ledger.data

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException

/**
 * [block], or null when it fails: typically the database under it (an SQLite error such as disk
 * full, a constraint or a damaged file, or a closed database after a restore swapped the file),
 * but any other exception too; [onFailure] gets the error. Only cancellation passes through.
 */
@Suppress("TooGenericExceptionCaught") // why: the ledger must never crash the UI; unknown failures are logged like before
internal inline fun <T> unlessDbFails(onFailure: (Exception) -> Unit, block: () -> T): T? =
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
