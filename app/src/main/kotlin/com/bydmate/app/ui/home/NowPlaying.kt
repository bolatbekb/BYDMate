package com.bydmate.app.ui.home

import android.content.ComponentName
import android.content.Context
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

    // DEMO playlist
    private val demoTracks = listOf(
        Triple("КИНО", "Группа крови", 287_000L),
        Triple("Сплин", "Выхода нет", 238_000L),
        Triple("Ляпис Трубецкой", "Воины света", 219_000L),
    )
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
            _state.value = NowPlaying(t.first, t.second, demoPlaying, demoPos, t.third, winding, "Моя волна")
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
        _state.value = NowPlaying(
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
            isPlaying = playing,
            positionMs = pos,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            winding = winding,
            source = c.packageName,
        )
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
