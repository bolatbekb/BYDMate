package com.bydmate.app.fork.cells.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.domain.battery.BatteryStateRepository
import com.bydmate.app.fork.cells.collect.CellCollector
import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.CheckView
import com.bydmate.app.fork.cells.data.MeasureKey
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.data.cellsDb
import com.bydmate.app.fork.cells.ui.views.EqualizationData
import com.bydmate.app.fork.cells.ui.views.EqualizationLoader
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.SpreadPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the open window holds besides the settings, the stream and the live values. */
private data class WindowState(
    val picker: List<PickerItem> = emptyList(),
    val selectedId: Long? = null,
    val selected: Evaluation? = null,
    val latestLoad: Evaluation? = null,
    val history: List<SpreadPoint> = emptyList(),
    /** The cell count of the series [history] belongs to. */
    val historyCells: Int? = null,
    val manual: ManualUi = ManualUi.Idle,
    val details: DetailsTab? = null,
    val helpOpen: Boolean = false,
    val soh: Float? = null,
    val loading: Boolean = false,
    val levels: Map<Long, Level> = emptyMap(),
    /** What «Выравнивание» draws, with the id of the measurement it was read for; null data = the read failed. */
    val eq: Pair<Long, EqualizationData?>? = null,
)

private fun WindowState.toUi(c: CellsConfig, s: StreamStatus, framesSeen: Boolean, d: DiParsData?) = BatteryCheckUi(
    config = c,
    header = HeaderUi(d?.soc, d?.hvVoltage, d?.hvCurrent, soh),
    stream = s,
    picker = picker,
    selected = selected,
    latestLoad = latestLoad,
    history = history,
    manual = manual,
    details = details,
    helpOpen = helpOpen,
    framesSeen = framesSeen,
    loading = loading,
    selectedId = selectedId,
    levels = levels,
    // data read for another measurement never shows: the selection may have changed while it loaded
    equalization = eq?.takeIf { it.first == selected?.input?.id }?.second,
    equalizationFailed = eq?.let { it.first == selected?.input?.id && it.second == null } ?: false,
)

/**
 * The battery-check window and the «Машина» row. The window listens to the cells stream while it
 * is open and on screen; every database read runs on [io] and degrades to an empty state. fork-local.
 */
