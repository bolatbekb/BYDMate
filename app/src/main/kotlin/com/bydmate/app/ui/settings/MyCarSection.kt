package com.bydmate.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.ui.profileLabelRes
import com.bydmate.app.fork.mycar.MyCarViewModel
import com.bydmate.app.ui.skin.Card
import com.bydmate.app.ui.skin.RoundedCornerShape
import com.bydmate.app.ui.theme.CardSurfaceElevated

/**
 * Settings › «Мой автомобиль»: name, auto-detected drive type (read-only), car model (for the
 * Leopard-5 temperatures), battery size and — for a hybrid — tank size.
 * Writes the same keys the other screens use; the drive type is never set here. fork-local.
 */
@Composable
internal fun MyCarSection(vm: MyCarViewModel = hiltViewModel()) {
    val name by vm.carName.collectAsStateWithLifecycle()
    val driveKind by vm.driveKind.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val battery by vm.batteryCapacity.collectAsStateWithLifecycle()
    val tank by vm.tankCapacity.collectAsStateWithLifecycle()

    SectionHeader(text = name.ifBlank { stringResource(R.string.fork_my_car_title) })
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsTextField(
                label = stringResource(R.string.fork_my_car_name_label),
                value = name,
                onValueChange = vm::setCarName,
                keyboardType = KeyboardType.Text,
            )
            SettingDivider()
            SettingInfoRow(
                title = stringResource(R.string.fork_my_car_drive_title),
                description = driveKindText(driveKind),
            )
            SettingDivider()
            val profiles = CarProfile.entries
            SettingChipRow(
                title = stringResource(R.string.fork_cells_car_title),
                description = stringResource(R.string.fork_cells_car_desc),
                options = profiles.map { stringResource(profileLabelRes(it)) },
                selectedIndex = profiles.indexOf(profile),
                onSelect = { vm.setProfile(profiles[it]) },
            )
            SettingDivider()
            SettingsTextField(
                label = stringResource(R.string.settings_battery_capacity_label),
                value = battery,
                onValueChange = vm::setBatteryCapacity,
                keyboardType = KeyboardType.Decimal,
            )
            if (driveKind == VehicleKind.HYBRID) {
                SettingsTextField(
                    label = stringResource(R.string.fork_my_car_tank_label),
                    value = tank,
                    onValueChange = vm::setTankCapacity,
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }
    }
}

@Composable
private fun driveKindText(kind: VehicleKind): String = stringResource(
    when (kind) {
        VehicleKind.HYBRID -> R.string.fork_my_car_drive_hybrid
        VehicleKind.EV -> R.string.fork_my_car_drive_ev
        VehicleKind.UNKNOWN -> R.string.fork_my_car_drive_unknown
    },
)
