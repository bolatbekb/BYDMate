package com.bydmate.app.fork.cells.collect

import com.bydmate.app.data.vehicle.BatchReadItem

/**
 * Three BMS alarm flags stored in the passport of a snapshot (ruling R3). No verdict uses them in v1:
 * their coding is unknown. null = the read failed or the car did not answer that one.
 */
data class BmsFlags(val consistency: Int?, val tempDiff: Int?, val unbalanced: Int?)

private const val TX_GET_INT = 5

/** getInt reads only (tx 5), ids from the owner's fid dump 2026-11-19; never a write. */
val BMS_FLAG_ITEMS: List<BatchReadItem> = listOf(
    BatchReadItem(TX_GET_INT, 1039, 1132462090), // GB_BATTERY_POOR_CONSISTENCY_ALARM
    BatchReadItem(TX_GET_INT, 1039, 1132462088), // GB_TEMP_DIFF_ALARM
    BatchReadItem(TX_GET_INT, 1005, 882903102), // POWER_VOLTAGE_UNBALANCED_ALARM
)

/** [reply] is HelperClient.readBatch of [BMS_FLAG_ITEMS]: status 0 → the value, else null; no answer → null. */
fun bmsFlagsOf(reply: List<Pair<Int, Int>>?): BmsFlags? {
    if (reply == null || reply.size != BMS_FLAG_ITEMS.size) return null
    fun value(i: Int): Int? = reply[i].let { (status, v) -> if (status == 0) v else null }
    return BmsFlags(value(0), value(1), value(2))
}
