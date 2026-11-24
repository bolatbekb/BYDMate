package com.bydmate.app.fork.alerts.model

/**
 * The problem-alerts model. Stage 1: sources emit problems, the center merges and filters them, a
 * screen lists them. No persistent store yet. Everything here is fork-local and free of Android so
 * the logic is unit-testable; the UI resolves every user-visible string from resources.
 */

/** Three levels, ordered so a bigger ordinal is more important (INFO < WARNING < CRITICAL). */
enum class Severity { INFO, WARNING, CRITICAL }

/** The blocks the owner groups sources by, both in settings and (where it helps) in the list. */
enum class AlertBlock { STORAGE, BATTERY, DRIVE_COOLING, TYRES, ELECTRICAL, DIAGNOSTICS }

/** Every source has a stable id (a settings key) and belongs to one block. */
enum class AlertSourceId(val block: AlertBlock) {
    STORAGE_FREE(AlertBlock.STORAGE),
    BATTERY_TEMP(AlertBlock.BATTERY),
    CELL_IMBALANCE(AlertBlock.BATTERY),
    INSULATION(AlertBlock.BATTERY),
    COOLANT_TEMP(AlertBlock.DRIVE_COOLING),
    MOTOR_TEMP(AlertBlock.DRIVE_COOLING),
    TYRE_PRESSURE(AlertBlock.TYRES),
    TYRE_TEMP(AlertBlock.TYRES),
    VOLT_12V(AlertBlock.ELECTRICAL),
}

/** The unit a scalar value is shown in; the UI turns it into localized text. */
enum class ScalarUnit { VOLT, CELSIUS, KPA, KOHM }

/**
 * The value an alert carries, structured so the UI formats it in the active locale (no strings are
 * baked into a source). [Storage] holds the free-space numbers and the volume label; [Scalar] a
 * single reading with its unit and an optional item label (a wheel name); [Plain] a ready line used
 * only by the demo seed.
 */
sealed interface AlertValue {
    data class Storage(val label: String, val percent: Int, val freeGb: Int, val totalGb: Int) : AlertValue
    data class Scalar(val amount: Double, val unit: ScalarUnit, val label: String? = null) : AlertValue
    data class Plain(val text: String) : AlertValue
}

/**
 * One active problem. [itemKey] separates several alerts from one source (a volume id, a wheel), so
 * each keeps its own hysteresis and its own first-seen time. [firstSeenMs] is wall time.
 */
data class Alert(
    val sourceId: AlertSourceId,
    val itemKey: String,
    val severity: Severity,
    val firstSeenMs: Long,
    val value: AlertValue,
) {
    val block: AlertBlock get() = sourceId.block

    /** Identity across recomputations: same source and item is the same alert. */
    val key: String get() = "${sourceId.name}/$itemKey"
}

/** A short in-memory history entry: an alert that became active at [atMs]. */
data class AlertEvent(
    val sourceId: AlertSourceId,
    val itemKey: String,
    val severity: Severity,
    val atMs: Long,
)
