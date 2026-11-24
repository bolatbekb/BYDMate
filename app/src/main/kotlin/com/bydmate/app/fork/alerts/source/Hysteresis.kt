package com.bydmate.app.fork.alerts.source

import com.bydmate.app.fork.alerts.model.Severity

/** Which way a reading is a problem: low readings ([LOW], e.g. 12 V) or high ones ([HIGH], e.g. °C). */
enum class Direction { LOW, HIGH }

/**
 * Thresholds for one scalar signal. [warn] opens a WARNING, [crit] a CRITICAL; [clearMargin] is the
 * gap the reading must come back by before the alert clears, so a value sitting on the threshold
 * does not flash on and off. For [Direction.LOW] the alert clears at `warn + clearMargin`, for
 * [Direction.HIGH] at `warn - clearMargin`.
 */
data class ThresholdSpec(
    val direction: Direction,
    val warn: Double,
    val crit: Double,
    val clearMargin: Double,
)

/**
 * Pure threshold + hysteresis. Given the spec, the current reading and whether an alert was already
 * active ([firing]), returns the severity now, or null for "no alert". Escalation is immediate both
 * ways; a full clear waits for the margin. [firing] carries only "was there an alert", because once
 * past the warn line the severity is decided by the raw value anyway.
 */
object Hysteresis {
    fun evaluate(spec: ThresholdSpec, value: Double, firing: Boolean): Severity? {
        val raw = rawSeverity(spec, value)
        if (raw != null) return raw
        if (!firing) return null
        // Firing, but the raw value is back below the warn line: hold at WARNING until it clears
        // past the margin, so the alert does not flap around the threshold.
        return if (cleared(spec, value)) null else Severity.WARNING
    }

    private fun rawSeverity(spec: ThresholdSpec, value: Double): Severity? = when (spec.direction) {
        Direction.LOW -> when {
            value <= spec.crit -> Severity.CRITICAL
            value < spec.warn -> Severity.WARNING
            else -> null
        }
        Direction.HIGH -> when {
            value >= spec.crit -> Severity.CRITICAL
            value > spec.warn -> Severity.WARNING
            else -> null
        }
    }

    private fun cleared(spec: ThresholdSpec, value: Double): Boolean = when (spec.direction) {
        Direction.LOW -> value >= spec.warn + spec.clearMargin
        Direction.HIGH -> value <= spec.warn - spec.clearMargin
    }
}
