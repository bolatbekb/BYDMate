package com.bydmate.app.ui.home

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.media.MediaSessionListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the retro home screens show on the tape: the active media session, or nothing. */
data class NowPlaying(
    val artist: String? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    /**
     * The player's own position anchor: the position at [positionAtMs] (elapsedRealtime). It changes
     * only when the player reports a new one; [livePosition] runs it forward.
     */
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    /** Short-lived: true right after Prev/Next, so the reels spin fast like a tape winding. */
    val winding: Boolean = false,
    val source: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val art: Bitmap? = null,
    /** Human-readable name of the playing app (null: unknown). */
    val sourceLabel: String? = null,
    /** Custom-action ids of the session, for the Graphite shuffle / repeat buttons. */
    val shuffleAction: String? = null,
    val repeatAction: String? = null,
    /** PlaybackState speed (1 = normal), for running the position between polls. */
    val playbackSpeed: Float = 1f,
    /** Custom-action ids for «like» / «dislike» (null: the player has none). */
    val likeAction: String? = null,
    val dislikeAction: String? = null,
    /** Is the track liked (null: unknown). */
    val liked: Boolean? = null,
    /** Shuffle on (null: unknown). */
    val shuffleOn: Boolean? = null,
    /** Repeat mode: 0 off, 1 track, 2 list (null: unknown). */
    val repeatMode: Int? = null,
    /** elapsedRealtime at which [positionMs] was true (fork: the anchor of the running position). */
    val positionAtMs: Long = 0L,
) {
    /** 0..1 through the track; 0.5 when unknown, so the two tape packs look even. */
    val progress: Float
        get() {
            val pos = positionMs ?: return 0.5f
            val dur = durationMs?.takeIf { it > 0 } ?: return 0.5f
            return (pos.toFloat() / dur).coerceIn(0f, 1f)
        }

    val hasTrack: Boolean get() = !title.isNullOrBlank()
}

/** Transport commands the deck buttons send. */
interface MusicControls {
    fun playPause()
    fun play()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    /** Sends a session custom action (shuffle / repeat). */
    fun custom(action: String) {}
    /** Like / dislike the current track (Graphite only; the retro homes never call these). */
    fun like() {}
    fun dislike() {}
    /** Opens the app that is playing. */
    fun openSource() {}
}

/**
 * Polls the active media sessions once a second while a retro home screen is visible.
 * Reading sessions needs the notification-listener access BYDMate already grants itself on
 * DiLink (MediaSessionListenerService); without it nothing is shown. In DEMO mode a simulated
 * playlist plays instead, so the deck can be judged on an emulator.
 */
