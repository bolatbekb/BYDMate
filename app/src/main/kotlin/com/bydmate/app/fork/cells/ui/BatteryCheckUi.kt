package com.bydmate.app.fork.cells.ui

import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.SpreadPoint
import kotlin.math.abs

// The battery-check window and its entries on «Машина»: state and the pure rules behind them. fork-local.

enum class DetailsTab { DEVIATIONS, PROBES, HISTORY, MEASURES }

/** Live values of the header chips; null shows a dash. */
data class HeaderUi(val soc: Int?, val packV: Int?, val currentA: Double?, val sohPercent: Float?)

/** One measurement of the picker and of «Замеры»; [level] is null until its verdict is known. */
data class PickerItem(
    val id: Long,
    val kind: MeasureKind,
    val ts: Long,
    val spreadMv: Int,
    val level: Level?,
    val preliminary: Boolean,
    val soc: Int? = null,
    val currentA: Double? = null,
    val restMin: Int? = null,
    val probeMinC: Int? = null,
    val probeMaxC: Int? = null,
)

sealed interface ManualUi {
    data object Idle : ManualUi
    data object Taking : ManualUi
    data class Done(val r: ManualResult) : ManualUi
}

/** [framesSeen]: frames came since listening began (tells «stopped» from «never came»); [loading]: the first load of the window runs. */
data class BatteryCheckUi(
    val config: CellsConfig,
    val header: HeaderUi,
    val stream: StreamStatus,
    val picker: List<PickerItem>,
    val selected: Evaluation?,
    val latestLoad: Evaluation?,
    val history: List<SpreadPoint>,
    val manual: ManualUi,
    val details: DetailsTab?,
    val helpOpen: Boolean,
    val framesSeen: Boolean = false,
    val loading: Boolean = false,
    val selectedId: Long? = null,
)

/** What the window can ask of its view model. */
data class BatteryCheckActions(
    val select: (Long) -> Unit,
    val takeManual: () -> Unit,
    val enableCollection: () -> Unit,
    val details: (DetailsTab?) -> Unit,
    val help: (Boolean) -> Unit,
    val dismiss: () -> Unit,
)

/** A string resource with its arguments; an argument may be another [TextRes] or a [Joined] list of them. */
data class TextRes(val res: Int, val args: List<Any> = emptyList()) {
    constructor(res: Int, vararg a: Any) : this(res, a.toList())
}

/** One argument made of several texts, joined with «, ». */
data class Joined(val parts: List<TextRes>)

enum class EntryKind { HIDDEN, COLLECT_OFF, NO_MEASUREMENTS, MISMATCH, NO_STREAM, RESULT }

/** [stopped]: with NO_STREAM, frames came since listening began and then stopped. */
data class EntryRow(
    val kind: EntryKind,
    val level: Level? = null,
    val preliminary: Boolean = false,
    val spreadMv: Int? = null,
    val dateMs: Long? = null,
    val stopped: Boolean = false,
)

/** The row on «Машина»: hidden without a car; a stream problem beats the result; an old result stays with collection off. */
fun entryRow(config: CellsConfig, stream: StreamStatus, latest: Evaluation?, framesSeen: Boolean = false): EntryRow = when {
    config.profile == CarProfile.NONE -> EntryRow(EntryKind.HIDDEN)
    stream.state == StreamState.MISMATCH -> EntryRow(EntryKind.MISMATCH)
    stream.state == StreamState.NO_STREAM -> EntryRow(EntryKind.NO_STREAM, stopped = framesSeen)
    latest != null -> EntryRow(EntryKind.RESULT, latest.level, latest.preliminary, latest.spreadMv, latest.input.ts)
    !config.collect -> EntryRow(EntryKind.COLLECT_OFF)
    else -> EntryRow(EntryKind.NO_MEASUREMENTS)
}

/** [cells] are 1-based, the farthest from the median first. */
data class DeviationGroup(val cls: CellClass, val cells: List<Int>)

private val GROUP_ORDER = listOf(CellClass.STRONGLY_LOW, CellClass.NOTICEABLY_LOW, CellClass.NOTICEABLY_HIGH, CellClass.STRONGLY_HIGH)

fun deviationGroups(e: Evaluation): List<DeviationGroup> = GROUP_ORDER.mapNotNull { cls ->
    e.classes.indices.filter { e.classes[it] == cls }
        .sortedByDescending { abs(e.deviationsMv[it]) }
        .map { it + 1 }
        .takeIf { it.isNotEmpty() }
        ?.let { DeviationGroup(cls, it) }
}

/** [index] is 1-based (Д1…Д24); [tempC] null when the probe gave no value. */
data class ProbeTile(val index: Int, val tempC: Int?, val hottest: Boolean, val coldest: Boolean)

/** The first of equal extremes is marked, as the pack's own 0x43B page does; all equal marks nothing. */
fun probeTiles(probesC: IntArray): List<ProbeTile> {
    val valid = probesC.filter { it != NO_TEMP }
    val max = valid.maxOrNull()
    val min = valid.minOrNull()
    val hot = if (max != null && max != min) probesC.indexOf(max) else -1
    val cold = if (min != null && max != min) probesC.indexOf(min) else -1
    return probesC.mapIndexed { i, t -> ProbeTile(i + 1, t.takeIf { it != NO_TEMP }, i == hot, i == cold) }
}

sealed interface ConditionPart {
    data class Kind(val k: MeasureKind) : ConditionPart
    data class Rest(val min: Int) : ConditionPart
    data object RestUnknown : ConditionPart
    data class Current(val a: Double) : ConditionPart
    data class Probes(val min: Int, val max: Int) : ConditionPart
}

/** The footer's parts: kind, rest (not under load), current, probe range; unknown values are left out, unknown rest is said. */
fun conditionsLine(e: Evaluation): List<ConditionPart> = buildList {
    val m = e.input
    add(ConditionPart.Kind(m.kind))
    if (m.kind != MeasureKind.LOAD) add(m.restMin?.let { ConditionPart.Rest(it) } ?: ConditionPart.RestUnknown)
    m.currentA?.let { add(ConditionPart.Current(it)) }
    val probes = m.probesC.filter { it != NO_TEMP }
    if (probes.isNotEmpty()) add(ConditionPart.Probes(probes.min(), probes.max()))
}

/** A picker row from a list projection; null for a plain snapshot or an unknown kind. */
fun pickerItemOf(s: SnapshotSummary, level: Level? = null, preliminary: Boolean = false): PickerItem? {
    val kind = s.measure?.let { k -> MeasureKind.entries.firstOrNull { it.name == k } } ?: return null
    return PickerItem(s.id, kind, s.ts, s.spreadMv, level, preliminary, s.soc, s.currentA, s.restMin, s.probeMinC, s.probeMaxC)
}

/** Colour of a history point by the thresholds of its own row; cold doubles them and never gives red. */
fun pointLevel(p: SpreadPoint): Level {
    val k = if (p.cold) 2 else 1
    val service = p.row.serviceMv?.times(k)
    return when {
        p.spreadMv <= p.row.normMv * k -> Level.OK
        service != null && p.spreadMv >= service && !p.cold -> Level.SERVICE
        else -> Level.WATCH
    }
}
