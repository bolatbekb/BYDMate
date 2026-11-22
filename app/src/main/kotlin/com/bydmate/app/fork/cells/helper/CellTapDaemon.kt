package com.bydmate.app.fork.cells.helper

import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellStreamAssembler
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.GB_DEVICE
import com.bydmate.app.fork.cells.stream.GB_STREAM_FID
import com.bydmate.app.fork.cells.stream.writeCellSnapshot
import com.bydmate.app.fork.cells.stream.writeListenReply
import com.bydmate.app.helper.describeRootCause
import com.bydmate.app.helper.push.FID_PUSH_DEVICE_CLASSES
import com.bydmate.app.helper.push.FID_PUSH_NOT_IN_FEATURE_MAP
import com.bydmate.app.helper.push.FID_PUSH_OK
import com.bydmate.app.helper.push.confirmRegistration
import com.bydmate.app.helper.push.deviceInstance
import com.bydmate.app.helper.push.findListenerMethod
import java.lang.reflect.Method

private const val TAG = "CellTap"

/**
 * Daemon side of the battery check: ONE listener on the GB device (dev 1039) for the GB stream
 * fid only, feeding a [CellStreamAssembler] the app reads with [CellTx.SNAPSHOT]. Read-only towards
 * the car: getInstance, registerListener and unregisterListener are the only vendor calls.
 *
 * The app keeps the listener alive: every LISTEN and SNAPSHOT is a contact, and the watchdog takes
 * the listener off once no contact came for [CONTACT_TIMEOUT_MS] (app gone, collection switched off).
 *
 * Locking rule, as FidPushRegistry: [lock] guards our own fields and nothing else; no vendor call
 * runs while it is held. The firmware dispatches events under its listener-map monitor, so a vendor
 * call under [lock] would be a lock-order inversion with a frame arriving at the same moment.
 */
internal object CellTapDaemon {
    const val CONTACT_TIMEOUT_MS = 120_000L
    const val TICK_MS = 5_000L

    /** Internal rather than private so the tests can assert it is free during a vendor call. */
    internal val lock = Any()

    /** Test seams: the daemon always uses the real vendor device, the real listener and the real clock. */
    @Volatile internal var deviceFactory: (String) -> Any = ::deviceInstance
    @Volatile internal var listenerFactory: ((Int, ByteArray?) -> Unit) -> Any = ::CellGbListener
    @Volatile internal var clock: () -> Long = SystemClock::elapsedRealtime
    @Volatile internal var autoTick: Boolean = true

    private val assembler = CellStreamAssembler { clock() }

    // All guarded by [lock].
    private var tap: GbTap? = null
    private var regCode = CellTx.REG_OFF
    private var detail = ""
    private var listeningSinceMs = 0L
    private var lastContactMs = 0L
    private var registering = false

    /** Bumped by every take-down, so a registration that finishes after one is undone, not adopted. */
    private var generation = 0
    private var watchdog: Thread? = null

    /** Binder thread, from HelperDaemon's dispatch line. Answers every code of [CellTx.RANGE]; never throws. */
    fun onTransact(code: Int, data: Parcel, reply: Parcel?): Boolean {
        runCatching {
            when (code) {
                CellTx.LISTEN -> {
                    val answer = listen(data.dataAvail() >= Int.SIZE_BYTES && data.readInt() == 1)
                    reply?.writeInt(CellTx.STATUS_OK)
                    reply?.let { writeListenReply(it, answer) }
                }
                CellTx.SNAPSHOT -> {
                    val s = snapshot()
                    reply?.writeInt(CellTx.STATUS_OK)
                    reply?.let { writeCellSnapshot(it, s) }
                }
                else -> reply?.writeInt(CellTx.STATUS_UNKNOWN_CODE)
            }
        }.onFailure { t ->
            runCatching {
                Log.w(TAG, "transact $code failed: ${describeRootCause(t)}")
                reply?.setDataPosition(0)
                reply?.setDataSize(0)
                reply?.writeInt(CellTx.STATUS_FAILED)
            }
        }
        return true
    }

    /**
     * on: one listener for the stream fid, or — when one is already there — only a contact.
     * off: the listener is taken down. Never throws: any failure is [CellTx.REG_FAILED] with its root cause.
     */
    fun listen(on: Boolean): CellListenReply = runCatching {
        if (on) return@runCatching listenOn()
        synchronized(lock) {
            lastContactMs = clock()
            takeDownLocked()
        }?.close()
        CellListenReply(CellTx.REG_OFF, "")
    }.getOrElse { CellListenReply(CellTx.REG_FAILED, describeRootCause(it)) }

    /** The assembler's state with the listening flags; a contact too. */
    fun snapshot(): CellStreamSnapshot {
        val (on, code, since) = synchronized(lock) {
            lastContactMs = clock()
            Triple(tap != null, regCode, listeningSinceMs)
        }
        return assembler.snapshot(on, code, since)
    }

    /** Watchdog step: no app contact for over [CONTACT_TIMEOUT_MS] takes the listener off. Never throws. */
    internal fun tick() {
        runCatching {
            val expired = synchronized(lock) {
                if (tap == null || clock() - lastContactMs <= CONTACT_TIMEOUT_MS) return@runCatching
                takeDownLocked()
            }
            Log.i(TAG, "no app contact for ${CONTACT_TIMEOUT_MS / 1000} s -> unregister")
            expired?.close()
        }
    }

