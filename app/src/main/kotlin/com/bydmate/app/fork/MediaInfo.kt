package com.bydmate.app.fork

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.bydmate.app.media.MediaSessionListenerService

/**
 * The active media sessions for the diagnostic dump header: which player, its state and what
 * metadata it publishes. Shows what the retro homes get from a player the fork never saw
 * (the car's own FM radio, AIMP's radio mode): which fields arrive and which stay empty.
 */
object MediaInfo {
    private const val MAX_TEXT = 60

    fun lines(context: Context): List<String> {
        val controllers = MediaSessionListenerService.activeControllers(context)
        if (controllers.isEmpty()) return listOf("media: no active sessions (or no listener access)")
        return controllers.mapIndexed { i, c ->
            val md = c.metadata
            val pb = c.playbackState
            fun text(key: String) = md?.getString(key)?.take(MAX_TEXT)?.let { "\"$it\"" } ?: "-"
            val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 }
            "media#$i: ${c.packageName} state=${stateName(pb?.state)} pos=${pb?.position ?: "-"} " +
                "dur=${duration ?: "-"} artist=${text(MediaMetadata.METADATA_KEY_ARTIST)} " +
                "albumArtist=${text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)} " +
                "title=${text(MediaMetadata.METADATA_KEY_TITLE)} " +
                "display=${text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)} " +
                "subtitle=${text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)} " +
                "actions=0x${java.lang.Long.toHexString(pb?.actions ?: 0)}"
        }
    }

    private fun stateName(state: Int?): String = when (state) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_NONE -> "none"
        null -> "-"
        else -> "s$state"
    }
}
