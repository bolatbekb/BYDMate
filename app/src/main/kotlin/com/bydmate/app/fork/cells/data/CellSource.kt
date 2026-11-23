package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot

/** One answer of the cell source. Never an exception: every failure is one of these. */
sealed interface SourceReply<out T> {
    data class Ok<T>(val value: T) : SourceReply<T>

    /** No live helper binder, or it died during the call. */
    data object HelperMissing : SourceReply<Nothing>

    /** The helper runs but does not know the battery-check codes (a daemon from an older build). */
    data object HelperOutdated : SourceReply<Nothing>

    /** The helper answered, but with an error, nothing readable, or not in time. */
    data class Failed(val detail: String) : SourceReply<Nothing>
}

/** Where the GB cell stream comes from: the helper daemon on the car, a model in the demo. */
interface CellSource {
    /** on: ask for the stream (also the keepalive); off: let it go. */
    suspend fun listen(on: Boolean): SourceReply<CellListenReply>

    suspend fun snapshot(): SourceReply<CellStreamSnapshot>
}
