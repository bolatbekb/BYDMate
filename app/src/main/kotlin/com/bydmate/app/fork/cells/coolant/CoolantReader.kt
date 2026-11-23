package com.bydmate.app.fork.cells.coolant

import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.HelperClient

private const val TX_GET_INT = 5
private const val MIN_C = -39
private const val MAX_C = 150

/** Coolant °C of the Leopard 5, the fid confirmed on the car: device 1006, fid 843055176. getInt only, never a write. */
private val COOLANT_ITEM = BatchReadItem(TX_GET_INT, 1006, 843055176)

/** −39..150 is a temperature; 65535 (no sensor), −40 (sensor fault) and everything else is none. */
fun coolantOrNull(raw: Int): Int? = if (raw in MIN_C..MAX_C) raw else null

interface CoolantReader {
    /** Null = no value (read failed, status not 0, or a sentinel). */
    suspend fun readC(): Int?
}

class HelperCoolantReader(private val helper: HelperClient) : CoolantReader {
    override suspend fun readC(): Int? {
        val reply = helper.readBatch(listOf(COOLANT_ITEM)) ?: return null
        val (status, value) = reply.firstOrNull() ?: return null
        return if (status == 0) coolantOrNull(value) else null
    }
}
