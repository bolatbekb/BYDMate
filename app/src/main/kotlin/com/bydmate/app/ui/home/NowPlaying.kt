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
    private var demoPos = 134_000L
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

    /** Stops the poll and lets go of everything UI-scoped: this object lives as long as the process. */
    fun stopPolling() {
        job?.cancel()
        job = null
        scope = null
        controller = null
        artKey = null
        artBitmap = null
        _state.value = _state.value.copy(art = null)
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
            _state.value = NowPlaying()
            return
        }
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        controller = c
        if (c == null) { _state.value = NowPlaying(); return }
        val md = c.metadata
        val pb = c.playbackState
        val now = SystemClock.elapsedRealtime()
        val playing = pb?.state == PlaybackState.STATE_PLAYING
        val pos = pb?.let { st ->
            if (st.position < 0) null
            else st.position + if (playing) ((now - st.lastPositionUpdateTime) * st.playbackSpeed).toLong() else 0L
        }
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val actions = pb?.customActions.orEmpty().map { it.toMediaAction() }
        val like = pickLike(actions)
        val (liked, shuffleOn, repeatMode) = stateOf(actions)
        _state.value = NowPlaying(
            artist = artist,
            title = title,
            isPlaying = playing,
            positionMs = pos,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            winding = winding,
            source = c.packageName,
            album = album,
            year = md?.getLong(MediaMetadata.METADATA_KEY_YEAR)?.takeIf { it > 0 }?.toInt(),
            art = artFor(md, "$title|$artist|$album", c.packageName),
            sourceLabel = labelFor(ctx, c.packageName),
            shuffleAction = pickShuffle(actions)?.action,
            repeatAction = pickRepeat(actions)?.action,
            playbackSpeed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            likeAction = like?.action,
            dislikeAction = pickDislike(actions)?.action,
            liked = liked,
            shuffleOn = shuffleOn,
            repeatMode = repeatMode,
        )
    }

    private fun demoNowPlaying(winding: Boolean): NowPlaying {
        val now = SystemClock.elapsedRealtime()
        if (demoPlaying) demoPos += now - demoTickAt
        demoTickAt = now
        val (artist, title, dur) = demoTracks[demoIdx]
        if (demoPos >= dur) { demoIdx = (demoIdx + 1) % demoTracks.size; demoPos = 0 }
        val t = demoTracks[demoIdx]
        return NowPlaying(
            t.first, t.second, demoPlaying, demoPos, t.third, winding, "Моя волна",
            album = demoAlbums[demoIdx].first, year = demoAlbums[demoIdx].second, sourceLabel = "Моя волна",
            likeAction = DEMO_LIKE, dislikeAction = DEMO_DISLIKE, liked = demoLiked,
            shuffleAction = DEMO_SHUFFLE, shuffleOn = demoShuffle,
            repeatAction = DEMO_REPEAT, repeatMode = demoRepeat,
        )
    }

    private fun PlaybackState.CustomAction.toMediaAction() = MediaAction(
        action = action,
        name = name?.toString(),
        active = extras?.takeIf { it.containsKey("active") }?.getBoolean("active"),
        mode = extras?.takeIf { it.containsKey("mode") }?.getInt("mode"),
    )

    /**
     * Scaled once per «title|artist|album»; the first non-null cover for a key wins (each poll
     * unparcels a new Bitmap). Bitmaps first (ALBUM_ART → ART → DISPLAY_ICON), then a URI
     * (ALBUM_ART_URI → ART_URI → DISPLAY_ICON_URI), loaded once per track in the background.
     */
    internal fun artFor(md: MediaMetadata?, key: String, pkg: String = ""): Bitmap? {
        if (md == null) { artKey = null; artBitmap = null; return null }
        if (key != artKey) { artKey = key; artBitmap = null }
        if (artBitmap == null) {
            val pick = pickArt(md)
            val bitmapKey = pick.bitmapKey
            val uri = pick.uri
            when {
                bitmapKey != null -> {
                    artBitmap = md.getBitmap(bitmapKey)?.let { scaleArt(it) }
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
        if (demo()) demoPlaying = true else controller?.transportControls?.play()
        refresh()
    }

    override fun pause() {
        if (demo()) demoPlaying = false else controller?.transportControls?.pause()
        refresh()
    }

    override fun stop() {
        if (demo()) { demoPlaying = false; demoPos = 0 } else controller?.transportControls?.stop()
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
        if (demo()) { demoIdx = (demoIdx + 1) % demoTracks.size; demoPos = 0; demoPlaying = true }
        else controller?.transportControls?.skipToNext()
        wind()
    }

    override fun previous() {
        if (demo()) { demoIdx = (demoIdx + demoTracks.size - 1) % demoTracks.size; demoPos = 0; demoPlaying = true }
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
