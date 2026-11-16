package com.bydmate.app.fork.ledger.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerRepository @Inject constructor(private val db: LedgerDatabase) {

    val refuels: RefuelDao get() = db.refuelDao()
    val expenses: ExpenseDao get() = db.expenseDao()
    val categories: ExpenseCategoryDao get() = db.categoryDao()
    private val settings: LedgerSettingDao get() = db.settingDao()

    suspend fun tankCapacityL(): Double? = settings.get(KEY_TANK_CAPACITY)?.toDoubleOrNull()

    suspend fun setTankCapacityL(v: Double?) =
        settings.set(LedgerSettingEntity(KEY_TANK_CAPACITY, v?.toString()))

    suspend fun detectThresholdPp(): Int =
        settings.get(KEY_DETECT_THRESHOLD)?.toIntOrNull() ?: DEFAULT_DETECT_THRESHOLD_PP

    suspend fun mismatchThresholdPct(): Double =
        settings.get(KEY_MISMATCH_THRESHOLD)?.toDoubleOrNull() ?: DEFAULT_MISMATCH_THRESHOLD_PCT

    suspend fun detectorState(): String? = settings.get(KEY_DETECTOR_STATE)

    suspend fun setDetectorState(json: String?) =
        settings.set(LedgerSettingEntity(KEY_DETECTOR_STATE, json))

    companion object {
        const val KEY_TANK_CAPACITY = "tank_capacity_l"
        const val KEY_DETECT_THRESHOLD = "detect_threshold_pp"
        const val KEY_MISMATCH_THRESHOLD = "mismatch_threshold_pct"
        const val KEY_DETECTOR_STATE = "detector_state"
        const val DEFAULT_DETECT_THRESHOLD_PP = 10
        const val DEFAULT_MISMATCH_THRESHOLD_PCT = 5.0
    }
}
