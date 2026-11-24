@file:SuppressLint("RestrictedApi")

package com.bydmate.app.fork.carhost

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.location.Location
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import androidx.car.app.AppInfo
import androidx.car.app.FailureResponse
import androidx.car.app.HandshakeInfo
import androidx.car.app.IAppHost
import androidx.car.app.IAppManager
import androidx.car.app.ICarApp
import androidx.car.app.ICarHost
import androidx.car.app.IOnDoneCallback
import androidx.car.app.ISurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.TemplateWrapper
import androidx.car.app.navigation.INavigationHost
import androidx.car.app.navigation.model.Trip
import androidx.car.app.serialization.Bundleable
import androidx.car.app.serialization.BundlerException
import androidx.car.app.versioning.CarAppApiLevels
import com.bydmate.app.fork.naviprobe.AsciiText
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** The car-app API level this host speaks: the latest one of the linked library. */
val HOST_LATEST: Int get() = CarAppApiLevels.getLatest()

/**
 * One host session with one navigator's `CarAppService`: binds it, runs [HostFlow] and carries out
 * every [HostAction] it returns, feeds every binder result back as a [HostEvent]. Records what the
 * app sends (app info, templates, trips); never writes anything to the car.
 *
 * Threads: every binder call and every [HostFlow] step runs on this session's own
 * [HandlerThread]; host stubs are called on binder threads and post to it. [log] gets event names
 * and fields without the target (the caller adds it); [onState] gets every state change.
 */
