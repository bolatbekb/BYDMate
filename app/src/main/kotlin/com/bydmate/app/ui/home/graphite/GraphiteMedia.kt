package com.bydmate.app.ui.home.graphite

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.livePosition
import com.bydmate.app.ui.home.common.px
import java.util.Locale
import kotlinx.coroutines.delay

/** Cover side (mockup px) in the regular and the compact card (compact: the PM2.5 line shortens the top cards). */
internal fun coverSide(compact: Boolean): Float = if (compact) 192f else 230f

/** «Медиа» card: source pill, cover and title, progress, transport row and media volume. */
@Composable
fun MediaCard(musicState: State<NowPlaying>, controls: MusicControls, compact: Boolean, modifier: Modifier) {
    val music = musicState.value
    val cover = coverSide(compact)
    val volume = rememberVolume()
    CardBody(modifier) {
        val label = music.sourceLabel
        CardHeader(
            R.drawable.gr_ic_note, label ?: stringResource(R.string.home_gr_media_title),
            modifier = Modifier.openSource(music.hasTrack) { controls.openSource() },
        )
        Spacer(Modifier.height(px(if (compact) 14f else 22f)))
        // The content lambda reads the live music through a State, so it updates even if AnimatedContent
        // keeps the lambda it first composed an entry with.
        val live by rememberUpdatedState(music)
        // one ~12-fps clock for the glow and the equalizer, so they change in the same frame
        val clock = rememberStepClock(music.isPlaying && music.hasTrack)
        val slide = with(LocalDensity.current) { px(12).roundToPx() }
        // A new track cross-fades the cover and the text, the new one rising 12 px; the same
        // track (play / pause, position) updates in place.
        Box(Modifier.fillMaxWidth().height(px(cover))) {
            // The glow breathes in its own layer behind the cover (unclipped, so the blur shows).
            Box(Modifier.size(px(cover)).graphicsLayer().coverGlow(music.isPlaying && music.hasTrack, cover, clock))
            // The target is the track's identity (title / artist / album), not the per-second NowPlaying:
            // Compose keeps an entry per target, so anything that changes within one track (a late cover
            // or year) must stay out of it. Those are read live per child below.
            AnimatedContent(
                targetState = music.face(),
                contentKey = { it },
                transitionSpec = {
                    (fadeIn(tween(TRACK_MS)) + slideInVertically(tween(TRACK_MS)) { slide }) togetherWith fadeOut(tween(TRACK_MS)) using
                        SizeTransform(clip = false)
                },
                modifier = Modifier.fillMaxSize(),
                label = "track",
            ) { face ->
                val open = Modifier.openSource(face.hasTrack) { controls.openSource() }
                // The current track shows the live cover and year (a late one updates in place, no
                // cross-fade); an outgoing track keeps the last ones it showed while it fades.
                val held = remember { HeldMedia() }
                if (live.face() == face) {
                    held.art = live.art
                    held.year = live.year
                }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(px(30))) {
                    Cover(held.art, cover, open)
                    Meta(face, held.year, live.isPlaying, clock, Modifier.weight(1f).fillMaxHeight().padding(bottom = px(6)).then(open))
                }
            }
        }
        Spacer(Modifier.height(px(if (compact) 18 else 24)))
        Progress(music)
        Spacer(Modifier.height(px(6)))
        Controls(music, controls, compact)
        Spacer(Modifier.weight(1f).heightIn(min = px(14)))
        MediaVolume(volume, compact)
    }
}

private const val TRACK_MS = 400

/** What makes a track a new one for the cross-fade; equal values are the same target, so a poll adds no entry. */
private data class TrackFace(val title: String?, val artist: String?, val album: String?) {
    val hasTrack: Boolean get() = !title.isNullOrBlank()
}

private fun NowPlaying.face() = TrackFace(title, artist, album)

/** The cover and year a track's child last showed (plain fields: read in the same composition that sets them). */
private class HeldMedia {
    var art: Bitmap? = null
    var year: Int? = null
}

/**
 * A soft glow behind the cover (its 24-radius square, blur 28) whose alpha breathes 0.10 ↔ 0.28
 * every 4 s while [on]; off, it fades out in 400 ms and the infinite transition is gone.
 * Electric, or ProgressEnd on Soft, where Electric is mint.
 */
@Composable
private fun Modifier.coverGlow(on: Boolean, coverSide: Float, clock: State<Float>): Modifier {
    val p = LocalGr.current
    val color = if (p == GrSoft) p.progressEnd else p.highlight
    val fade by animateFloatAsState(if (on) 1f else 0f, tween(GLOW_FADE_MS), label = "glowFade")
    val last = remember { floatArrayOf(GLOW_MIN) } // the breath to fade out from
    return this.drawWithCache {
        val u = px(1).toPx()
        val side = px(coverSide).toPx()
        val r = 24 * u
        val paint = blurPaint(color, 28 * u)
        onDrawBehind {
            // the breath follows the shared 12-fps clock; off, it fades from where it stopped
            val b = if (on) glowBreath(clock.value).also { last[0] = it } else last[0]
            val a = b * fade
            if (a > 0.001f) {
                paint.color = color.copy(alpha = a).toArgb()
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, side, side, r, r, paint) }
            }
        }
    }
}

private const val GLOW_FADE_MS = 400

@Composable
private fun Cover(art: Bitmap?, side: Float, open: Modifier) {
    val p = LocalGr.current
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(px(side)).then(open).clip(RoundedCornerShape(px(24)))
                .border(px(1), Color.White.copy(alpha = 0.08f), RoundedCornerShape(px(24))),
        )
    } else {
        InsetBox(24f, Modifier.size(px(side)).then(open)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GrIcon(R.drawable.gr_ic_note, 72f, p.textTertiary)
            }
        }
    }
}

