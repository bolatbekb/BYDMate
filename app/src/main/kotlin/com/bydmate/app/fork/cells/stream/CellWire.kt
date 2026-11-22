package com.bydmate.app.fork.cells.stream

import android.os.Parcel

/**
 * The fork's own binder codes on the helper daemon (battery check). The author numbers his codes
 * from 1 upwards one by one, so this block far above them stays clear of every future one, and it
 * stays below IBinder.LAST_CALL_TRANSACTION. A daemon too old to know the block answers transact()
 * with false; one that knows the block but not a code answers [STATUS_UNKNOWN_CODE].
 *
 * Every request starts with writeInterfaceToken(HelperBinderProtocol.DESCRIPTOR), like the author's.
 */
object CellTx {
    /** in: int on (1/0). out: int status, int registration (REG_*), String detail. */
    const val LISTEN = 0x00F0C001

    /** in: nothing. out: int status, then the table of [writeCellSnapshot]. */
    const val SNAPSHOT = 0x00F0C002

    val RANGE: IntRange = 0x00F0C001..0x00F0C00F

    const val STATUS_OK = 0
    const val STATUS_FAILED = -1
    const val STATUS_UNKNOWN_CODE = -2

    const val REG_OFF = 0
    const val REG_OK = 1

    /** Registered, but the firmware's listener map could not be read back to confirm it. */
    const val REG_SENT = 2
    const val REG_NOT_IN_MAP = 3
    const val REG_FAILED = 4
}

/** The LISTEN answer: a REG_* code and the daemon's text for it (the root cause on REG_FAILED). */
data class CellListenReply(val registration: Int, val detail: String)

/** Wire order: scalars, the four arrays, the extremes. [readCellSnapshot] reads the same order. */
fun writeCellSnapshot(p: Parcel, s: CellStreamSnapshot) {
    p.writeLong(s.nowMs)
    p.writeInt(if (s.listening) 1 else 0)
    p.writeInt(s.registration)
    p.writeLong(s.listeningSinceMs)
    p.writeLong(s.lastFrameMs)
    p.writeLong(s.frames)
    p.writeLong(s.rejected)
    p.writeInt(s.declaredCells)
    p.writeInt(s.declaredProbes)
    p.writeIntArray(s.cellMv)
    p.writeLongArray(s.cellPageMs)
    p.writeIntArray(s.probeC)
    p.writeLongArray(s.probePageMs)
    p.writeInt(s.hottest)
    p.writeInt(s.hottestC)
    p.writeInt(s.coldest)
    p.writeInt(s.coldestC)
}

/** Null on any short read, and when an array is not exactly the assembler's size. Never throws. */
fun readCellSnapshot(p: Parcel): CellStreamSnapshot? = runCatching {
    val r = SafeReader(p)
    // Named arguments are evaluated in the order written: this is the wire order.
    val s = CellStreamSnapshot(
        nowMs = r.long(),
        listening = r.int() == 1,
        registration = r.int(),
        listeningSinceMs = r.long(),
        lastFrameMs = r.long(),
        frames = r.long(),
        rejected = r.long(),
        declaredCells = r.int(),
        declaredProbes = r.int(),
        cellMv = r.ints(MAX_CELL_PAGES * CELLS_PER_PAGE),
        cellPageMs = r.longs(MAX_CELL_PAGES),
        probeC = r.ints(MAX_PROBE_PAGES * PROBES_PER_PAGE),
        probePageMs = r.longs(MAX_PROBE_PAGES),
        hottest = r.int(),
        hottestC = r.int(),
        coldest = r.int(),
        coldestC = r.int(),
    )
    s.takeIf { r.ok }
}.getOrNull()

fun writeListenReply(p: Parcel, r: CellListenReply) {
    p.writeInt(r.registration)
    p.writeString(r.detail)
}

/** Null on a short read or a missing text. Never throws. */
fun readListenReply(p: Parcel): CellListenReply? = runCatching {
    val r = SafeReader(p)
    val reply = CellListenReply(registration = r.int(), detail = r.string())
    reply.takeIf { r.ok }
}.getOrNull()

private const val CELLS_PER_PAGE = 3
private const val PROBES_PER_PAGE = 6

/** Reads only what the parcel still holds; the first short read clears [ok] and yields zeros. */
private class SafeReader(private val p: Parcel) {
    var ok = true
        private set

    private fun has(bytes: Long): Boolean {
        if (ok && p.dataAvail() < bytes) ok = false
        return ok
    }

    fun int(): Int = if (has(Int.SIZE_BYTES.toLong())) p.readInt() else 0

    fun long(): Long = if (has(Long.SIZE_BYTES.toLong())) p.readLong() else 0L

    fun string(): String {
        val s = if (has(Int.SIZE_BYTES.toLong())) p.readString() else null
        if (s == null) ok = false
        return s.orEmpty()
    }

    fun ints(size: Int): IntArray {
        if (int() != size || !has(Int.SIZE_BYTES.toLong() * size)) ok = false
        return if (ok) IntArray(size) { p.readInt() } else IntArray(0)
    }

    fun longs(size: Int): LongArray {
        if (int() != size || !has(Long.SIZE_BYTES.toLong() * size)) ok = false
        return if (ok) LongArray(size) { p.readLong() } else LongArray(0)
    }
}