class CarHostSession(
    private val ctx: Context,
    private val target: CarAppTarget,
    private val giveSurface: Boolean,
    private val log: (String, List<Pair<String, String>>) -> Unit,
    private val onState: (HostState) -> Unit,
    private val onTrip: (String) -> Unit = {},
) {
    private val thread = HandlerThread("carhost-session").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val flow = HostFlow(HOST_LATEST)

    // Touched only on the session thread.
    private var lastState = flow.state
    private var bound = false
    private var app: ICarApp? = null
    private var appManager: IAppManager? = null
    private var templateWanted = false
    private var lastTemplate: List<Pair<String, String>>? = null
    private var lastTripLogAt = 0L
    private var imageReader: ImageReader? = null
    private var surfaceCallback: ISurfaceCallback? = null
    private var surfaceContainer: Bundleable? = null

    private val askedApp = AtomicBoolean(false)
    private val askedNavigation = AtomicBoolean(false)

    private val deathRecipient = IBinder.DeathRecipient { post { feed(HostEvent.BinderDied) } }
    private val fetchTemplate = Runnable { fetchTemplateNow() }
    private val capReached = Runnable { feed(HostEvent.CapReached) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (!bound || service == null) return
            app = ICarApp.Stub.asInterface(service)
            try {
                service.linkToDeath(deathRecipient, 0)
            } catch (e: RemoteException) {
                feed(HostEvent.BinderDied)
                return
            }
            feed(HostEvent.Bound)
        }

        override fun onServiceDisconnected(name: ComponentName?) = feed(HostEvent.BinderDied)
        override fun onBindingDied(name: ComponentName?) = feed(HostEvent.BinderDied)
        override fun onNullBinding(name: ComponentName?) = feed(HostEvent.CallFailed("bind", "null binding"))
    }

    fun connect() = post {
        if (flow.state != HostState.IDLE || bound) return@post
        log("carhost_bind", listOf("surface" to giveSurface.toString(), "host_api" to HOST_LATEST.toString()))
        handler.postDelayed(capReached, MAX_SESSION_MS)
        val intent = Intent(CarAppTargets.ACTION).setComponent(ComponentName(target.pkg, target.service))
        // Bound before the call: a false return still leaves the connection registered.
        bound = true
        val ok = try {
            ctx.bindService(intent, Context.BIND_AUTO_CREATE, executor, connection)
        } catch (e: SecurityException) {
            feed(HostEvent.CallFailed("bind", errorText(e)))
            return@post
        }
        if (!ok) feed(HostEvent.CallFailed("bind", "bindService returned false"))
    }

    fun stop() = post { feed(HostEvent.StopAsked) }

    fun navigate(query: String) = post {
        val a = app
        if (a == null || flow.state != HostState.RUNNING) {
            log("carhost_navigate_skipped", listOf("state" to flow.state.name))
            return@post
        }
        val intent = Intent(ACTION_NAVIGATE, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
        log("carhost_navigate", listOf("query_len" to query.length.toString()))
        guarded("navigate") { a.onNewIntent(intent, done("navigate") { log("carhost_navigate_ok", emptyList()) }) }
    }

    private fun post(block: () -> Unit) {
        handler.post(block)
    }

    // ---- flow ----

    private fun feed(e: HostEvent) {
        flow.on(e).forEach(::run)
        val s = flow.state
        if (s == lastState) return
        lastState = s
        log("carhost_state", listOf("state" to s.name))
        if (s == HostState.FAILED || s == HostState.DISCONNECTED) release()
        onState(s)
    }

    private fun run(a: HostAction) {
        when (a) {
            HostAction.GetAppInfo -> withApp("getAppInfo") { it.getAppInfo(done("getAppInfo", ::appInfoGot)) }
            is HostAction.Handshake -> withApp("handshake") {
                log("carhost_handshake", listOf("level" to a.level.toString()))
                val info = Bundleable.create(HandshakeInfo(ctx.packageName, a.level))
                it.onHandshakeCompleted(info, done("handshake") { feed(HostEvent.HandshakeDone) })
            }
            HostAction.Create -> withApp("create") {
                val intent = Intent(CarAppTargets.ACTION).setComponent(ComponentName(target.pkg, target.service))
                it.onAppCreate(carHost, intent, ctx.resources.configuration, done("create") { feed(HostEvent.Created) })
            }
            HostAction.StartResume -> withApp("start") { app ->
                app.onAppStart(done("start") {
                    withApp("resume") { it.onAppResume(done("resume") { feed(HostEvent.Started) }) }
                })
            }
            HostAction.GetManagers -> withApp("getManager") {
                it.getManager(MANAGER_APP, done("getManager") { r -> appManagerGot(r) })
                it.getManager(MANAGER_NAVIGATION, done("getManager") {
                    log("carhost_manager", listOf("type" to MANAGER_NAVIGATION))
                })
            }
            HostAction.FetchTemplate -> {
                handler.removeCallbacks(fetchTemplate)
                handler.postDelayed(fetchTemplate, TEMPLATE_DEBOUNCE_MS)
            }
            HostAction.Unbind -> unbind()
            is HostAction.Log -> log(a.what, a.fields)
        }
    }

    private fun appInfoGot(r: Bundleable?) {
        val info = r?.get() as AppInfo
        log("carhost_appinfo", CarHostDigest.appInfo(info))
        feed(HostEvent.AppInfoGot(info.minCarAppApiLevel, info.latestCarAppApiLevel))
    }

    private fun appManagerGot(r: Bundleable?) {
        // The raw binder, not Bundleable.get(): that one looks up the app's own stub class by name.
        val m = IAppManager.Stub.asInterface(binderOf(r))
        appManager = m
        log("carhost_manager", listOf("type" to MANAGER_APP, "ok" to (m != null).toString()))
        if (templateWanted) fetchTemplateNow()
    }

    private fun fetchTemplateNow() {
        if (flow.state != HostState.RUNNING) return
        val m = appManager
        if (m == null) {
            templateWanted = true
            return
        }
        templateWanted = false
        guarded("getTemplate") {
            m.getTemplate(done("getTemplate") { r ->
                val digest = CarHostDigest.template((r?.get() as? TemplateWrapper)?.template)
                if (digest != lastTemplate) {
                    lastTemplate = digest
                    log("carhost_template", digest)
                }
            })
        }
    }

    private fun tripGot(b: Bundleable?) {
        val trip = try {
            b?.get() as? Trip
        } catch (e: BundlerException) {
            log("carhost_trip_bad", listOf("error" to errorText(e)))
            return
        } ?: return
        val digest = CarHostDigest.trip(trip)
        onTrip(digest.joinToString(" ") { (k, v) -> "$k=$v" })
        val now = SystemClock.elapsedRealtime()
        if (lastTripLogAt == 0L || now - lastTripLogAt >= TRIP_LOG_EVERY_MS) {
            lastTripLogAt = now
            log("carhost_trip", digest)
        }
    }

    @SuppressLint("WrongConstant") // ImageReader takes PixelFormat.RGBA_8888 (documented).
    private fun giveSurfaceTo(cb: ISurfaceCallback) {
        if (imageReader != null || flow.state == HostState.FAILED || flow.state == HostState.DISCONNECTED) return
        val reader = ImageReader.newInstance(SURFACE_W, SURFACE_H, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close() }, handler)
        imageReader = reader
        log("carhost_surface", listOf("w" to SURFACE_W.toString(), "h" to SURFACE_H.toString()))
        guarded("surface") {
            val container = Bundleable.create(SurfaceContainer(reader.surface, SURFACE_W, SURFACE_H, SURFACE_DPI))
            surfaceCallback = cb
            surfaceContainer = container
            cb.onSurfaceAvailable(container, done("surface") { log("carhost_surface_ok", emptyList()) })
        }
    }

    // ---- binder plumbing ----

    private fun withApp(call: String, block: (ICarApp) -> Unit) {
        val a = app ?: return feed(HostEvent.CallFailed(call, "no app binder"))
        guarded(call) { block(a) }
    }

    /** A binder call; a failure on our side becomes [HostEvent.CallFailed]. */
    private fun guarded(call: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RemoteException) {
            feed(HostEvent.CallFailed(call, errorText(e)))
        } catch (e: BundlerException) {
            feed(HostEvent.CallFailed(call, errorText(e)))
        } catch (e: RuntimeException) {
            feed(HostEvent.CallFailed(call, errorText(e)))
        }
    }

    /** The app's answer to one call, handled on the session thread. */
    private fun done(call: String, onOk: (Bundleable?) -> Unit): IOnDoneCallback = object : IOnDoneCallback.Stub() {
        override fun onSuccess(response: Bundleable?) = post {
            if (!ended()) guarded(call) { onOk(response) }
        }

        override fun onFailure(failure: Bundleable?) = post {
            if (ended()) return@post
            val trace = try {
                (failure?.get() as? FailureResponse)?.stackTrace
            } catch (e: BundlerException) {
                null
            }
            feed(HostEvent.CallFailed(call, clean(trace?.lineSequence()?.firstOrNull()) ?: "unknown"))
        }
    }

    private fun ended() = flow.state == HostState.FAILED || flow.state == HostState.DISCONNECTED

    private fun unbind() {
        if (!bound) return
        bound = false
        try {
            ctx.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            log("carhost_unbind_skipped", listOf("error" to errorText(e)))
        }
    }

    /** End of the session: binding, death link, surface, timers and the thread go. */
    private fun release() {
        takeSurfaceBack()
        unbind()
        handler.removeCallbacks(capReached)
        handler.removeCallbacks(fetchTemplate)
        try {
            app?.asBinder()?.unlinkToDeath(deathRecipient, 0)
        } catch (e: NoSuchElementException) {
            // Never linked.
        }
        app = null
        appManager = null
        imageReader?.close()
        imageReader = null
        thread.quitSafely()
    }

    /** Tells the app its surface is gone before the reader behind it closes. */
    private fun takeSurfaceBack() {
        val cb = surfaceCallback ?: return
        val container = surfaceContainer
        surfaceCallback = null
        surfaceContainer = null
        try {
            cb.onSurfaceDestroyed(container, ignored)
        } catch (e: RemoteException) {
            // The app is gone already.
        } catch (e: RuntimeException) {
            // Same: nothing left to tell.
        }
    }

    /** For calls made while the session ends: the answer no longer matters. */
    private val ignored = object : IOnDoneCallback.Stub() {
        override fun onSuccess(response: Bundleable?) = Unit
        override fun onFailure(failure: Bundleable?) = Unit
    }

    private fun errorText(e: Throwable): String =
        clean(e.message?.lineSequence()?.firstOrNull())?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.simpleName

    /** An error line for the trace: the target's package name is masked, text is ASCII. */
    private fun clean(s: String?): String? =
        AsciiText.encode(s?.replace(target.pkg, "<pkg>"), CarHostDigest.MAX_VALUE)

    // ---- host stubs (binder threads) ----

    private val appHost = object : IAppHost.Stub() {
        override fun invalidate() = post { if (!ended()) run(HostAction.FetchTemplate) }

        override fun showToast(text: CharSequence?, duration: Int) = post {
            log("carhost_toast", listOfNotNull(
                AsciiText.encode(text, CarHostDigest.MAX_VALUE)?.let { "text" to it },
                "duration" to duration.toString(),
            ))
        }

        override fun setSurfaceCallback(cb: ISurfaceCallback?) = post {
            when {
                cb == null -> log("carhost_app_host", listOf("call" to "setSurfaceCallback", "cb" to "null"))
                giveSurface -> giveSurfaceTo(cb)
                else -> log("carhost_surface_wanted", emptyList())
            }
        }

        override fun sendLocation(location: Location?) = hostCall("sendLocation")
        override fun showAlert(alert: Bundleable?) = hostCall("showAlert")
        override fun dismissAlert(alertId: Int) = hostCall("dismissAlert")
        override fun openMicrophone(request: Bundleable?): Bundleable? {
            hostCall("openMicrophone")
            return null
        }
    }

    private val navigationHost = object : INavigationHost.Stub() {
        override fun navigationStarted() = post { log("carhost_nav_started", emptyList()) }
        override fun navigationEnded() = post { log("carhost_nav_ended", emptyList()) }
        override fun updateTrip(trip: Bundleable?) = post { if (!ended()) tripGot(trip) }
    }

    private val carHost = object : ICarHost.Stub() {
        override fun startCarApp(intent: Intent?) = post {
            log("carhost_start_car_app", listOfNotNull(intent?.action?.let { "action" to it }))
        }

        override fun getHost(type: String?): IBinder? = when (type) {
            HOST_APP -> {
                if (askedApp.compareAndSet(false, true)) post { log("carhost_host_asked", listOf("type" to HOST_APP)) }
                appHost.asBinder()
            }
            HOST_NAVIGATION -> {
                if (askedNavigation.compareAndSet(false, true)) {
                    post { log("carhost_host_asked", listOf("type" to HOST_NAVIGATION)) }
                }
                navigationHost.asBinder()
            }
            else -> {
                val t = AsciiText.encode(type, CarHostDigest.MAX_VALUE) ?: "null"
                post { log("carhost_host_asked", listOf("type" to t)) }
                null
            }
        }

        override fun finish() = post {
            log("carhost_finish", emptyList())
            feed(HostEvent.StopAsked)
        }
    }

    private fun hostCall(name: String) = post { log("carhost_app_host", listOf("call" to name)) }

    private companion object {
        const val ACTION_NAVIGATE = "androidx.car.app.action.NAVIGATE"
        const val HOST_APP = "app"
        const val HOST_NAVIGATION = "navigation"
        const val MANAGER_APP = "app"
        const val MANAGER_NAVIGATION = "navigation"
        const val TEMPLATE_DEBOUNCE_MS = 500L
        const val TRIP_LOG_EVERY_MS = 1000L
        const val SURFACE_W = 800
        const val SURFACE_H = 480
        const val SURFACE_DPI = 160
        const val TAG_VALUE = "tag_value"

        /** The binder inside a Bundleable that carries an IInterface (Bundler key `tag_value`). */
        fun binderOf(b: Bundleable?): IBinder? {
            if (b == null) return null
            val p = Parcel.obtain()
            return try {
                b.writeToParcel(p, 0)
                p.setDataPosition(0)
                p.readBundle(Bundleable::class.java.classLoader)?.getBinder(TAG_VALUE)
            } finally {
                p.recycle()
            }
        }
    }
}
