package com.bydmate.app.ui.home.graphite

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.common.px
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Side = 264f

/** «Медиа» card: source pill, cover and title, progress, transport row and media volume. */
@Composable
fun MediaCard(music: NowPlaying, controls: MusicControls, modifier: Modifier) {
    val p = LocalGr.current
    val volume = rememberVolume()
    val ctx = LocalContext.current
    CardBody(modifier) {
        val label = music.sourceLabel
        CardHeader(R.drawable.gr_ic_note, label ?: "Медиа")
        Spacer(Modifier.height(px(22)))
        val slide = with(LocalDensity.current) { px(12).roundToPx() }
        // A new track cross-fades the cover and the text, the new one rising 12 px; the same
        // track (play / pause, position) updates in place.
        Box(Modifier.fillMaxWidth().height(px(Side))) {
            // The glow breathes in its own layer behind the cover (unclipped, so the blur shows).
            Box(Modifier.size(px(Side)).graphicsLayer().coverGlow(music.isPlaying && music.hasTrack))
            AnimatedContent(
                targetState = music,
                contentKey = { it.trackKey() },
                transitionSpec = {
                    (fadeIn(tween(TRACK_MS)) + slideInVertically(tween(TRACK_MS)) { slide }) togetherWith fadeOut(tween(TRACK_MS)) using
                        SizeTransform(clip = false)
                },
                modifier = Modifier.fillMaxSize(),
                label = "track",
            ) { m ->
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(px(30))) {
                    Cover(m)
                    Meta(m, Modifier.weight(1f).fillMaxHeight().padding(bottom = px(6)))
                }
            }
        }
        Spacer(Modifier.height(px(28)))
        Progress(music)
        Spacer(Modifier.height(px(6)))
        Controls(music, controls)
        Spacer(Modifier.weight(1f).heightIn(min = px(14)))
        // The whole sunken block takes the touch, not just the 28 px bar.
        InsetBox(26f, Modifier.fillMaxWidth().volumeInput(volume) { v -> GraphiteVolume.set(ctx, v) }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = px(VolPad), vertical = px(18)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(px(VolGap)),
            ) {
                GrIcon(R.drawable.gr_ic_volume, VolIcon, p.textSecondary)
                VolumeBar(volume.value, Modifier.weight(1f))
                Text(
                    volume.value.toString(), maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                    style = grStyle(26f, 500, color = p.textPrimary), modifier = Modifier.width(px(VolNumber)),
                )
            }
        }
    }
}

private const val TRACK_MS = 400

/** What makes a track a new one for the cross-fade. */
private fun NowPlaying.trackKey(): Triple<String?, String?, String?> = Triple(title, artist, album)

/**
 * A soft glow behind the cover (its 24-radius square, blur 28) whose alpha breathes 0.10 ↔ 0.28
 * every 4 s while [on]; off, it fades out in 400 ms and the infinite transition is gone.
 * Electric, or ProgressEnd on Soft, where Electric is mint.
 */
@Composable
private fun Modifier.coverGlow(on: Boolean): Modifier {
    val p = LocalGr.current
    val color = if (p == GrSoft) p.progressEnd else p.electric
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
        val side = px(Side).toPx()
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
private fun Cover(music: NowPlaying) {
    val p = LocalGr.current
    val art = music.art
    if (art != null) {
        val image = remember(art) { art.asImageBitmap() }
        Image(
            image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(px(Side)).clip(RoundedCornerShape(px(24)))
                .border(px(1), Color.White.copy(alpha = 0.08f), RoundedCornerShape(px(24))),
        )
    } else {
        InsetBox(24f, Modifier.size(px(Side))) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GrIcon(R.drawable.gr_ic_note, 72f, p.textTertiary)
            }
        }
    }
}

@Composable
private fun Meta(music: NowPlaying, modifier: Modifier) {
    val p = LocalGr.current
    Column(modifier, verticalArrangement = Arrangement.Bottom) {
        if (!music.hasTrack) {
            FitText("Нет музыки", grStyle(50f, 500, -1f, p.textSecondary, tnum = false), 50f, 36f, Modifier)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (music.isPlaying) "Сейчас играет" else "Пауза").uppercase(Locale.getDefault()),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                style = grStyle(20f, 400, 2f, p.textTertiary, tnum = false),
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(px(10)))
            Equalizer(music.isPlaying, p.electric)
        }
        Spacer(Modifier.height(px(12)))
        FitText(music.title.orEmpty(), grStyle(50f, 500, -1f, p.textPrimary, tnum = false), 50f, 36f, Modifier)
        if (!music.artist.isNullOrBlank()) {
            Spacer(Modifier.height(px(12)))
            FitText(music.artist, grStyle(28f, 400, color = p.textSecondary, tnum = false), 28f, 22f, Modifier)
        }
        val sub = listOfNotNull(music.album?.takeIf { it.isNotBlank() }, music.year?.toString()).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(px(8)))
            FitText(sub, grStyle(21f, 400, color = p.textTertiary, tnum = false), 21f, 17f, Modifier)
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
    var polledMs = 0L
    var atMs = 0L
    var playing = false
    var durationMs: Long? = null
    var speed = 1f
}

