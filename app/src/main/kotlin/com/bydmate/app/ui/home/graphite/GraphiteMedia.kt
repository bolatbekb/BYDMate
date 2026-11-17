package com.bydmate.app.ui.home.graphite

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.bydmate.app.ui.home.common.px
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cover side (mockup px) in the regular and the compact card (compact: the PM2.5 line shortens the top cards). */
internal fun coverSide(compact: Boolean): Float = if (compact) 192f else 230f

/** «Медиа» card: source pill, cover and title, progress, transport row and media volume. */
@Composable
fun MediaCard(music: NowPlaying, controls: MusicControls, compact: Boolean, modifier: Modifier) {
    val cover = coverSide(compact)
    val volume = rememberVolume()
    CardBody(modifier) {
        val label = music.sourceLabel
        MediaHeader(music, controls, label ?: stringResource(R.string.home_gr_media_title), if (compact) 14f else 22f)
        // The content lambda reads the live music through a State, so it updates even if AnimatedContent
        // keeps the lambda it first composed an entry with.
        val live by rememberUpdatedState(music)
        val slide = with(LocalDensity.current) { px(12).roundToPx() }
        // A new track cross-fades the cover and the text, the new one rising 12 px; the same
        // track (play / pause, position) updates in place.
        Box(Modifier.fillMaxWidth().height(px(cover))) {
            // The glow breathes in its own layer behind the cover (unclipped, so the blur shows).
            Box(Modifier.size(px(cover)).graphicsLayer().coverGlow(music.isPlaying && music.hasTrack, cover))
            // The target is the track's face, not the per-second NowPlaying: Compose keeps an entry per
            // target, and a new object every poll made them pile up. Only what changes with the track is
            // in the face (the outgoing track keeps its own text and cover while it fades); the live
            // state (playing) is read from the current music.
            AnimatedContent(
                targetState = music.face(),
                contentKey = { it.key },
                transitionSpec = {
                    (fadeIn(tween(TRACK_MS)) + slideInVertically(tween(TRACK_MS)) { slide }) togetherWith fadeOut(tween(TRACK_MS)) using
                        SizeTransform(clip = false)
                },
                modifier = Modifier.fillMaxSize(),
                label = "track",
            ) { face ->
                val open = Modifier.openSource(face.hasTrack) { controls.openSource() }
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(px(30))) {
                    Cover(face.art, cover, open)
                    Meta(face, live.isPlaying, Modifier.weight(1f).fillMaxHeight().padding(bottom = px(6)).then(open))
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

/** What the cross-fade shows of a track; equal faces are the same target, so a poll adds no entry. */
private data class TrackFace(
    val title: String?, val artist: String?, val album: String?, val year: Int?, val art: Bitmap?,
) {
    val hasTrack: Boolean get() = !title.isNullOrBlank()

    /** What makes a track a new one for the cross-fade (the art arriving later updates in place). */
    val key: Triple<String?, String?, String?> get() = Triple(title, artist, album)
}

private fun NowPlaying.face() = TrackFace(title, artist, album, year, art)

/**
 * A soft glow behind the cover (its 24-radius square, blur 28) whose alpha breathes 0.10 ↔ 0.28
 * every 4 s while [on]; off, it fades out in 400 ms and the infinite transition is gone.
 * Electric, or ProgressEnd on Soft, where Electric is mint.
 */
@Composable
private fun Modifier.coverGlow(on: Boolean, coverSide: Float): Modifier {
    val p = LocalGr.current
    val color = if (p == GrSoft) p.progressEnd else p.highlight
    val fade by animateFloatAsState(if (on) 1f else 0f, tween(GLOW_FADE_MS), label = "glowFade")
    val breath: State<Float>? = if (on) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            GLOW_MIN, GLOW_MAX,
            infiniteRepeatable(tween(GLOW_HALF_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else null
    val last = remember { floatArrayOf(GLOW_MIN) } // the breath to fade out from
    return this.drawWithCache {
        val u = px(1).toPx()
        val side = px(coverSide).toPx()
        val r = 24 * u
        val paint = blurPaint(color, 28 * u)
        onDrawBehind {
            val b = breath?.value?.also { last[0] = it } ?: last[0]
            val a = b * fade
            if (a > 0.001f) {
                paint.color = color.copy(alpha = a).toArgb()
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(0f, 0f, side, side, r, r, paint) }
            }
        }
    }
}

private const val GLOW_MIN = 0.10f
private const val GLOW_MAX = 0.28f
private const val GLOW_HALF_MS = 2_000
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
private fun Meta(music: TrackFace, isPlaying: Boolean, modifier: Modifier) {
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
            Equalizer(isPlaying, p.highlight)
        }
        Spacer(Modifier.height(px(12)))
        FitText(music.title.orEmpty(), grStyle(50f, 500, -1f, p.textPrimary, tnum = false), 50f, 36f, Modifier, lineBox = true)
        if (!music.artist.isNullOrBlank()) {
            Spacer(Modifier.height(px(12)))
            FitText(music.artist, grStyle(30f, 400, color = p.textSecondary, tnum = false), 30f, 22f, Modifier, lineBox = true)
        }
        val sub = listOfNotNull(music.album?.takeIf { it.isNotBlank() }, music.year?.toString()).joinToString(" · ")
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
private fun Equalizer(playing: Boolean, color: Color) {
    val clock = remember { mutableFloatStateOf(0f) }
    val live = remember { Animatable(if (playing) 1f else 0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(playing, owner) {
        if (playing) {
            launch { live.animateTo(1f, tween(EQ_EASE_MS)) }
            // Ticks only while the screen is started (not under navigation).
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val start = SystemClock.elapsedRealtime() - (clock.floatValue * 1000).toLong()
                while (true) {
                    clock.floatValue = (SystemClock.elapsedRealtime() - start) / 1000f
                    delay(EQ_TICK_MS)
                }
            }
        } else {
            live.animateTo(0f, tween(EQ_EASE_MS))
        }
    }
    Canvas(Modifier.size(px(4 * EQ_BAR + 3 * EQ_GAP), px(EQ_H)).graphicsLayer()) {
        val u = px(1).toPx()
        val t = clock.floatValue
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
private const val EQ_TICK_MS = 83L
private const val EQ_EASE_MS = 300

/** The anchor of the shown position: the poll it runs from, on the elapsedRealtime clock. */
private class PositionClock {
    var anchor = PolledPosition(0L, 0L, null, playing = false)
}

/**
 * The position to show: the last poll, run forward in real time every 250 ms while playing with a
 * known duration. A poll re-anchors it when play state, track length or speed changed, or when it
 * is a seek ([reanchors]); a poll within the tolerance leaves the running clock alone (no jitter).
 */
@Composable
private fun rememberShownPosition(music: NowPlaying): State<Long?> {
    val clock = remember { PositionClock() }
    val shown = remember { mutableStateOf(music.positionMs) }
    val pos = music.positionMs
    val dur = music.durationMs
    val playing = music.isPlaying
    val speed = music.playbackSpeed
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(pos, playing, dur, speed, owner) {
        if (pos == null) {
            shown.value = null
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        val poll = PolledPosition(pos, now, dur, playing, speed)
        if (shown.value == null || reanchors(clock.anchor, poll, now)) clock.anchor = poll
        shown.value = extrapolatePosition(clock.anchor, now)
        if (!playing || dur == null) return@LaunchedEffect
        // Runs only while the screen is started; it picks up the real time again on return.
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shown.value = extrapolatePosition(clock.anchor, SystemClock.elapsedRealtime())
                delay(POSITION_TICK_MS)
            }
        }
    }
    return shown
}

private const val POSITION_TICK_MS = 250L

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
