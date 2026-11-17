package com.bydmate.app.ui.home.graphite

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bydmate.app.ui.home.OPTIMISTIC_MS
import com.bydmate.app.ui.home.Optimistic
import com.bydmate.app.ui.home.shown
import kotlinx.coroutines.delay

/** Opens the player on a tap (only [enabled]), with the press feel and no ripple. */
@Composable
internal fun Modifier.openSource(enabled: Boolean, onOpen: () -> Unit): Modifier {
    if (!enabled) return this
    val source = remember { MutableInteractionSource() }
    return this.pressScale(source, true).clickable(interactionSource = source, indication = null, onClick = onOpen)
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
internal fun <T> rememberOptimistic(polled: T, trackKey: Any? = null): OptimisticValue<T> {
    // A new track starts without the previous one's overlay.
    var opt by remember(trackKey) { mutableStateOf<Optimistic<T>?>(null) }
    var now by remember(trackKey) { mutableLongStateOf(0L) }
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