/**
 * The position to show: the last poll, run forward in real time every 250 ms while playing with a
 * known duration. A poll re-anchors it when play state or track length changed, or when it is a
 * seek ([isSeek]); a poll within the tolerance leaves the running clock alone (no jitter).
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
        val running = extrapolatePosition(clock.polledMs, clock.atMs, now, dur, playing, clock.speed)
        if (!playing || clock.playing != playing || clock.durationMs != dur || clock.speed != speed ||
            shown.value == null || isSeek(pos, running)
        ) {
            clock.polledMs = pos
            clock.atMs = now
            clock.playing = playing
            clock.durationMs = dur
            clock.speed = speed
        }
        shown.value = extrapolatePosition(clock.polledMs, clock.atMs, now, dur, playing, speed)
        if (!playing || dur == null) return@LaunchedEffect
        // Runs only while the screen is started; it picks up the real time again on return.
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                shown.value = extrapolatePosition(clock.polledMs, clock.atMs, SystemClock.elapsedRealtime(), dur, true, speed)
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
            val st = grStyle(20f, 400, color = p.textTertiary)
            Text(elapsed, maxLines = 1, softWrap = false, style = st)
            Text(grTrackTime(music.durationMs), maxLines = 1, softWrap = false, style = st)
        }
    }
}

@Composable
private fun Controls(music: NowPlaying, controls: MusicControls) {
    val p = LocalGr.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = px(6)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val shuffle = music.shuffleAction
        val repeat = music.repeatAction
        CtrlButton(R.drawable.gr_ic_shuffle, 34f, p.textTertiary, shuffle != null) { shuffle?.let(controls::custom) }
        CtrlButton(R.drawable.gr_ic_prev, 44f, p.textPrimary, true) { controls.previous() }
        val playSource = remember { MutableInteractionSource() }
        Box(
            Modifier.size(px(112)).pressScale(playSource, true).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(p.playTop, p.playBottom)))
                .clickable(interactionSource = playSource, indication = null) { controls.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            // Play ⇄ pause: a 200 ms cross-fade, the icons turning and growing into place.
            AnimatedContent(
                targetState = music.isPlaying,
                transitionSpec = {
                    (fadeIn(tween(ICON_MS)) + scaleIn(tween(ICON_MS), initialScale = 0.8f)) togetherWith
                        (fadeOut(tween(ICON_MS)) + scaleOut(tween(ICON_MS), targetScale = 0.8f))
                },
                label = "playPause",
            ) { playing ->
                val turn by transition.animateFloat({ tween(ICON_MS) }, label = "turn") { state ->
                    when (state) {
                        EnterExitState.PreEnter -> -45f
                        EnterExitState.Visible -> 0f
                        EnterExitState.PostExit -> 45f
                    }
                }
                Box(Modifier.graphicsLayer { rotationZ = turn }) {
                    GrIcon(if (playing) R.drawable.gr_ic_pause else R.drawable.gr_ic_play, 50f, p.bg)
                }
            }
        }
        CtrlButton(R.drawable.gr_ic_next, 44f, p.textPrimary, true) { controls.next() }
        CtrlButton(R.drawable.gr_ic_repeat, 34f, p.textTertiary, repeat != null) { repeat?.let(controls::custom) }
    }
}

private const val ICON_MS = 200

/** Shrinks to 0.92 while [source] is pressed and springs back; nothing when not [enabled]. */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource, enabled: Boolean): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (enabled && pressed) 0.92f else 1f, spring(dampingRatio = 0.5f), label = "press")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
private fun CtrlButton(icon: Int, size: Float, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(px(88)).pressScale(source, enabled).clip(CircleShape).alpha(if (enabled) 1f else 0.4f)
            .then(
                if (enabled) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) { GrIcon(icon, size, tint) }
}

// Volume row geometry (mockup px): padding, icon, gap, bar, gap, number, padding.
private const val VolPad = 24f
private const val VolIcon = 32f
private const val VolGap = 20f
private const val VolNumber = 46f
private const val VolKnob = 14f

/** 0..100 at [x] along a bar starting at [start], [width] long; clamped at both ends. */
internal fun volumeOnTrack(x: Float, start: Float, width: Float): Int =
    if (width <= 0f) 0 else ((x - start) / width * 100f).roundToInt().coerceIn(0, 100)

/** Whether [x] is on the bar or within [slop] of its ends (a tap on the icon or the number does nothing). */
internal fun nearTrack(x: Float, start: Float, width: Float, slop: Float): Boolean =
    x >= start - slop && x <= start + width + slop

/** The bar inside the volume block, in this pointer scope's px: start, length, knob slop. */
private class VolTrack(val start: Float, val width: Float, val slop: Float) {
    fun near(x: Float) = nearTrack(x, start, width, slop)
    fun at(x: Float) = volumeOnTrack(x, start, width)
}

private fun PointerInputScope.volTrack(): VolTrack {
    val start = px(VolPad + VolIcon + VolGap).toPx()
    val width = size.width - start - px(VolGap + VolNumber + VolPad).toPx()
    return VolTrack(start, width, px(VolKnob).toPx())
}

