package com.bydmate.app.fork.cells.coolant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.bydmate.app.R
import com.bydmate.app.ui.tech.DASH
import com.bydmate.app.ui.tech.TechRow

/** Coolant temperature row of the «Топливо» card; nothing unless the profile is Leopard 5. */
@Composable
fun CoolantTempRow(vm: CoolantViewModel = hiltViewModel()) {
    val visible by vm.visible.collectAsState()
    if (!visible) return
    val temp by vm.tempC.collectAsState()
    TechRow(
        stringResource(R.string.fork_cells_coolant_label),
        temp?.let { stringResource(R.string.tech_value_deg, it) } ?: DASH,
    )
}
