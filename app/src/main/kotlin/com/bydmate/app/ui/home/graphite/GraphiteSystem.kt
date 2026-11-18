package com.bydmate.app.ui.home.graphite

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bydmate.app.ui.home.common.watchMusicVolume
import kotlin.math.roundToInt


/** Media volume (STREAM_MUSIC) as 0..100. */
object GraphiteVolume {
    fun read(ctx: Context): Int = runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt() else 0
    }.getOrDefault(0)

    /** Sets the stream to [percent]; no call when that is the index it already has (a drag moves 1 % at a time). */
    fun set(ctx: Context, percent: Int) {
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val index = volumeIndex(percent, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            if (index != am.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
            }
        }
    }
}

/** Stream index for 0..100 % of a stream with [max] steps. */
internal fun volumeIndex(percent: Int, max: Int): Int = (percent.coerceIn(0, 100) * max / 100f).roundToInt()

/** The media volume shown on the home: [value] 0..100; while [dragging] the poll leaves it alone. */
@Stable
class GrVolume(initial: Int) {
    var value by mutableIntStateOf(initial)
    var dragging = false
}

/**
 * [GraphiteVolume.read], re-read on the volume broadcast (and every 10 s) while the screen is started. The slider writes
 * the state at once; the next read is the source of truth, except during a drag, which the
 * poll would otherwise yank back to the last read value.
 */
@Composable
fun rememberVolume(): GrVolume {
    val ctx = LocalContext.current
    val volume = remember { GrVolume(GraphiteVolume.read(ctx)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // fork: a volume broadcast plus a 10-s fallback poll instead of a read every second
            watchMusicVolume(ctx) { if (!volume.dragging) volume.value = GraphiteVolume.read(ctx) }
        }
    }
    return volume
}
