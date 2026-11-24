package com.bydmate.app.fork.carhost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import com.bydmate.app.diagnostics.Trace
import com.bydmate.app.diagnostics.TraceArea
import com.bydmate.app.data.autoservice.AdbOnDeviceClient
import com.bydmate.app.fork.naviprobe.AsciiText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProbeRow(
    val target: CarAppTarget,
    val state: HostState,
    val guard: GuardVerdict?,
    val lastTrip: String?,
    val countdown: Int?,
    /** Why the navigator refused (masked, at most [FAIL_REASON_MAX] chars); null if not refused. */
    val failReason: String? = null,
)

internal const val FAIL_REASON_MAX = 80

/** The refusal reason out of a `carhost_refused` log line; null for any other line. */
internal fun refusalReason(what: String, fields: List<Pair<String, String>>): String? {
    if (what != "carhost_refused") return null
    val v = fields.firstOrNull { it.first == "reason" }?.second
        ?: fields.firstOrNull { it.first == "error" }?.second
    return v?.take(FAIL_REASON_MAX)?.ifBlank { null }
}

/**
 * The car-connection provider is on only while some session runs and is not being stopped
 * ([stopping]: packages whose stop is asked but not yet handled by their session thread).
 */
internal fun providerShouldBeEnabled(states: Map<String, HostState>, stopping: Set<String>): Boolean =
    states.any { (pkg, state) -> state == HostState.RUNNING && pkg !in stopping }

/**
 * App-wide holder of the car host probe: one [CarHostSession] per navigator package, their rows
 * for the UI, and the connection state [CarConnectionProbeProvider] answers.
 *
 * Rolls back by itself: nothing is persisted and nothing connects on its own; the provider is
 * disabled in the manifest, enabled only while a session is RUNNING and disabled again when none
 * is (also on every process start, in case the last process died while connected); "off" is the
 * manifest default, so nothing lasting is left in the package settings; each flip
 * sends the library's update broadcast so the navigator asks again. A session ends by itself at
 * [MAX_SESSION_MS] and leaves the map once it is unbound.
 */
object CarHostProbe {
    const val COUNTDOWN_S = 10
    private val SNAPSHOT_AT_S = listOf(3, 10, 30)
    private const val AFTER_STOP_S = 3

    private val rows = MutableStateFlow<Map<String, ProbeRow>>(emptyMap())
    val states: StateFlow<Map<String, ProbeRow>> = rows.asStateFlow()

    private val sessions = HashMap<String, CarHostSession>()
    @Volatile private var appCtx: Context? = null
    @Volatile private var connectedForTest: Boolean? = null
    private val stopping = HashSet<String>()
    private var providerOn = false

    // UI guard: one job per package (countdown, snapshots); a stop cancels it.
    private val guardScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guards = HashMap<String, Job>()
    private var guardRuns = 0

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GuardDeps {
        fun adb(): AdbOnDeviceClient
    }

    val anyConnected: Boolean
        @Synchronized get() = connectedForTest ?: wanted()

    private fun wanted() = providerShouldBeEnabled(rows.value.mapValues { it.value.state }, stopping)