    internal fun resetForTest() {
        val taken = synchronized(lock) {
            watchdog?.interrupt()
            watchdog = null
            registering = false
            lastContactMs = 0L
            takeDownLocked()
        }
        taken?.close()
        assembler.reset()
        deviceFactory = ::deviceInstance
        listenerFactory = ::CellGbListener
        clock = SystemClock::elapsedRealtime
        autoTick = true
    }

    private fun listenOn(): CellListenReply {
        val gen = synchronized(lock) {
            lastContactMs = clock()
            // A second caller while the first one registers gets the in-flight answer, not a second listener.
            if (registering) return CellListenReply(CellTx.REG_SENT, "registering")
            if (tap != null) {
                startWatchdogLocked()
                return CellListenReply(regCode, detail)
            }
            registering = true
            // Before the listener exists, so no frame of the new registration is lost.
            assembler.reset()
            generation
        }
        val opened = GbTap.open(deviceFactory, listenerFactory) { fid, bytes -> assembler.onBuffer(fid, bytes) }
        val orphan = synchronized(lock) {
            registering = false
            if (gen != generation) return@synchronized opened.tap
            tap = opened.tap
            regCode = opened.reply.registration
            detail = opened.reply.detail
            listeningSinceMs = if (opened.tap != null) clock() else 0L
            if (opened.tap != null) startWatchdogLocked()
            null
        }
        Log.i(TAG, "listen dev=$GB_DEVICE -> ${opened.reply.registration} ${opened.reply.detail}")
        if (orphan == null) return opened.reply
        // Taken down while we were registering: undo instead of leaving a listener nobody owns.
        orphan.close()
        return CellListenReply(CellTx.REG_OFF, "")
    }

    /** Clears our state and hands back the listener to undo with [GbTap.close] once [lock] is released. */
    private fun takeDownLocked(): GbTap? {
        generation++
        val taken = tap
        tap = null
        regCode = CellTx.REG_OFF
        detail = ""
        listeningSinceMs = 0L
        return taken
    }

    /** One watchdog per live listener; the thread ends on its own once the listener is gone. */
    private fun startWatchdogLocked() {
        if (!autoTick || watchdog != null) return
        val thread = Thread({
            runCatching { watchLoop() }
            synchronized(lock) { if (watchdog === Thread.currentThread()) watchdog = null }
        }, "cells-watchdog")
        thread.isDaemon = true
        watchdog = thread
        thread.start()
    }

    private fun watchLoop() {
        while (true) {
            Thread.sleep(TICK_MS)
            tick()
            // Deciding to end and clearing the field under one lock: a listener adopted after this
            // check finds no watchdog and starts its own.
            synchronized(lock) {
                if (tap == null && !registering) {
                    watchdog = null
                    return
                }
            }
        }
    }
}

/** One live listener on the GB device. Built and undone with no lock of ours held. */
private class GbTap(private val device: Any, private val listener: Any, private val unregister: Method) {

    /** unregisterListener; a failure is logged, never thrown. */
    fun close() {
        runCatching { unregister.invoke(device, listener) }
            .onFailure { t -> runCatching { Log.w(TAG, "unregister failed: ${describeRootCause(t)}") } }
    }

    class Opened(val tap: GbTap?, val reply: CellListenReply)

    companion object {
        /**
         * getInstance, registerListener for the stream fid only, then the listener-map readback.
         * Vendor calls: never with [CellTapDaemon.lock] held. A null tap leaves nothing to undo.
         */
        fun open(
            devices: (String) -> Any,
            listeners: ((Int, ByteArray?) -> Unit) -> Any,
            sink: (Int, ByteArray?) -> Unit,
        ): Opened {
            val (tap, register) = runCatching {
                val device = devices(FID_PUSH_DEVICE_CLASSES.getValue(GB_DEVICE))
                val listener = listeners(sink)
                GbTap(device, listener, findListenerMethod(device, listener, "unregisterListener", withFeatureIds = false)) to
                    findListenerMethod(device, listener, "registerListener", withFeatureIds = true)
            }.getOrElse { return failed(it) }
            val outcome = runCatching {
                register.invoke(tap.device, tap.listener, intArrayOf(GB_STREAM_FID))
                confirmRegistration(tap.device, GB_STREAM_FID)
            }.getOrElse {
                // Whatever the firmware kept of a register call that threw goes back off.
                tap.close()
                return failed(it)
            }
            return when (outcome) {
                FID_PUSH_OK -> Opened(tap, CellListenReply(CellTx.REG_OK, outcome))
                // The framework registered nothing (checkDeviceFeatures refused the fid): nothing to undo.
                FID_PUSH_NOT_IN_FEATURE_MAP -> Opened(null, CellListenReply(CellTx.REG_NOT_IN_MAP, outcome))
                else -> Opened(tap, CellListenReply(CellTx.REG_SENT, outcome))
            }
        }

        private fun failed(t: Throwable) = Opened(null, CellListenReply(CellTx.REG_FAILED, describeRootCause(t)))
    }
}
