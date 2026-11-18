package com.bydmate.app.ui.home.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

internal const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

/** The fallback poll for a head unit that never sends the volume broadcast. */
internal const val VOLUME_FALLBACK_MS = 10_000L

/** Whether [intent] reports the music stream (an intent without a stream type is taken as a change too). */
internal fun isMusicVolumeChange(intent: Intent?): Boolean =
    intent?.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC) == AudioManager.STREAM_MUSIC

/**
 * Calls [read] now, on every ping and at least every [fallbackMs]; ends only by cancellation. The pings are
 * conflated: a burst of volume steps reads once.
 */
internal suspend fun readOnPingOrTimeout(pings: ReceiveChannel<Unit>, fallbackMs: Long, read: () -> Unit) {
    while (true) {
        read()
        withTimeoutOrNull(fallbackMs) { pings.receive() }
    }
}

/**
 * Reads the volume while this runs (call it inside repeatOnLifecycle(STARTED)): at once, on each music-volume
 * broadcast, and every 10 s as a fallback. The receiver is unregistered when the coroutine is cancelled.
 */
internal suspend fun watchMusicVolume(context: Context, read: () -> Unit) {
    val pings = Channel<Unit>(Channel.CONFLATED)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (isMusicVolumeChange(intent)) pings.trySend(Unit)
        }
    }
    runCatching {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    try {
        readOnPingOrTimeout(pings, VOLUME_FALLBACK_MS, read)
    } finally {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
