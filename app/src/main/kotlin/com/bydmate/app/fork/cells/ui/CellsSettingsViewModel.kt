package com.bydmate.app.fork.cells.ui

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.R
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellStats
import com.bydmate.app.fork.cells.data.CellStatsRow
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.CheckView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings block «Диагностика батареи»: profile, collection, interval, view, stored data. fork-local. */
@HiltViewModel
class CellsSettingsViewModel @Inject constructor(
    private val settings: CellsSettings,
    private val repo: CellRepository,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {
    val config: StateFlow<CellsConfig> = settings.config

    // No car profile: the cells database is neither opened nor created by this screen.
    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<CellStats?> = config
        .map { it.profile != CarProfile.NONE }
        .distinctUntilChanged()
        .flatMapLatest { on -> if (on) repo.stats() else flowOf(null) }
        .catch { emit(CellStats(CellStatsRow(0, null, 0, 0, 0), 0L)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val noticeState = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = noticeState.asStateFlow()

    fun setProfile(p: CarProfile) = settings.setProfile(p)
    fun setCollect(on: Boolean) = settings.setCollect(on)
    fun setInterval(sec: Int) = settings.setIntervalSec(sec)
    fun setView(v: CheckView) = settings.setView(v)

    private val exportingState = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = exportingState.asStateFlow()

    fun exportCsv() {
        if (!exportingState.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    repo.exportCsv(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), System.currentTimeMillis())
                }
                noticeState.value = file?.let { ctx.getString(R.string.fork_cells_export_done, it.name) }
                    ?: ctx.getString(R.string.fork_cells_export_failed)
            } finally {
                exportingState.value = false
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.clear() }
            noticeState.value = if (ok) null else ctx.getString(R.string.fork_cells_clear_failed)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