@HiltViewModel
class BatteryCheckViewModel @Inject constructor(
    private val settings: CellsSettings,
    repo: CellRepository,
    private val collector: CellCollector,
    loader: CellHistoryLoader,
    private val battery: BatteryStateRepository,
    @Named("cellsLive") live: StateFlow<DiParsData?>,
) : ViewModel() {
    /** Where the reads and the manual snapshot run; a test seam. */
    internal var io: CoroutineDispatcher = Dispatchers.IO

    private val data = BatteryCheckData(repo, loader)
    private val eqLoader = EqualizationLoader(repo)
    private val openState = MutableStateFlow(false)
    private val window = MutableStateFlow(WindowState())
    private var loadJob: Job? = null
    private var manualJob: Job? = null

    val config: StateFlow<CellsConfig> = settings.config
    val open: StateFlow<Boolean> = openState.asStateFlow()

    val ui: StateFlow<BatteryCheckUi> = combine(config, collector.stream, collector.lastFrameMs, live, window) { c, s, f, d, w ->
        w.toUi(c, s, f > 0L, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), WindowState().toUi(config.value, collector.stream.value, false, null))

    val entry: StateFlow<EntryRow> = combine(config, collector.stream, collector.lastFrameMs, latestEvaluation(config, repo, data) { io }) { c, s, f, e ->
        entryRow(c, s, e, f > 0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), EntryRow(EntryKind.HIDDEN))

    init {
        // «Выравнивание» reads its bounded windows only while it is the chosen view, once per selected measurement.
        viewModelScope.launch {
            combine(config, window) { c, w -> w.selected?.takeIf { c.view == CheckView.EQUALIZATION } }
                .distinctUntilChangedBy { e -> e?.input?.let { it.id to it.ts } }
                .collectLatest { e ->
                    if (e == null) return@collectLatest
                    // a failing read is remembered as such (the chart says there is no data), never an exception
                    val id = e.input.id
                    val d = withContext(io) { cellsDb({ log("equalization", it) }) { eqLoader.load(e) } }
                    window.update { if (it.selected?.input?.id == id) it.copy(eq = id to d) else it }
                }
        }
    }

    fun openWindow() {
        if (openState.value) return
        openState.value = true
        collector.setWindowOpen(true)
        window.value = WindowState(loading = true, manual = manualNow)
        reload(null)
        viewModelScope.launch {
            // One read when the window opens; the vehicle API may be away.
            val soh = withContext(io) { cellsDb({ log("soh", it) }) { battery.refresh().sohPercent } }
            window.update { it.copy(soh = soh) }
        }
    }

    fun closeWindow() {
        if (!openState.value) return
        openState.value = false
        collector.setWindowOpen(false)
        loadJob?.cancel()
        // a snapshot still being taken keeps its line for the next opening
        window.value = WindowState(manual = manualNow)
    }

    /** The screen under the window went away (app in the background) or came back: listen only while it is seen. */
    fun setVisible(visible: Boolean) {
        if (openState.value) collector.setWindowOpen(visible)
    }

    /** A measurement of another cell count brings its own series: the window reloads around it. */
    fun select(id: Long) {
        window.update { it.copy(selectedId = id) }
        viewModelScope.launch {
            val e = withContext(io) { data.evaluate(id) }
            val cells = e?.input?.cellsMv?.size
            if (cells != null && cells != window.value.historyCells && window.value.selectedId == id) {
                reload(id)
                return@launch
            }
            window.update { if (it.selectedId == id) it.copy(selected = e) else it }
        }
    }

    /** One snapshot at a time: a second tap while one is being taken does nothing. */
    fun takeManual() {
        if (manualJob?.isActive == true) return
        window.update { it.copy(manual = ManualUi.Taking) }
        manualJob = viewModelScope.launch {
            val r = withContext(io) { cellsDb({ log("manual", it) }) { collector.takeManual() } }
            window.update { it.copy(manual = r?.let { done -> ManualUi.Done(done) } ?: ManualUi.Error) }
            if (r is ManualResult.Stored && openState.value) reload(r.id)
        }
    }

    fun enableCollection() = settings.setCollect(true)

    fun showDetails(tab: DetailsTab?) = window.update { it.copy(details = tab) }

    fun showHelp(on: Boolean) = window.update { it.copy(helpOpen = on) }

    override fun onCleared() {
        if (openState.value) collector.setWindowOpen(false)
    }

    /** Picker, selection and history first, the levels of the picker and the history after them (cached by measurement). */
    private fun reload(select: Long?) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val d = withContext(io) { data.load(select) }
            window.update {
                it.copy(
                    picker = d.picker, selectedId = d.selectedId, selected = d.selected, latestLoad = d.latestLoad, history = d.history,
                    historyCells = d.historyCells, loading = false,
                )
            }
            val levels = withContext(io) { data.levels(d) }
            window.update { it.copy(picker = levels.picker, levels = levels.byId) }
        }
    }

    private val manualNow: ManualUi get() = if (manualJob?.isActive == true) ManualUi.Taking else ManualUi.Idle

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/**
 * The «Машина» row's evaluation: nothing without a car; the latest rest measurement, else the newest of any kind,
 * re-read when that one or its relax state changes. A failing read gives no evaluation, never an exception.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun latestEvaluation(
    config: StateFlow<CellsConfig>,
    repo: CellRepository,
    data: BatteryCheckData,
    io: () -> CoroutineDispatcher,
): Flow<Evaluation?> =
    config.map { it.profile != CarProfile.NONE }.distinctUntilChanged().flatMapLatest { car ->
        if (!car) {
            flowOf(null)
        } else {
            val newest = repo.measurements(1).map { list -> list.firstOrNull()?.let { MeasureKey(it.id, it.relax) } }
            combine(repo.latestRestKey(), newest) { rest, any -> rest ?: any }.distinctUntilChanged()
                .mapLatest { key -> key?.let { withContext(io()) { data.evaluate(it.id) } } }
        }
    }.catch { t ->
        log("row", t)
        emit(null)
    }

private fun log(op: String, t: Throwable) {
    runCatching { Log.w("BatteryCheck", "battery check $op failed: ${t.javaClass.simpleName}") }
}
