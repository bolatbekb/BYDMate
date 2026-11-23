package com.bydmate.app.fork.cells.coolant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsSettings
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

private const val POLL_MS = 5_000L
private const val STOP_TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CoolantViewModel @Inject constructor(
    settings: CellsSettings,
    private val reader: CoolantReader,
) : ViewModel() {
    val visible: StateFlow<Boolean> = settings.config
        .map { it.profile == CarProfile.LEOPARD5_318 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.config.value.profile == CarProfile.LEOPARD5_318)

    /** Polls only while collected and visible; a failed read is null (a dash), never a crash. */
    val tempC: StateFlow<Int?> = visible
        .flatMapLatest { shown -> if (shown) poll() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun poll() = flow {
        while (true) {
            emit(runCatching { reader.readC() }.getOrNull())
            delay(POLL_MS)
        }
    }
}