@Composable
private fun Meta(music: TrackFace, year: Int?, isPlaying: Boolean, clock: State<Float>, modifier: Modifier) {
    val p = LocalGr.current
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        if (!music.hasTrack) {
            FitText(stringResource(R.string.home_gr_no_music), grStyle(50f, 500, -1f, p.textSecondary, tnum = false), 50f, 36f, Modifier, lineBox = true)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (isPlaying) R.string.home_gr_now_playing else R.string.home_gr_paused).uppercase(Locale.getDefault()),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = grStyle(19f, 400, 2f, p.textTertiary, tnum = false),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(px(10)))
            Equalizer(isPlaying, p.highlight, clock)
        }
        Spacer(Modifier.height(px(12)))
        FitText(music.title.orEmpty(), grStyle(50f, 500, -1f, p.textPrimary, tnum = false), 50f, 36f, Modifier, lineBox = true)
        if (!music.artist.isNullOrBlank()) {
            Spacer(Modifier.height(px(12)))
            FitText(music.artist, grStyle(30f, 400, color = p.textSecondary, tnum = false), 30f, 22f, Modifier, lineBox = true)
        }
        val sub = listOfNotNull(music.album?.takeIf { it.isNotBlank() }, year?.toString()).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(px(8)))
            FitText(sub, grStyle(22f, 400, color = p.textTertiary, tnum = false), 22f, 17f, Modifier, lineBox = true)
        }
    }
}

/**
 * Equalizer after «Сейчас играет»: 4 bars 4 × 14, 3 apart, radius 2. Playing, each follows its
 * own curve ([eqLevel]) at ~12 fps through a float state read only while drawing; paused, they
 * ease to 0.2 in 300 ms and the loop ends.
 */
@Composable
private fun Equalizer(playing: Boolean, color: Color, clock: State<Float>) {
    val live = remember { Animatable(if (playing) 1f else 0f) }
    LaunchedEffect(playing) {
        live.animateTo(if (playing) 1f else 0f, tween(EQ_EASE_MS))
    }
    Canvas(Modifier.size(px(4 * EQ_BAR + 3 * EQ_GAP), px(EQ_H)).graphicsLayer()) {
        val u = px(1).toPx()
        val t = clock.value
        val k = live.value
        val r = CornerRadius(2 * u)
        for (i in 0 until 4) {
            val f = EQ_REST + (eqLevel(i, t) - EQ_REST) * k
            val h = size.height * f
            drawRoundRect(color, Offset(i * (EQ_BAR + EQ_GAP) * u, size.height - h), Size(EQ_BAR * u, h), r)
        }
    }
}

private const val EQ_BAR = 4f
private const val EQ_GAP = 3f
private const val EQ_H = 14f
private const val EQ_REST = 0.2f
private const val EQ_EASE_MS = 300

/**
 * The position to show, run from the anchor the source published ([livePosition]): once on every
 * new anchor, then once a second while playing. The anchor does not move
 * between polls, so there is nothing to smooth.
 */
@Composable
private fun rememberShownPosition(music: NowPlaying): State<Long?> {
    val shown = remember { mutableStateOf(music.livePosition(SystemClock.elapsedRealtime())) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(music.positionMs, music.positionAtMs, music.isPlaying, music.durationMs, music.playbackSpeed, owner) {
        shown.value = music.livePosition(SystemClock.elapsedRealtime())
        if (!music.isPlaying || music.positionMs == null) return@LaunchedEffect
        // Runs only while the screen is started; it picks up the real time again on return.
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shown.value = music.livePosition(SystemClock.elapsedRealtime())
                delay(POSITION_TICK_MS)
            }
        }
    }
    return shown
}

private const val POSITION_TICK_MS = 1_000L // fork: the bar moves about 1 px/s, so a 250-ms tick only woke the frame clock

@Composable
private fun Progress(music: NowPlaying) {
    val p = LocalGr.current
    val shown = rememberShownPosition(music)
    val dur = music.durationMs
    // The label changes once a second; the bar reads the position only while drawing.
    val elapsed by remember(shown) { derivedStateOf { grTrackTime(shown.value) } }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(px(20)).graphicsLayer().drawBehind {
                val fraction = mediaFraction(shown.value, dur)
                val h = px(6).toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(Color.White.copy(alpha = 0.08f), Offset(0f, top), Size(size.width, h), r)
                val w = size.width * fraction
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(p.progressStart, p.progressEnd), 0f, size.width),
                        Offset(0f, top), Size(w, h), r,
                    )
                }
                val cx = (w).coerceIn(px(10).toPx(), size.width - px(10).toPx())
                drawCircle(Color.White.copy(alpha = 0.12f), px(15).toPx(), Offset(cx, size.height / 2))
                drawCircle(Color.White, px(10).toPx(), Offset(cx, size.height / 2))
            },
        )
        Spacer(Modifier.height(px(12)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val st = grStyle(19f, 400, color = p.textTertiary)
            Text(elapsed, maxLines = 1, softWrap = false, style = st)
            Text(grTrackTime(music.durationMs), maxLines = 1, softWrap = false, style = st)
        }
    }
}

/** 0..1 through the track; 0 when position or duration is unknown (the bar stays empty). */
internal fun mediaFraction(positionMs: Long?, durationMs: Long?): Float {
    if (positionMs == null || durationMs == null || durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