/**
 * Volume input for the whole block: a tap on the bar or a drag that starts on it sets the value
 * at once; a drag then follows the finger past the ends. [GrVolume.dragging] holds the poll off.
 */
private fun Modifier.volumeInput(volume: GrVolume, set: (Int) -> Unit): Modifier {
    fun setTo(v: Int) {
        if (v != volume.value) {
            volume.value = v
            set(v)
        }
    }
    return pointerInput(volume) {
        detectTapGestures { volTrack().let { t -> if (t.near(it.x)) setTo(t.at(it.x)) } }
    }.pointerInput(volume) {
        var active = false
        val stop = { active = false; volume.dragging = false }
        detectHorizontalDragGestures(
            onDragStart = {
                val t = volTrack()
                active = t.near(it.x)
                if (active) {
                    volume.dragging = true
                    setTo(t.at(it.x))
                }
            },
            onDragEnd = stop,
            onDragCancel = stop,
            onHorizontalDrag = { change, _ -> if (active) setTo(volTrack().at(change.position.x)) },
        )
    }
}

/** The volume bar, drawing only; [volumeInput] on the block handles the touch. */
@Composable
private fun VolumeBar(value: Int, modifier: Modifier) {
    val p = LocalGr.current
    Box(
        modifier.height(px(28))
            .drawBehind {
                val h = px(8).toPx()
                val top = (size.height - h) / 2
                val r = CornerRadius(h / 2)
                drawRoundRect(p.track, Offset(0f, top), Size(size.width, h), r)
                val w = size.width * value / 100f
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(p.textPrimary.copy(alpha = 0.25f), p.textPrimary), 0f, size.width),
                        Offset(0f, top), Size(w, h), r,
                    )
                }
                drawCircle(Color.Black.copy(alpha = 0.25f), px(VolKnob).toPx(), Offset(w, size.height / 2 + px(2).toPx()))
                drawCircle(Color.White, px(VolKnob).toPx(), Offset(w, size.height / 2))
            },
    )
}

/** «Пробег сегодня» card: km today on the left, up to four stats in a 2x2 grid on the right. */
@Composable
fun TodayCard(todayKm: Double?, today: TodayStats?, hasFuelTank: Boolean, modifier: Modifier) {
    val p = LocalGr.current
    val cells = todayCells(today, hasFuelTank, p.electric, p.textPrimary)
    CardBody(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CardHeader(R.drawable.gr_ic_route, "Пробег сегодня")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitText(
                            todayKm?.let { grDec(it) } ?: "—",
                            grStyle(96f, 250, -4f, p.textPrimary), 96f, 56f, Modifier.weight(1f, fill = false),
                        )
                        Text(
                            "км", maxLines = 1, softWrap = false,
                            style = grStyle(34f, 400, color = p.textSecondary, tnum = false),
                            modifier = Modifier.padding(start = px(6), bottom = px(12)),
                        )
                    }
                }
            }
            Spacer(Modifier.width(px(28)))
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(px(18), Alignment.CenterVertically)) {
                for (row in cells.chunked(2)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(26))) {
                        // an odd last cell takes the whole row: a hidden stat leaves no hole
                        for (c in row) StatCell(c, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal data class TodayCell(val value: String, val label: String, val color: Color)

/**
 * The four «Пробег сегодня» cells, always all four so the card never looks empty before the
 * first trip: no data reads «0» for time and energy and «—» for share and speed.
 */
internal fun todayCells(today: TodayStats?, hasFuelTank: Boolean, electric: Color, primary: Color): List<TodayCell> {
    val kwh = grDec(today?.kwh ?: 0.0).let { if (today?.kwh == null) "0" else it }
    val litres = grDec(today?.litres ?: 0.0).let { if (today?.litres == null) "0" else it }
    return listOf(
        TodayCell(grDuration(today?.minutes ?: 0), "в пути", primary),
        TodayCell(today?.electricShare?.let { "$it%" } ?: "—", "электротяга", electric),
        TodayCell(today?.avgKmh?.let { "$it км/ч" } ?: "—", "средняя скорость", primary),
        if (hasFuelTank) TodayCell("$kwh · $litres", "кВт·ч · л", primary) else TodayCell(kwh, "кВт·ч", primary),
    )
}

@Composable
private fun StatCell(c: TodayCell, modifier: Modifier) {
    val p = LocalGr.current
    val line = p.divider
    Column(
        modifier.drawBehind { drawRect(line, Offset.Zero, Size(px(1).toPx(), size.height)) }.padding(start = px(18)),
    ) {
        FitText(c.value, grStyle(30f, 500, color = c.color), 30f, 22f, Modifier)
        FitText(c.label, grStyle(19f, 400, color = p.textTertiary, tnum = false), 19f, 15f, Modifier)
    }
}

/** 0..1 through the track; 0 when position or duration is unknown (the bar stays empty). */
internal fun mediaFraction(positionMs: Long?, durationMs: Long?): Float {
    if (positionMs == null || durationMs == null || durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
