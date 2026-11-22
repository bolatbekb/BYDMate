package com.bydmate.app.fork.cells.data

import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import com.bydmate.app.data.vehicle.helperServiceBinder
import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.readCellSnapshot
import com.bydmate.app.fork.cells.stream.readListenReply
import com.bydmate.app.helper.HelperBinderHolder
import com.bydmate.app.helper.HelperBinderProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's end of the battery-check codes: transacts with the helper daemon itself (ruling R2 —
 * no HelperClient hook, no author mutex: these codes only read the daemon's memory). The binder is
 * resolved like HelperClientImpl.resolveBinder and kept while it lives. Never throws; only the
 * caller's own cancellation passes through.
 */
class HelperCellSource(
    private val binder: () -> IBinder? = ::defaultHelperBinder,
    private val timeoutMs: Long = 2_000L,
) : CellSource {

    @Volatile private var cached: IBinder? = null

    override suspend fun listen(on: Boolean): SourceReply<CellListenReply> =
        call(CellTx.LISTEN, { it.writeInt(if (on) 1 else 0) }, ::readListenReply)

    override suspend fun snapshot(): SourceReply<CellStreamSnapshot> =
        call(CellTx.SNAPSHOT, {}, ::readCellSnapshot)

    private suspend fun <T> call(code: Int, args: (Parcel) -> Unit, parse: (Parcel) -> T?): SourceReply<T> =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) { transact(code, args, parse) }
        } ?: SourceReply.Failed("no answer in $timeoutMs ms")

    private fun resolve(): IBinder? {
        cached?.takeIf { it.isBinderAlive }?.let { return it }
        val fresh = runCatching { binder() }.getOrNull()?.takeIf { it.isBinderAlive }
        cached = fresh
        return fresh
    }

    private fun <T> transact(code: Int, args: (Parcel) -> Unit, parse: (Parcel) -> T?): SourceReply<T> {
        val target = resolve() ?: return SourceReply.HelperMissing
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            runCatching {
                data.writeInterfaceToken(HelperBinderProtocol.DESCRIPTOR)
                args(data)
                // false: the daemon does not know the code (or the uid gate refused us).
                if (target.transact(code, data, reply, 0)) parseReply(reply, parse) else SourceReply.HelperOutdated
            }.getOrElse { t ->
                if (t is DeadObjectException) {
                    cached = null
                    SourceReply.HelperMissing
                } else {
                    SourceReply.Failed("${t.javaClass.simpleName}: ${t.message ?: "-"}")
                }
            }
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun <T> parseReply(reply: Parcel, parse: (Parcel) -> T?): SourceReply<T> {
        if (reply.dataAvail() < Int.SIZE_BYTES) return SourceReply.Failed("empty reply")
        return when (val status = reply.readInt()) {
            CellTx.STATUS_OK -> parse(reply)?.let { SourceReply.Ok(it) } ?: SourceReply.Failed("unreadable reply")
            // A daemon that knows the block but not this code is older than the app.
            CellTx.STATUS_UNKNOWN_CODE -> SourceReply.HelperOutdated
            else -> SourceReply.Failed("status $status")
        }
    }
}

/** As HelperClientImpl.resolveBinder: the ServiceManager name first, then the binder the daemon broadcast. */
internal fun defaultHelperBinder(): IBinder? =
    helperServiceBinder()?.takeIf { it.isBinderAlive }
        ?: HelperBinderHolder.binder?.takeIf { it.isBinderAlive }
