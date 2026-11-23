package com.bydmate.app.fork.cells.data

import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.data.repository.parseNumericSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val SANE_KWH = 5.0..250.0

/**
 * The battery capacity the driver entered in the app's own settings field («Ёмкость батареи», the author's
 * [SettingsRepository.KEY_BATTERY_CAPACITY]): the battery check's one read of it. The author's default (72.9 kWh, another
 * car) is not used: an empty field gives null, and the car profile's capacity stands in. fork-local.
 */
class PackCapacity(private val settings: SettingsRepository) {
    fun observe(): Flow<Double?> =
        settings.observeString(SettingsRepository.KEY_BATTERY_CAPACITY).map(::capacityOf).catch { emit(null) }
}

/** kWh from the stored text («31,8» or «31.8»); null when it was never entered or is no sane pack size (5…250 kWh). */
internal fun capacityOf(raw: String?): Double? = raw?.parseNumericSetting()?.takeIf { it in SANE_KWH }
