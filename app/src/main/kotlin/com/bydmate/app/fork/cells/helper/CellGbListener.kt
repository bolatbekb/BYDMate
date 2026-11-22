package com.bydmate.app.fork.cells.helper

import android.hardware.bydauto.BYDAutoEventValue
import android.hardware.bydauto.gb.AbsBYDAutoGBListener
import android.util.Log
import androidx.annotation.Keep

/**
 * The GB device's listener (dev 1039) for the battery check tap. A real subclass, because the
 * firmware rejects a dynamic Proxy over IBYDAutoListener; onDataChanged is final in the firmware,
 * so onDataEventChanged is the hook, and the stream's CAN frame rides in bufferDataValue.
 *
 * Runs on a vendor binder thread under the firmware's listener-map monitor: it only hands the fid
 * and the buffer to [sink] and returns, and nothing escapes into the vendor stack.
 *
 * @Keep: the supertype exists only on the head unit (compileOnly stubs), so R8 cannot see that
 * onDataEventChanged overrides anything, and this package is outside the author's helper keep rule.
 */
@Keep
internal class CellGbListener(private val sink: (Int, ByteArray?) -> Unit) : AbsBYDAutoGBListener() {
    override fun onDataEventChanged(eventType: Int, eventValue: BYDAutoEventValue?) {
        runCatching { sink(eventType, eventValue?.bufferDataValue) }
    }

    override fun onError(errCode: Int, errMessage: String?) {
        runCatching { Log.w(TAG, "GB listener error code=$errCode msg=${errMessage ?: "-"}") }
    }

    private companion object {
        const val TAG = "CellTap"
    }
}
