package com.bydmate.app.fork.carhost

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import com.bydmate.app.diagnostics.Trace
import com.bydmate.app.diagnostics.TraceArea
import com.bydmate.app.fork.naviprobe.AsciiText
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
)

/** The car-connection provider is on only while some session runs. */
internal fun providerShouldBeEnabled(states: Collection<HostState>): Boolean = HostState.RUNNING in states

/**
 * App-wide holder of the car host probe: one [CarHostSession] per navigator package, their rows
 * for the UI, and the connection state [CarConnectionProbeProvider] answers.
 *
 * Rolls back by itself: nothing is persisted and nothing connects on its own; the provider is
 * disabled in the manifest, enabled only while a session is RUNNING and disabled again when none
 * is (also on every process start, in case the last process died while connected); each flip
 * sends the library's update broadcast so the navigator asks again. A session ends by itself at
 * [MAX_SESSION_MS] and leaves the map once it is unbound.
 */
object CarHostProbe {
    private val rows = MutableStateFlow<Map<String, ProbeRow>>(emptyMap())
    val states: StateFlow<Map<String, ProbeRow>> = rows.asStateFlow()

    private val sessions = HashMap<String, CarHostSession>()
    @Volatile private var appCtx: Context? = null
    @Volatile private var connectedForTest: Boolean? = null
    private var providerOn = false

    val anyConnected: Boolean
        get() = connectedForTest ?: providerShouldBeEnabled(rows.value.values.map { it.state })

    /** Process start: the provider goes back to disabled; the navigators hear it if it was on. */
    @Synchronized
    fun resetOnStart(ctx: Context) {
        val app = ctx.applicationContext ?: ctx
        appCtx = app
        val pm = app.packageManager
        val was = pm.getComponentEnabledSetting(providerName(app))
        providerOn = false
        if (was == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
            was == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        ) return
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
        sessions.remove(target.pkg)?.stop()
        val label = AsciiText.encode(target.label) ?: "?"
        lateinit var session: CarHostSession
        session = CarHostSession(
            ctx = app,
            target = target,
            giveSurface = giveSurface,
            log = { what, fields -> Trace.event(TraceArea.HUD, what, "pkg" to label, *fields.toTypedArray()) },
            onState = { state -> stateChanged(target, session, state) },
            onTrip = { trip -> changeRow(target, session) { it.copy(lastTrip = trip) } },
        )
        sessions[target.pkg] = session
        rows.update { it + (target.pkg to ProbeRow(target, HostState.IDLE, null, null, null)) }
        session.connect()
    }

    @Synchronized
    fun stop(pkg: String) {
        sessions[pkg]?.stop()
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
        val current = sessions[target.pkg] === session
        if (current) rows.update { m -> m[target.pkg]?.let { m + (target.pkg to it.copy(state = state)) } ?: m }
        // A terminal session has already unbound and freed its surface: it leaves the map.
        if (current && (state == HostState.FAILED || state == HostState.DISCONNECTED)) sessions.remove(target.pkg)
        if (sessions.isEmpty()) stopAll() else syncProvider()
    }

    /** Updates the row of [target] while [session] is still the one for it (a newer one wins). */
    @Synchronized
    private fun changeRow(target: CarAppTarget, session: CarHostSession, change: (ProbeRow) -> ProbeRow) {
        if (sessions[target.pkg] !== session) return
        rows.update { m -> m[target.pkg]?.let { m + (target.pkg to change(it)) } ?: m }
    }

    private fun syncProvider() {
        val want = providerShouldBeEnabled(rows.value.values.map { it.state })
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
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        ctx.packageManager.setComponentEnabledSetting(providerName(ctx), state, PackageManager.DONT_KILL_APP)
        Trace.event(TraceArea.HUD, "carhost_car_connection", "connected" to on)
    }

    /** The library's update broadcast to each navigator, so its `CarConnection` asks again. */
    private fun announce(ctx: Context, pkgs: Collection<String>) {
        for (pkg in pkgs) ctx.sendBroadcast(Intent(CarConnectionProbeProvider.ACTION_UPDATED).setPackage(pkg))
    }
}
