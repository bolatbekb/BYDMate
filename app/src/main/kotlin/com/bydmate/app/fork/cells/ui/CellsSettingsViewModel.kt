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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    val stats: StateFlow<CellStats?> = repo.stats()
        .catch { emit(CellStats(CellStatsRow(0, null, 0, 0, 0), 0L)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val noticeState = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = noticeState.asStateFlow()

    fun setProfile(p: CarProfile) = settings.setProfile(p)
    fun setCollect(on: Boolean) = settings.setCollect(on)
    fun setInterval(sec: Int) = settings.setIntervalSec(sec)
    fun setView(v: CheckView) = settings.setView(v)

    fun exportCsv() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                repo.exportCsv(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), System.currentTimeMillis())
            }
            noticeState.value = file?.let { ctx.getString(R.string.fork_cells_export_done, it.name) }
                ?: ctx.getString(R.string.fork_cells_export_failed)
        }
    }

    fun clear() {
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.clear() } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
