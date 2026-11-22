package com.bydmate.app.fork.cells.collect

import android.os.SystemClock
import android.util.Log
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSource
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CellsSettings
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.stream.CellReading
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ManualResult {
    data class Stored(val id: Long, val conditionsOk: Boolean, val problems: List<ManualProblem>) : ManualResult

    /** NO_STREAM, MISMATCH, HELPER_MISSING, HELPER_OUTDATED, LISTEN_FAILED, CONNECTING (nothing complete in 12 s), IDLE (no car). */
    data class Failed(val why: StreamState) : ManualResult
}

enum class ManualProblem { CURRENT, SHORT_REST, UNKNOWN_REST, CHARGING, COLD }

/**
 * Takes the cell snapshots: driven by the live samples, decided by [CellSchedule], stored through
 * [CellRepository] with their passport. Idle unless a car profile is chosen and collection is on and
 * the car is on, or the battery-check window is open. Never throws out of a step; only
 * cancellation passes. All state is touched under [lock].
 */
@Suppress("LongParameterList") // why: the plan's constructor; the clocks are test seams with defaults
class CellCollector(
    private val settings: CellsSettings,
    private val source: CellSource,
    private val repo: CellRepository,
    private val live: StateFlow<DiParsData?>,
    private val flags: suspend () -> BmsFlags?,
    private val scope: CoroutineScope,
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val uptime: () -> Long = SystemClock::uptimeMillis,
    /** Wall time of TrackingService's last poll (0 = unknown: the live value is never re-fed). */
    private val liveAtMs: () -> Long = { 0L },
) {
    private val link = CellLink(source)
    private val lock = Mutex()
    private val started = AtomicBoolean(false)
    private val window = MutableStateFlow(false)
    private var schedule: CellSchedule? = null
    private var scheduleProfile = CarProfile.NONE
    private var active = false
    private val gate = SessionGate()
    private var lastSampleMs = 0L

    val stream: StateFlow<StreamStatus> = link.stream

    /**
     * Helper clock (elapsedRealtime) of the newest GB frame seen since the collector began to
     * listen; 0 = none. With NO_STREAM it tells «the stream stopped» (> 0) from «it never came» (0).
     */
    val lastFrameMs: StateFlow<Long> = link.lastFrameMs

    /** Idempotent. Collects the live samples with the settings, and the window flag. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val guard = CoroutineExceptionHandler { _, t -> runCatching { Log.w(TAG, "collector stopped: ${t.javaClass.simpleName}") } }
        scope.launch(guard) {
            combine(live, settings.config) { d, c -> d to c }.collect { (d, c) -> lock.withLock { onSample(d, c) } }
        }
        scope.launch(guard) { window.collectLatest { open -> windowLoop(open) } }
        // An unchanged poll is no new state-flow value: while TrackingService still polls, the last one is re-fed,
        // so the rest clock and the schedule keep running at a standstill.
        scope.launch(guard) {
            while (true) {
                delay(REFEED_MS)
                lock.withLock {
                    cellsStep("refeed") {
                        val at = liveAtMs()
                        val fresh = at > 0L && wall() - at in 0L..LIVE_FRESH_MS
                        if (active && fresh && elapsed() - lastSampleMs >= REFEED_MS) onSample(live.value, settings.config.value)
                    }
                }
            }
        }
    }

    /** The battery-check window listens while it is open, even with collection off. */
    fun setWindowOpen(open: Boolean) {
        window.value = open
    }

    /** «Снять замер сейчас»: polls a snapshot each second for up to 12 s and stores the first complete one. */
    suspend fun takeManual(): ManualResult {
        val profile = settings.config.value.profile
        if (profile == CarProfile.NONE) return ManualResult.Failed(StreamState.IDLE)
        lock.withLock { cellsStep("manual listen") { link.listen(true) } }
        var result: ManualResult? = null
        for (attempt in 1..MANUAL_POLLS) {
            result = lock.withLock { manualAttempt(profile) }
                // Before the release below: letting the listener go turns the state to IDLE.
                ?: stream.value.state.takeIf { it in MANUAL_GIVE_UP }?.let { ManualResult.Failed(it) }
            if (result != null) break
            if (attempt < MANUAL_POLLS) delay(MANUAL_POLL_MS)
        }
        lock.withLock { cellsStep("manual release") { if (!active && !window.value && link.listening) link.listen(false) } }
        return result ?: ManualResult.Failed(StreamState.CONNECTING)
    }

    private suspend fun onSample(d: DiParsData?, c: CellsConfig) = cellsStep("sample") {
        val enabled = c.profile != CarProfile.NONE && c.collect
        val s = d?.let { liveSampleOf(it, wall(), elapsed()) }
        if (s == null) {
            if (active && !enabled) end()
            return@cellsStep
        }
        lastSampleMs = s.elapsedMs
        if (c.profile != CarProfile.NONE) settings.noteFull(s)
        val sch = session(gate.move(s, uptime(), enabled, active), s, c.profile) ?: return@cellsStep
        val step = sch.onSample(s)
        step.relax.forEach { repo.updateRelax(it) }
        if (step.due.keepalive) {
            // An attempt counts too: a missing helper is asked again in a minute, not on every sample.
            sch.onHelperContact(s.elapsedMs)
            link.listen(true)
        }
        if (step.due.wantsSnapshot && link.mayRequest(s.elapsedMs)) measure(s, step.due, c.profile, sch)
    }

    /** Applies the gate's [move]; the schedule to feed [s] to, or null when the sample is not fed. */
    private suspend fun session(move: SessionMove, s: LiveSample, profile: CarProfile): CellSchedule? = when (move) {
        SessionMove.STAY_OFF, SessionMove.HOLD -> null
        SessionMove.END -> {
            end()
            null
        }
        SessionMove.CONTINUE -> schedule ?: begin(s, profile, wake = true)
        SessionMove.WAKE -> begin(s, profile, wake = true)
        SessionMove.JOIN -> begin(s, profile, wake = false)
        SessionMove.RESTART -> {
            end()
            begin(s, profile, wake = true)
        }
    }

    /**
     * The session starts with the rest since the last stored snapshot (none after collection was off, R23); [wake]
     * false (collection switched on with the car on) takes no after-parking measurement.
     */
    private suspend fun begin(s: LiveSample, profile: CarProfile, wake: Boolean): CellSchedule {
        val sch = schedule?.takeIf { scheduleProfile == profile }
            ?: CellSchedule(profile.capacityAh) { settings.config.value.intervalSec * MS_PER_SEC }
        schedule = sch
        scheduleProfile = profile
        sch.startSession(s, repo.anchor(settings.anchorClearedAtMs), wake)
        active = true
        link.listen(true)
        return sch
    }

    private suspend fun end() {
        active = false
        // First: a failing write below must not keep the helper listening.
        cellsStep("release") { if (!window.value) link.listen(false) }
        schedule?.endSession()?.forEach { repo.updateRelax(it) }
    }

    private suspend fun measure(s: LiveSample, due: Due, profile: CarProfile, sch: CellSchedule) {
        sch.onHelperContact(s.elapsedMs)
        val read = link.read(profile, if (due.load) due.windowStartMs else 0L, s.elapsedMs) ?: return
        if (!read.listening) link.listen(true)
        val reading = read.reading as? CellReading.Complete ?: return
        if (!saneReading(reading)) return
        val vmax = reading.cellsMv.max()
        val measure = measureFor(due, vmax, s.soc)
        val conditions = RowConditions(
            measure = measure,
            restMin = sch.restMinutes(s.elapsedMs),
            restPeriod = if (measure != MeasureKind.LOAD) sch.restPeriod else null,
            relax = sch.relaxFor(measure, vmax, s.soc),
            daysSinceFull = daysSinceFull(s.wallMs, settings.lastFull100WallMs),
            flags = readFlags(flags),
        )
        val id = repo.insert(snapshotRow(profile, s, reading, conditions)) ?: return
        sch.onStored(s, StoredSnapshot(id, s.elapsedMs, vmax, s.soc, measure)).forEach { repo.updateRelax(it) }
    }

    private suspend fun manualAttempt(profile: CarProfile): ManualResult.Stored? {
        cellsStep("manual") {
            val now = elapsed()
            val read = link.read(profile, 0L, now) ?: return null
            if (!read.listening) link.listen(true)
            val reading = read.reading as? CellReading.Complete ?: return null
            if (!saneReading(reading)) return null
            val s = live.value?.let { liveSampleOf(it, wall(), now) } ?: LiveSample(wall(), now, false, null, null, null, false)
            val sch = schedule?.takeIf { active }
            val restMin = sch?.restMinutes(now)
            val atRest = restConditions(s)
            val problems = manualProblems(s, restMin, reading.probesC.minOrNull())
            val conditions = RowConditions(
                measure = MeasureKind.MANUAL,
                restMin = restMin,
                restPeriod = if (atRest) sch?.restPeriod else null,
                relax = Relax.NOT_NEEDED,
                daysSinceFull = daysSinceFull(s.wallMs, settings.lastFull100WallMs),
                flags = readFlags(flags),
            )
            val id = repo.insert(snapshotRow(profile, s, reading, conditions)) ?: return null
            // R23: taken while collection is off, it must not become the next session's anchor (the drive since is unseen).
            if (!settings.config.value.collect) settings.anchorClearedAtMs = s.wallMs + 1
            val vmax = reading.cellsMv.max()
            // A manual snapshot off rest is a plain snapshot for the schedule: it closes no rest slot.
            sch?.onStored(s, StoredSnapshot(id, now, vmax, s.soc, if (atRest) MeasureKind.MANUAL else null))
                ?.forEach { repo.updateRelax(it) }
            return ManualResult.Stored(id, problems.isEmpty(), problems)
        }
        return null
    }

    /** While open: listen (keepalive each minute) and keep the stream state fresh for the window; store nothing. */
    private suspend fun windowLoop(open: Boolean) {
        if (!open) {
            lock.withLock { cellsStep("window closed") { if (!active && link.listening) link.listen(false) } }
            return
        }
        var lastListen: Long? = null
        while (true) {
            lock.withLock {
                cellsStep("window") {
                    val profile = settings.config.value.profile
                    if (profile == CarProfile.NONE) return@cellsStep
                    val now = elapsed()
                    val last = lastListen
                    if (last == null || now - last >= CellSchedule.KEEPALIVE_MS || now < last) {
                        lastListen = now
                        link.listen(true)
                    }
                    if (link.mayRequest(now)) link.read(profile, 0L, now)?.let { if (!it.listening) link.listen(true) }
                }
            }
            delay(WINDOW_POLL_MS)
        }
    }

    private companion object {
        const val TAG = "CellCollector"
        const val MS_PER_SEC = 1_000L
        const val MANUAL_POLLS = 12
        const val MANUAL_POLL_MS = 1_000L
        const val WINDOW_POLL_MS = 5_000L
        const val REFEED_MS = 30_000L

        /** As the home screen's LIVE_FRESH_MS: TrackingService polls at most a minute apart. */
        const val LIVE_FRESH_MS = 90_000L
        val MANUAL_GIVE_UP = setOf(
            StreamState.NO_STREAM, StreamState.MISMATCH, StreamState.HELPER_MISSING,
            StreamState.HELPER_OUTDATED, StreamState.LISTEN_FAILED,
        )
    }
}
