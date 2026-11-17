package com.bydmate.app.ui.home.graphite

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.bydmate.app.ui.home.OPTIMISTIC_MS
import com.bydmate.app.ui.home.Optimistic
import com.bydmate.app.ui.home.shown
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.bydmate.app.R
import com.bydmate.app.ui.home.DislikeArm
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.common.px
import kotlinx.coroutines.delay

/** Opens the player on a tap (only [enabled]), with the press feel and no ripple. */
@Composable
internal fun Modifier.openSource(enabled: Boolean, onOpen: () -> Unit): Modifier {
    if (!enabled) return this
    val source = remember { MutableInteractionSource() }
    return this.pressScale(source, true).clickable(interactionSource = source, indication = null, onClick = onOpen)
}

/** Heart (like) and thumbs-down (two-tap dislike) in the card header; each only if the player has the action. */
@Composable
internal fun MediaActionButtons(music: NowPlaying, controls: MusicControls) {
    val p = LocalGr.current
    if (music.likeAction != null) {
        val like = rememberOptimistic(music.liked)
        val on = like.value == true
        HeaderIcon(if (on) R.drawable.gr_ic_heart_filled else R.drawable.gr_ic_heart, if (on) p.highlight else p.textTertiary) {
            controls.like()
            like.expect(like.value?.not())
        }
    }
    if (music.dislikeAction != null) {
        val arm = remember { DislikeArm() }
        var now by remember { mutableLongStateOf(0L) }
        var gen by remember { mutableIntStateOf(0) }
        // One delay to the end of the 3 s window re-reads «armed»; nothing runs per frame.
        LaunchedEffect(gen) {
            val left = arm.remainingMs(SystemClock.elapsedRealtime())
            if (left > 0) {
                delay(left)
                now = SystemClock.elapsedRealtime()
            }
        }
        HeaderIcon(R.drawable.gr_ic_dislike, if (arm.armed(now)) p.highlight else p.textTertiary) {
            val t = SystemClock.elapsedRealtime()
            now = t
            if (arm.tap(t)) controls.dislike()
            gen++
        }
    }
}

/** A 34 px header icon in a 56 px touch box (it may outgrow the 34 px header row). */
@Composable
private fun HeaderIcon(icon: Int, tint: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.requiredSize(px(56)).pressScale(source, true).clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GrIcon(icon, 34f, tint) }
}

/** The mode a «repeat» tap leads to (off → list → track → off); null while the mode is unknown. */
internal fun nextRepeatMode(mode: Int?): Int? = when (mode) {
    0 -> 2
    2 -> 1
    1 -> 0
    else -> null
}

/** A polled value with a short-lived optimistic overlay (see [shown]); [expect] starts it. */
internal class OptimisticValue<T>(val value: T, private val onExpect: (T) -> Unit) {
    fun expect(v: T?) {
        if (v != null) onExpect(v)
    }
}

/**
 * [polled], or the value just asked for through [OptimisticValue.expect] for up to 2 s (until the poll
 * agrees). One delay to the end of the window re-reads it; nothing runs per frame.
 */
@Composable
internal fun <T> rememberOptimistic(polled: T): OptimisticValue<T> {
    var opt by remember { mutableStateOf<Optimistic<T>?>(null) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(polled) { if (opt?.value == polled) opt = null }
    LaunchedEffect(opt) {
        val o = opt ?: return@LaunchedEffect
        val left = OPTIMISTIC_MS - (SystemClock.elapsedRealtime() - o.atMs)
        if (left > 0) delay(left)
        now = SystemClock.elapsedRealtime()
    }
    return OptimisticValue(shown(polled, opt, now)) { v ->
        val t = SystemClock.elapsedRealtime()
        now = t
        opt = Optimistic(v, t)
    }
}
