package com.bydmate.app.fork.cells.helper

import android.hardware.IBYDAutoListener
import android.hardware.bydauto.BYDAutoEventValue
import com.bydmate.app.fork.cells.stream.GB_STREAM_FID

/**
 * Stands in for BYDAutoGBDevice: the two listener methods the tap finds by reflection, and the
 * private listener map confirmRegistration reads back. [onVendorCall] runs at the start of each.
 */
internal class FakeGbDevice(
    private val known: Set<Int> = setOf(GB_STREAM_FID),
    private val onVendorCall: () -> Unit = {},
) {
    private val mIBYDAutoListenerMap = FakeListenerMap()
    val listeners = mutableListOf<IBYDAutoListener>()
    val registeredIds = mutableListOf<List<Int>>()
    var registerCalls = 0
    var unregisterCalls = 0

    fun registerListener(listener: IBYDAutoListener, featureIds: IntArray) {
        onVendorCall()
        registerCalls++
        registeredIds += featureIds.toList()
        listeners += listener
        featureIds.filterTo(mIBYDAutoListenerMap.ids) { it in known }
    }

    fun unregisterListener(listener: IBYDAutoListener) {
        onVendorCall()
        unregisterCalls++
        listeners -= listener
    }

    /** One vendor event with a byte buffer, as AbsBYDAutoDevice would dispatch it. */
    fun fire(fid: Int, bytes: ByteArray?) {
        listeners.toList().forEach { it.onDataEventChanged(fid, BYDAutoEventValue().apply { bufferDataValue = bytes }) }
    }
}

internal class FakeListenerMap {
    val ids = mutableSetOf<Int>()
    fun containsId(id: Int): Boolean = id in ids
}
