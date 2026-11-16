package com.bydmate.app.fork.ledger.domain

import org.json.JSONObject
import kotlin.math.abs

/**
 * Pre-refuel baseline. [dismissed] marks a draft the user deleted: no new draft for this baseline.
 */
data class DetectorState(
    val baselinePercent: Int,
    val baselineFuelTotal: Double?,
    val baselineOdo: Double,
    val baselineTs: Long,
    val draftId: Long?,
    val dismissed: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("baselinePercent", baselinePercent)
        if (baselineFuelTotal != null) put("baselineFuelTotal", baselineFuelTotal)
        put("baselineOdo", baselineOdo)
        put("baselineTs", baselineTs)
        if (draftId != null) put("draftId", draftId)
        put("dismissed", dismissed)
    }.toString()

    companion object {
        fun fromJson(json: String?): DetectorState? = try {
            if (json.isNullOrBlank()) null else JSONObject(json).let {
                DetectorState(
                    baselinePercent = it.getInt("baselinePercent"),
                    baselineFuelTotal = if (it.has("baselineFuelTotal")) it.getDouble("baselineFuelTotal") else null,
                    baselineOdo = it.getDouble("baselineOdo"),
                    baselineTs = it.getLong("baselineTs"),
                    draftId = if (it.has("draftId")) it.getLong("draftId") else null,
                    dismissed = it.optBoolean("dismissed", false),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

sealed interface DetectorAction {
    object None : DetectorAction
    data class CreateDraft(val before: DetectorState, val afterPercent: Int, val ts: Long) : DetectorAction
    data class UpdateDraft(val draftId: Long, val afterPercent: Int) : DetectorAction
}

/** Spots a refuel as a tank-level jump with the odometer standing still. */
object RefuelDetector {
    private const val MOVED_KM = 2.0

    fun step(
        state: DetectorState?,
        percent: Int?,
        fuelTotal: Double?,
        odo: Double?,
        ts: Long,
        thresholdPp: Int,
    ): Pair<DetectorState?, DetectorAction> {
        // 0 % is a glitch (or a dead-empty tank we cannot tell from one): never a baseline
        if (percent == null || percent !in 1..100 || odo == null) return state to DetectorAction.None
        fun fresh() = DetectorState(percent, fuelTotal, odo, ts, null)
        if (state == null) return fresh() to DetectorAction.None
        if (abs(odo - state.baselineOdo) >= MOVED_KM) return fresh() to DetectorAction.None
        if (percent < state.baselinePercent) {
            return state.copy(
                baselinePercent = percent, baselineFuelTotal = fuelTotal, baselineOdo = odo, baselineTs = ts,
            ) to DetectorAction.None
        }
        if (percent - state.baselinePercent >= thresholdPp && !state.dismissed) {
            val id = state.draftId
            return state to if (id == null) DetectorAction.CreateDraft(state, percent, ts)
            else DetectorAction.UpdateDraft(id, percent)
        }
        return state to DetectorAction.None
    }
}