@Suppress("TooManyFunctions") // lifecycle + six transport commands
object NowPlayingSource : MusicControls {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private var job: Job? = null
    private var appContext: Context? = null
    private var controller: MediaController? = null
    // F3: the metadata (and the cover in it) is fetched once per controller; the callback pushes changes.
    private val metaGate = MetadataGate<TrackMeta>()
    private var callbackOn: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRun: Runnable = Runnable { refreshes.fired(); refresh() }
    private val refreshes: RefreshScheduler = RefreshScheduler(
        post = { handler.postDelayed(refreshRun, it) },
        cancel = { handler.removeCallbacks(refreshRun) },
    )
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metaGate.set(metadata?.let { readMeta(it) })
            refreshes.schedule(COALESCE_MS)
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) { refreshes.schedule(COALESCE_MS) }
        // A player may swap its session: wait before deciding there is no music.
        override fun onSessionDestroyed() { refreshes.pushBack(DESTROY_MS) }
    }
    private var windingUntil = 0L

    // Album art: only the current one is kept, scaled once per track.
    private var artKey: String? = null
    private var artBitmap: Bitmap? = null
    // Art published only as a URI: loaded once per track off the main thread; logged once per track.
    private val uriArt = UriArtState()
    private var loggedArtKey: String? = null
    private var scope: CoroutineScope? = null
    private val labelCache = HashMap<String, String?>()

    // DEMO playlist
    private val demoTracks = listOf(
        Triple("КИНО", "Группа крови", 287_000L),
        Triple("Сплин", "Выхода нет", 238_000L),
        Triple("Ляпис Трубецкой", "Воины света", 219_000L),
    )
    private val demoAlbums = listOf("Группа крови" to 1988, "Гранатовый альбом" to 1998, "Золотая коллекция" to 2005)
    private var demoIdx = 0
    private var demoPos = 134_000L // the anchor: the position at demoTickAt
    private var demoPlaying = true
    private var demoTickAt = SystemClock.elapsedRealtime()
    private var demoLiked = false
    private var demoShuffle = false
    private var demoRepeat = 0

    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        this.scope = scope
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(1000)
            }
        }
    }

    /** Stops the poll and lets go of the UI-scoped scope and controller: this object lives as long as the process. */
    fun stopPolling() {
        job?.cancel()
        job = null
        scope = null
        controller = null
        dropCallback()
        // The last cover (one bounded bitmap) stays, so the home shows a full first frame on return.
    }

    private fun dropCallback() {
        callbackOn?.let { runCatching { it.unregisterCallback(callback) } }
        callbackOn = null
        refreshes.clear()
        metaGate.reset()
    }

    /** Keeps one controller (and its callback) per session token; a new token re-reads the metadata once. */
    private fun sticky(c: MediaController): MediaController {
        val on = callbackOn
        if (on != null && on.sessionToken == c.sessionToken) return on
        dropCallback()
        c.registerCallback(callback, handler)
        callbackOn = c
        return c
    }

    private fun refresh() {
        val ctx = appContext ?: return
        val winding = SystemClock.elapsedRealtime() < windingUntil
        if (DemoMode.isOn(ctx)) { _state.value = demoNowPlaying(winding); return }
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val controllers = try {
            msm.getActiveSessions(ComponentName(ctx, MediaSessionListenerService::class.java))
        } catch (_: SecurityException) {
            controller = null
            dropCallback()
            _state.value = NowPlaying()
            return
        }
        // playbackState is a binder call: read once per controller
        val states = controllers.map { it to it.playbackState }
        val (picked, pb) = states.firstOrNull { it.second?.state == PlaybackState.STATE_PLAYING }
            ?: states.firstOrNull()
            ?: run { controller = null; dropCallback(); _state.value = NowPlaying(); return }
        val c = sticky(picked)
        controller = c
        val meta = metaGate.get(c.sessionToken) { c.metadata?.let { readMeta(it) } }
        val playing = pb?.state == PlaybackState.STATE_PLAYING
        // The player's own anchor, not extrapolated: it stays equal between changes, so nothing is republished.
        val pos = pb?.position?.takeIf { it >= 0 }
        val actions = pb?.customActions.orEmpty().map { it.toMediaAction() }
        val like = pickLike(actions)
        val (liked, shuffleOn, repeatMode) = stateOf(actions)
        _state.value = NowPlaying(
            artist = meta?.artist,
            title = meta?.title,
            isPlaying = playing,
            positionMs = pos,
            durationMs = meta?.durationMs,
            winding = winding,
            source = c.packageName,
            album = meta?.album,
            year = meta?.year,
            art = artResolve(meta, c.packageName),
            sourceLabel = labelFor(ctx, c.packageName),
            shuffleAction = pickShuffle(actions)?.action,
            repeatAction = pickRepeat(actions)?.action,
            playbackSpeed = pb?.playbackSpeed?.takeIf { it >= 0f } ?: 1f, // 0 = stalled: the position stays
            likeAction = like?.action,
            dislikeAction = pickDislike(actions)?.action,
            liked = liked,
            shuffleOn = shuffleOn,
            repeatMode = repeatMode,
            positionAtMs = pb?.lastPositionUpdateTime ?: 0L,
        )
    }

    private fun demoNowPlaying(winding: Boolean): NowPlaying {
        // The anchor moves only on a command or at the end of a track, so a poll republishes nothing.
        if (demoCurrentPos() >= demoTracks[demoIdx].third) {
            demoIdx = (demoIdx + 1) % demoTracks.size
            demoPos = 0
            demoTickAt = SystemClock.elapsedRealtime()
        }
        val t = demoTracks[demoIdx]
        return NowPlaying(
            t.first, t.second, demoPlaying, demoPos, t.third, winding, "Моя волна",
            positionAtMs = demoTickAt,
            album = demoAlbums[demoIdx].first, year = demoAlbums[demoIdx].second, sourceLabel = "Моя волна",
            likeAction = DEMO_LIKE, dislikeAction = DEMO_DISLIKE, liked = demoLiked,
            shuffleAction = DEMO_SHUFFLE, shuffleOn = demoShuffle,
            repeatAction = DEMO_REPEAT, repeatMode = demoRepeat,
        )
    }

    private fun demoCurrentPos(): Long =
        demoPos + if (demoPlaying) SystemClock.elapsedRealtime() - demoTickAt else 0L

    /** Folds the elapsed time into the anchor; call before a command changes the demo player. */
    private fun demoFold() {
        demoPos = demoCurrentPos()
        demoTickAt = SystemClock.elapsedRealtime()
    }

    private fun PlaybackState.CustomAction.toMediaAction() = MediaAction(
        action = action,
        name = name?.toString(),
        // Another app's extras can fail to unparcel (Android 12): unknown state, not a crash.
        active = runCatching { extras?.takeIf { it.containsKey("active") }?.getBoolean("active") }.getOrNull(),
        mode = runCatching { extras?.takeIf { it.containsKey("mode") }?.getInt("mode") }.getOrNull(),
    )

    /**
     * What the home needs from a [MediaMetadata], read once when it arrives; the metadata itself (and
     * the player's full-size cover in it) is dropped. [bitmap] is the cover scaled once; a cover the
     * track already has is not scaled again.
     */
    @Suppress("LongParameterList") // a plain holder of the fields the home reads
    internal class TrackMeta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val year: Int?,
        val pick: ArtPick,
        val bitmap: Bitmap?,
    ) {
        val key: String get() = "$title|$artist|$album"
    }

    internal fun readMeta(md: MediaMetadata): TrackMeta {
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val pick = pickArt(md)
        val have = "$title|$artist|$album" == artKey && artBitmap != null
        return TrackMeta(
            title, artist, album,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
            year = md.getLong(MediaMetadata.METADATA_KEY_YEAR).takeIf { it > 0 }?.toInt(),
            pick = pick,
            bitmap = if (have) null else pick.bitmapKey?.let { md.getBitmap(it) }?.let { scaleArt(it) },
        )
    }

    /** [readMeta] + [artResolve] for a metadata in hand and an explicit key. */
    internal fun artFor(md: MediaMetadata?, key: String, pkg: String = ""): Bitmap? {
        if (md == null) { artKey = null; artBitmap = null; return null }
        return artResolve(readMeta(md), pkg, key)
    }

    /**
     * Scaled once per «title|artist|album»; the first non-null cover for a key wins. Bitmaps first
     * (ALBUM_ART → ART → DISPLAY_ICON), then a URI (ALBUM_ART_URI → ART_URI → DISPLAY_ICON_URI),
     * loaded once per track in the background.
     */
    internal fun artResolve(meta: TrackMeta?, pkg: String = "", keyOverride: String? = null): Bitmap? {
        if (meta == null) { artKey = null; artBitmap = null; return null }
        val key = keyOverride ?: meta.key
        if (key != artKey) { artKey = key; artBitmap = null }
        if (artBitmap == null) {
            val pick = meta.pick
            val uri = pick.uri
            when {
                pick.bitmapKey != null -> {
                    artBitmap = meta.bitmap
                    logArt(key, pkg, pick, "bitmap")
                }
                uri != null -> when (uriArt.request(key)) {
                    UriArtState.Step.START -> loadUriArt(key, pkg, pick, uri)
                    UriArtState.Step.READY -> artBitmap = uriArt.bitmap(key)
                    UriArtState.Step.PENDING, UriArtState.Step.FAILED -> Unit
                }
                else -> logArt(key, pkg, pick, "none")
            }
        }
        return artBitmap
    }

    /** Loads [uri] on IO; the cover shows when the track is still the same. */
    private fun loadUriArt(key: String, pkg: String, pick: ArtPick, uri: String) {
        val ctx = appContext ?: return
        val s = scope ?: return
        s.launch {
            // Cancelled with the screen before it finished: let the next poll start it again.
            val r = try {
                withContext(Dispatchers.IO) { loadArtUri(ctx, uri) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                uriArt.forget(key)
                throw e
            }
            uriArt.done(key, r.bitmap)
            logArt(key, pkg, pick, r.reason)
            if (key == artKey && r.bitmap != null) {
                artBitmap = r.bitmap
                _state.value = _state.value.copy(art = r.bitmap)
            }
        }
    }

    /** Once per track: where its cover came from (URIs by scheme only, never the address). */
    private fun logArt(key: String, pkg: String, pick: ArtPick, outcome: String) {
        if (key == loggedArtKey) return
        loggedArtKey = key
        Log.d(TAG, artLogLine(pkg, pick, outcome))
    }

    private const val TAG = "NowPlaying"
    private const val COALESCE_MS = 100L
    private const val DESTROY_MS = 500L
    private const val DEMO_LIKE = "like"
    private const val DEMO_DISLIKE = "dislike"
    private const val DEMO_SHUFFLE = "shuffle"
    private const val DEMO_REPEAT = "repeat"

    private fun labelFor(ctx: Context, pkg: String): String? {
        if (labelCache.containsKey(pkg)) return labelCache[pkg]
        val label = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
        labelCache[pkg] = label
        return label
    }

    private fun demo(): Boolean = appContext?.let { DemoMode.isOn(it) } == true

    private fun wind() {
        windingUntil = SystemClock.elapsedRealtime() + 700
        refresh()
    }

    override fun playPause() = if (_state.value.isPlaying) pause() else play()

    override fun play() {
        if (demo()) { demoFold(); demoPlaying = true } else controller?.transportControls?.play()
        refresh()
    }

    override fun pause() {
        if (demo()) { demoFold(); demoPlaying = false } else controller?.transportControls?.pause()
        refresh()
    }

    override fun stop() {
        if (demo()) { demoPlaying = false; demoPos = 0; demoTickAt = SystemClock.elapsedRealtime() } else controller?.transportControls?.stop()
        refresh()
    }

    override fun custom(action: String) {
        if (demo()) {
            when (action) {
                DEMO_LIKE -> demoLiked = !demoLiked
                DEMO_SHUFFLE -> demoShuffle = !demoShuffle
                DEMO_REPEAT -> demoRepeat = when (demoRepeat) { 0 -> 2; 2 -> 1; else -> 0 }
            }
        } else {
            controller?.transportControls?.sendCustomAction(action, null)
        }
        refresh()
    }

    override fun like() { _state.value.likeAction?.let { custom(it) } }

    override fun dislike() {
        val act = _state.value.dislikeAction ?: return
        if (demo()) { demoLiked = false; next() } else { custom(act); wind() }
    }

    override fun openSource() {
        val ctx = appContext ?: return
        if (demo()) return
        val c = controller ?: return
        val session = c.sessionActivity
        // Android 14+ would need ActivityOptions.setPendingIntentBackgroundActivityStartMode for send(); DiLink 12 (API 31) does not.
        var plan = openPlan(session != null, session == null && hasLaunch(ctx, c.packageName))
        if (plan == OpenPlan.SESSION) {
            try {
                session?.send()
                return
            } catch (_: PendingIntent.CanceledException) {
                Log.d(TAG, "open: session activity cancelled, launching the app")
                plan = OpenPlan.LAUNCH
            }
        }
        if (plan == OpenPlan.LAUNCH) startLaunch(ctx, c.packageName)
    }

    private fun launchIntentOf(ctx: Context, pkg: String): Intent? =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()

    private fun hasLaunch(ctx: Context, pkg: String) = launchIntentOf(ctx, pkg) != null

    private fun startLaunch(ctx: Context, pkg: String) {
        val intent = launchIntentOf(ctx, pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Log.d(TAG, "open: no activity for $pkg")
        } catch (_: SecurityException) {
            Log.d(TAG, "open: launch of $pkg refused")
        }
    }

    override fun next() {
        if (demo()) { demoIdx = (demoIdx + 1) % demoTracks.size; demoPos = 0; demoTickAt = SystemClock.elapsedRealtime(); demoPlaying = true }
        else controller?.transportControls?.skipToNext()
        wind()
    }

    override fun previous() {
        if (demo()) { demoIdx = (demoIdx + demoTracks.size - 1) % demoTracks.size; demoPos = 0; demoTickAt = SystemClock.elapsedRealtime(); demoPlaying = true }
        else controller?.transportControls?.skipToPrevious()
        wind()
    }
}

/** Downscales [src] so its longer side is at most [max], keeping the aspect; never upscales (same instance). */
internal fun scaleArt(src: Bitmap, max: Int = 264): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= max) return src
    val k = max.toFloat() / longest
    return Bitmap.createScaledBitmap(
        src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true,
    )
}