    /** Process start: the provider goes back to disabled; the navigators hear it if it was on. */
    @Synchronized
    fun resetOnStart(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        appCtx = app
        val pm = app.packageManager
        val was = pm.getComponentEnabledSetting(providerName(app))
        providerOn = false
        if (was != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return
        setProvider(app, false)
        Trace.event(TraceArea.HUD, "carhost_provider_reset")
        val pkgs = try {
            CarAppTargets.discover(pm).map { it.pkg }
        } catch (e: RuntimeException) {
            emptyList()
        }
        announce(app, pkgs)
    }

    @Synchronized
    fun connect(ctx: Context, target: CarAppTarget, giveSurface: Boolean) {
        val app = ctx.applicationContext ?: ctx
        appCtx = app
        guards.remove(target.pkg)?.cancel()
        sessions.remove(target.pkg)?.stop()
        stopping.remove(target.pkg)
        val label = AsciiText.encode(target.label) ?: "?"
        lateinit var session: CarHostSession
        session = CarHostSession(
            ctx = app,
            target = target,
            giveSurface = giveSurface,
            log = { what, fields ->
                Trace.event(TraceArea.HUD, what, "pkg" to label, *fields.toTypedArray())
                refusalReason(what, fields)?.let { r -> changeRow(target, session) { it.copy(failReason = r) } }
            },
            onState = { state -> stateChanged(target, session, state) },
            onTrip = { trip -> changeRow(target, session) { it.copy(lastTrip = trip) } },
        )
        sessions[target.pkg] = session
        rows.update { it + (target.pkg to ProbeRow(target, HostState.IDLE, null, null, null)) }
        syncProvider()
        val runner = try {
            UiGuardRunner(EntryPointAccessors.fromApplication(app, GuardDeps::class.java).adb())
        } catch (e: RuntimeException) {
            null
        }
        val n = ++guardRuns
        guards[target.pkg] = guardScope.launch {
            val me = coroutineContext[Job]
            try {
                guardFlow(target, session, runner, n)
            } finally {
                synchronized(this@CarHostProbe) { if (guards[target.pkg] === me) guards.remove(target.pkg) }
            }
        }
    }

    /** Countdown, S0, connect, S1 at 3/10/30 s; BLOCKED stops the session and takes S2. */
    private suspend fun guardFlow(target: CarAppTarget, session: CarHostSession, runner: UiGuardRunner?, n: Int) {
        val me = coroutineContext[Job]
        val pkg = target.pkg
        for (left in COUNTDOWN_S downTo 1) {
            changeRow(target, session) { it.copy(countdown = left) }
            delay(1000)
        }
        changeRow(target, session) { it.copy(countdown = null) }
        val before = runner?.snapshot("before", n)
        coroutineContext.ensureActive()
        val started = synchronized(this) {
            if (guards[pkg] !== me || sessions[pkg] !== session) false else { session.connect(); true }
        }
        if (!started) return
        if (before == null) {
            Trace.event(TraceArea.HUD, "carhost_guard", "n" to n, "verdict" to "UNKNOWN", "reason" to "no_runner")
            return
        }
        var t = 0
        for (at in SNAPSHOT_AT_S) {
            delay((at - t) * 1000L)
            t = at
            if (!stillRunning(pkg, session, me)) return
            var after = runner.snapshot("${at}s", n)
            coroutineContext.ensureActive()
            var (verdict, reason) = UiGuard.verdict(before, after, pkg)
            if (verdict == GuardVerdict.UNKNOWN && reason == "no_reading") {
                delay(1000)
                after = runner.snapshot("${at}s-retry", n)
                coroutineContext.ensureActive()
                UiGuard.verdict(before, after, pkg).let { verdict = it.first; reason = it.second }
            }
            traceGuard("${at}s", n, verdict, reason, before, after, null)
            changeRow(target, session) { it.copy(guard = worse(it.guard, verdict)) }
            if (verdict == GuardVerdict.BLOCKED) {
                synchronized(this) {
                    if (guards[pkg] !== me || sessions[pkg] !== session) return
                    stopForGuard(pkg)
                }
                delay(AFTER_STOP_S * 1000L)
                val s2 = runner.snapshot("after", n)
                coroutineContext.ensureActive()
                val restored = UiGuard.verdict(before, s2, pkg).first.let {
                    it == GuardVerdict.OK || it == GuardVerdict.CHANGED
                }
                traceGuard("after", n, verdict, reason, before, s2, restored)
                return
            }
        }
    }

    @Synchronized
    private fun stillRunning(pkg: String, session: CarHostSession, me: Job?) =
        guards[pkg] === me && sessions[pkg] === session

    private fun worse(old: GuardVerdict?, new: GuardVerdict): GuardVerdict =
        if (old == null || new.ordinal > old.ordinal && old != GuardVerdict.BLOCKED) new else old

    private fun traceGuard(
        tag: String, n: Int, v: GuardVerdict, reason: String,
        before: UiSnapshot, after: UiSnapshot, restored: Boolean?,
    ) {
        val f = mutableListOf<Pair<String, Any?>>(
            "n" to n, "tag" to tag, "verdict" to v.name, "reason" to reason,
            "nodes_before" to before.nodes, "nodes_after" to after.nodes,
        )
        if (restored != null) f += "restored" to if (restored) "yes" else "no"
        Trace.event(TraceArea.HUD, "carhost_guard", *f.toTypedArray())
    }

    @Synchronized
    fun stop(pkg: String) {
        guards.remove(pkg)?.cancel()
        rows.update { m -> m[pkg]?.let { m + (pkg to it.copy(countdown = null)) } ?: m }
        stopForGuard(pkg)
    }

    /** Stop that leaves the running guard job alone (it is the one stopping, then takes S2). */
    @Synchronized
    private fun stopForGuard(pkg: String) {
        val s = sessions[pkg] ?: return
        stopping.add(pkg)
        syncProvider()
        s.stop()
    }

    @Synchronized
    fun navigate(pkg: String, query: String) {
        sessions[pkg]?.navigate(query)
    }

    /**
     * Every session stops (each unbinds and frees its surface on its own thread), the provider
     * goes off and the navigators hear it.
     */
    @Synchronized
    fun stopAll() {
        // Marked first: a session that is still RUNNING until its thread gets the stop must not
        // turn the provider back on.
        guards.values.forEach { it.cancel() }
        guards.clear()
        rows.update { m -> m.mapValues { it.value.copy(countdown = null) } }
        stopping.addAll(rows.value.keys)
        sessions.values.forEach { it.stop() }
        providerOn = false
        val ctx = appCtx ?: return
        setProvider(ctx, false)
        announce(ctx, rows.value.keys)
    }

    @VisibleForTesting
    fun setConnectedForTest(connected: Boolean?) {
        connectedForTest = connected
    }

    @Synchronized
    private fun stateChanged(target: CarAppTarget, session: CarHostSession, state: HostState) {
        if (sessions[target.pkg] === session) applyState(target.pkg, state) else afterChange()
    }

    /** A state of the current session of [pkg]; a terminal one (already unbound) leaves the map. */
    @VisibleForTesting
    @Synchronized
    internal fun applyState(pkg: String, state: HostState) {
        rows.update { m -> m[pkg]?.let { m + (pkg to it.copy(state = state)) } ?: m }
        if (state == HostState.FAILED || state == HostState.DISCONNECTED) {
            sessions.remove(pkg)
            // A guard-initiated stop (pkg in stopping) lets the guard take its after-shot.
            if (pkg !in stopping) guards.remove(pkg)?.cancel()
            stopping.remove(pkg)
        }
        afterChange()
    }

    private fun afterChange() {
        if (sessions.isEmpty()) stopAll() else syncProvider()
    }

    /** A row without a session, for tests of the provider decision. */
    @VisibleForTesting
    @Synchronized
    internal fun putRowForTest(target: CarAppTarget, state: HostState) {
        stopping.remove(target.pkg)
        rows.update { it + (target.pkg to ProbeRow(target, state, null, null, null)) }
    }

    @VisibleForTesting
    @Synchronized
    internal fun clearForTest() {
        guards.values.forEach { it.cancel() }
        guards.clear()
        sessions.clear()
        stopping.clear()
        rows.value = emptyMap()
        providerOn = false
        connectedForTest = null
    }

    /** Updates the row of [target] while [session] is still the one for it (a newer one wins). */
    @Synchronized
    private fun changeRow(target: CarAppTarget, session: CarHostSession, change: (ProbeRow) -> ProbeRow) {
        if (sessions[target.pkg] !== session) return
        rows.update { m -> m[target.pkg]?.let { m + (target.pkg to change(it)) } ?: m }
    }

    private fun syncProvider() {
        val want = wanted()
        if (want == providerOn) return
        providerOn = want
        val ctx = appCtx ?: return
        setProvider(ctx, want)
        announce(ctx, rows.value.keys)
    }

    private fun providerName(ctx: Context) = ComponentName(ctx, CarConnectionProbeProvider::class.java)

    private fun setProvider(ctx: Context, on: Boolean) {
        val state = if (on) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT // the manifest's: disabled
        }
        ctx.packageManager.setComponentEnabledSetting(providerName(ctx), state, PackageManager.DONT_KILL_APP)
        Trace.event(TraceArea.HUD, "carhost_car_connection", "connected" to on)
    }

    /** The library's update broadcast to each navigator, so its `CarConnection` asks again. */
    private fun announce(ctx: Context, pkgs: Collection<String>) {
        for (pkg in pkgs) ctx.sendBroadcast(Intent(CarConnectionProbeProvider.ACTION_UPDATED).setPackage(pkg))
    }
}
