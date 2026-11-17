package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.bydmate.app.R
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.common.px
import kotlin.math.roundToInt

@Composable
internal fun Controls(music: NowPlaying, controls: MusicControls, compact: Boolean = false) {
    val p = LocalGr.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = px(6)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val shuffle = music.shuffleAction
        val repeat = music.repeatAction
        val shuffleOpt = rememberOptimistic(music.shuffleOn, Triple(music.title, music.artist, music.album))
        val repeatOpt = rememberOptimistic(music.repeatMode, Triple(music.title, music.artist, music.album))
        val shuffleOn = shuffleOpt.value == true
        val mode = repeatOpt.value
        CtrlButton(R.drawable.gr_ic_shuffle, 34f, if (shuffleOn) p.highlight else p.textTertiary, shuffle != null) {
            shuffle?.let {
                controls.custom(it)
                shuffleOpt.expect(shuffleOpt.value?.not())
            }
        }
        CtrlButton(R.drawable.gr_ic_prev, 44f, p.textPrimary, true) { controls.previous() }
        val playSource = remember { MutableInteractionSource() }
        Box(
            Modifier.size(px(if (compact) 96 else 112)).pressScale(playSource, true).clip(CircleShape)
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
        CtrlButton(
            if (mode == 1) R.drawable.gr_ic_repeat_one else R.drawable.gr_ic_repeat, 34f,
            if (mode == 1 || mode == 2) p.highlight else p.textTertiary, repeat != null,
        ) {
            repeat?.let {
                controls.custom(it)
                repeatOpt.expect(nextRepeatMode(repeatOpt.value))
            }
        }
    }
}

private const val ICON_MS = 200

/** Shrinks to 0.92 while [source] is pressed and springs back; nothing when not [enabled]. */
@Composable
internal fun Modifier.pressScale(source: MutableInteractionSource, enabled: Boolean): Modifier {
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
private const val VOL_PAD = 24f
private const val VOL_ICON = 32f
private const val VOL_GAP = 20f
private const val VOL_NUMBER = 46f
private const val VOL_KNOB = 14f

/** The media volume row in its sunken block; the whole block takes the touch, not just the 28 px bar. */
@Composable
internal fun MediaVolume(volume: GrVolume, compact: Boolean = false) {
    val p = LocalGr.current
    val ctx = LocalContext.current
    InsetBox(26f, Modifier.fillMaxWidth().volumeInput(volume) { v -> GraphiteVolume.set(ctx, v) }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = px(VOL_PAD), vertical = px(if (compact) 14 else 18)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(px(VOL_GAP)),
        ) {
            GrIcon(R.drawable.gr_ic_volume, VOL_ICON, p.textSecondary)
            VolumeBar(volume.value, Modifier.weight(1f))
            Text(
                volume.value.toString(), maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                style = grStyle(26f, 500, color = p.textPrimary), modifier = Modifier.width(px(VOL_NUMBER)),
            )
        }
    }
}

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
    val start = px(VOL_PAD + VOL_ICON + VOL_GAP).toPx()
    val width = size.width - start - px(VOL_GAP + VOL_NUMBER + VOL_PAD).toPx()
    return VolTrack(start, width, px(VOL_KNOB).toPx())
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
                drawCircle(Color.Black.copy(alpha = 0.25f), px(VOL_KNOB).toPx(), Offset(w, size.height / 2 + px(2).toPx()))
                drawCircle(Color.White, px(VOL_KNOB).toPx(), Offset(w, size.height / 2))
            },
    )
}
