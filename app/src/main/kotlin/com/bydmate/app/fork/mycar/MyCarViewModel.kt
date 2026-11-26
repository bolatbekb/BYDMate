package com.bydmate.app.fork.mycar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.dtc.CarAutoType
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.nativestack.VehicleKindMonitor
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.ledger.data.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * «Мой автомобиль» state. The drive type is read-only (auto-detected, [VehicleKindMonitor]); the model
 * is the fork [CarProfile] (kept in [CellsSettings]) and is autofilled once from the head unit code;
 * battery size and tank write the same keys the other screens use. fork-local.
 */
@HiltViewModel
class MyCarViewModel @Inject constructor(
    private val myCar: MyCarSettings,
    private val cells: CellsSettings,
    private val settings: SettingsRepository,
    private val ledger: LedgerRepository,
    vehicleKind: VehicleKindMonitor,
    private val helper: HelperClient,
) : ViewModel() {

    val carName: StateFlow<String> = myCar.carName
    val driveKind: StateFlow<VehicleKind> = vehicleKind.kind
    val profile: StateFlow<CarProfile> =
        cells.config.map { it.profile }.stateIn(viewModelScope, SharingStarted.Eagerly, cells.config.value.profile)

    private val batteryState = MutableStateFlow("")
    val batteryCapacity: StateFlow<String> = batteryState.asStateFlow()

    private val tankState = MutableStateFlow("")
    val tankCapacity: StateFlow<String> = tankState.asStateFlow()

    init {
        viewModelScope.launch {
            batteryState.value =
                settings.getString(SettingsRepository.KEY_BATTERY_CAPACITY, SettingsRepository.DEFAULT_BATTERY_CAPACITY)
            tankState.value = ledger.tankCapacityL()?.let(::trimNumber) ?: ""
        }
        viewModelScope.launch {
            val code = withContext(Dispatchers.IO) { runCatching { CarAutoType.read(helper) }.getOrNull() }
            autofillModelOnce(
                alreadyAutofilled = myCar.modelAutofilled,
                currentProfile = cells.config.value.profile,
                code = code,
                setProfile = cells::setProfile,
                markAutofilled = myCar::markModelAutofilled,
            )
        }
    }

    fun setCarName(name: String) = myCar.setCarName(name)

    fun setProfile(p: CarProfile) = cells.setProfile(p)

    fun setBatteryCapacity(value: String) {
        batteryState.value = value
        viewModelScope.launch { settings.setString(SettingsRepository.KEY_BATTERY_CAPACITY, value) }
    }

    fun setTankCapacity(value: String) {
        tankState.value = value
        viewModelScope.launch { ledger.setTankCapacityL(value.replace(',', '.').toDoubleOrNull()) }
    }
}

/** A whole number without a trailing ".0", else the plain decimal. */
private fun trimNumber(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
