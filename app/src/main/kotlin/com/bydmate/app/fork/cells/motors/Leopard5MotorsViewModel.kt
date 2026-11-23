package com.bydmate.app.fork.cells.motors

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.ui.tech.TechPanelUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val POLL_MS = 3_000L
private const val STOP_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class Leopard5MotorsViewModel @Inject constructor(
    settings: CellsSettings,
    private val reader: MotorTempsReader,
) : ViewModel() {
    val active: StateFlow<Boolean> = settings.config
        .map { it.profile == CarProfile.LEOPARD5_318 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.config.value.profile == CarProfile.LEOPARD5_318)

    /** Polls only while collected and active; a failed read is null (dashes), never a crash. */
    val temps: StateFlow<MotorTemps?> = active
        .flatMapLatest { on -> if (on) poll() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun poll() = flow {
        while (true) {
            emit(runCatching { reader.read() }.getOrNull())
            delay(POLL_MS)
        }
    }
}

/** The «Моторы» card's state: as it is unless the profile is the Leopard 5, see [applyLeopard5Motors]. */
@Composable
fun leopard5Motors(state: TechPanelUiState, vm: Leopard5MotorsViewModel = hiltViewModel()): TechPanelUiState {
    val active by vm.active.collectAsStateWithLifecycle()
    if (!active) return state
    val temps by vm.temps.collectAsStateWithLifecycle()
    return applyLeopard5Motors(state, temps)
}
