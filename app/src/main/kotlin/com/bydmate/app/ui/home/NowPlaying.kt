package com.bydmate.app.ui.home

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.media.MediaSessionListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(1000)
            }
        }
    }

    fun stopPolling() {
        job?.cancel()
        job = null
    }

    private fun refresh() {
        val ctx = appContext ?: return
        val winding = SystemClock.elapsedRealtime() < windingUntil
        if (DemoMode.isOn(ctx)) {
            val now = SystemClock.elapsedRealtime()
            if (demoPlaying) demoPos += now - demoTickAt
            demoTickAt = now
            val (artist, title, dur) = demoTracks[demoIdx]
            if (demoPos >= dur) { demoIdx = (demoIdx + 1) % demoTracks.size; demoPos = 0 }
            val t = demoTracks[demoIdx]
            _state.value = NowPlaying(
                t.first, t.second, demoPlaying, demoPos, t.third, winding, "Моя волна",
                album = demoAlbums[demoIdx].first, year = demoAlbums[demoIdx].second, sourceLabel = "Моя волна",
            )
            return
        }
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
        val actions = pb?.customActions.orEmpty()
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
            art = artFor(md, "$title|$artist|$album"),
            sourceLabel = labelFor(ctx, c.packageName),
            shuffleAction = findAction(actions, "shuffle"),
            repeatAction = findAction(actions, "repeat"),
            playbackSpeed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }

    private fun findAction(actions: List<PlaybackState.CustomAction>, word: String): String? =
        actions.firstOrNull {
            it.action.contains(word, ignoreCase = true) || it.name?.toString()?.contains(word, ignoreCase = true) == true
        }?.action

    /** Scaled once per «title|artist|album»; the first non-null cover for a key wins (each poll unparcels a new Bitmap). */
    internal fun artFor(md: MediaMetadata?, key: String): Bitmap? {
        if (md == null) { artKey = null; artBitmap = null; return null }
        if (key != artKey) { artKey = key; artBitmap = null }
        if (artBitmap == null) {
            val src = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            artBitmap = src?.let { scaleArt(it) }
        }
        return artBitmap
    }

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
        if (!demo()) controller?.transportControls?.sendCustomAction(action, null)
        refresh()
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
