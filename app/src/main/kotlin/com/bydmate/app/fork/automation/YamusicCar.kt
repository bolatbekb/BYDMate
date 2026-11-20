package com.bydmate.app.fork.automation

import androidx.annotation.StringRes
import com.bydmate.app.R
import org.json.JSONObject

// Fork: the automation action «Я.Музыка Car» (kind `yamusic_car`). The commands of the player
// app `app.yamusic.car` are yamusiccar:// addresses; the action picks one from a list.

internal const val YAMUSIC_CAR_KIND = "yamusic_car"
internal const val YAMUSIC_CAR_PACKAGE = "app.yamusic.car"

// Payload keys, one place.
internal const val KEY_COMMAND = "command"
internal const val KEY_ID = "id"
internal const val KEY_UID = "uid"
internal const val KEY_KIND = "kind"
internal const val KEY_SHOW = "show"

/** What the identifier of a command looks like. */
internal enum class YamusicCarId { NONE, STATION, PLAYLIST }

/** One command of the list: [address] carries `{ID}` where the identifier goes. */
internal class YamusicCarCommand(
    val id: String,
    @StringRes val nameRes: Int,
    val address: String,
    val idKind: YamusicCarId = YamusicCarId.NONE,
    /** The «show the player» flag does not apply to the command that opens the player itself. */
    val allowsShow: Boolean = true,
)

internal val YAMUSIC_CAR_COMMANDS: List<YamusicCarCommand> = listOf(
    YamusicCarCommand("wave", R.string.yamusic_car_cmd_wave, "yamusiccar://play/wave"),
    YamusicCarCommand("wave_artist", R.string.yamusic_car_cmd_wave_artist,
        "yamusiccar://play/wave?station=artist:{ID}", YamusicCarId.STATION),
    YamusicCarCommand("wave_track", R.string.yamusic_car_cmd_wave_track,
        "yamusiccar://play/wave?station=track:{ID}", YamusicCarId.STATION),
    YamusicCarCommand("wave_album", R.string.yamusic_car_cmd_wave_album,
        "yamusiccar://play/wave?station=album:{ID}", YamusicCarId.STATION),
    YamusicCarCommand("wave_playlist", R.string.yamusic_car_cmd_wave_playlist,
        "yamusiccar://play/wave?station=playlist:{ID}", YamusicCarId.PLAYLIST),
    YamusicCarCommand("likes", R.string.yamusic_car_cmd_likes, "yamusiccar://play/likes"),
    YamusicCarCommand("likes_shuffle", R.string.yamusic_car_cmd_likes_shuffle, "yamusiccar://play/likes?shuffle=1"),
    YamusicCarCommand("resume", R.string.yamusic_car_cmd_resume, "yamusiccar://play/resume"),
    YamusicCarCommand("disk", R.string.yamusic_car_cmd_disk, "yamusiccar://play/disk"),
    YamusicCarCommand("play", R.string.yamusic_car_cmd_play, "yamusiccar://control/play"),
    YamusicCarCommand("pause", R.string.yamusic_car_cmd_pause, "yamusiccar://control/pause"),
    YamusicCarCommand("toggle", R.string.yamusic_car_cmd_toggle, "yamusiccar://control/toggle"),
    YamusicCarCommand("stop", R.string.yamusic_car_cmd_stop, "yamusiccar://control/stop"),
    YamusicCarCommand("next", R.string.yamusic_car_cmd_next, "yamusiccar://control/next"),
    YamusicCarCommand("prev", R.string.yamusic_car_cmd_prev, "yamusiccar://control/prev"),
    YamusicCarCommand("shuffle_on", R.string.yamusic_car_cmd_shuffle_on, "yamusiccar://control/shuffle?on=1"),
    YamusicCarCommand("shuffle_off", R.string.yamusic_car_cmd_shuffle_off, "yamusiccar://control/shuffle?on=0"),
    YamusicCarCommand("repeat_off", R.string.yamusic_car_cmd_repeat_off, "yamusiccar://control/repeat?mode=off"),
    YamusicCarCommand("repeat_all", R.string.yamusic_car_cmd_repeat_all, "yamusiccar://control/repeat?mode=all"),
    YamusicCarCommand("repeat_one", R.string.yamusic_car_cmd_repeat_one, "yamusiccar://control/repeat?mode=one"),
    YamusicCarCommand("open", R.string.yamusic_car_cmd_open, "yamusiccar://open", allowsShow = false),
)

private val STATION_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
private val PLAYLIST_UID = Regex("^[0-9]{1,64}$")
private val PLAYLIST_KIND = Regex("^[0-9]{1,12}$")

internal fun isYamusicCarStationId(v: String) = STATION_ID.matches(v)
internal fun isYamusicCarUid(v: String) = PLAYLIST_UID.matches(v)
internal fun isYamusicCarPlaylistKind(v: String) = PLAYLIST_KIND.matches(v)

internal fun yamusicCarCommand(id: String?): YamusicCarCommand? = YAMUSIC_CAR_COMMANDS.firstOrNull { it.id == id }

/** A playlist is `UID_KIND`: the owner's numeric uid and the playlist number. */
internal fun yamusicCarPlaylistId(uid: String, kind: String): String = "${uid}_$kind"

private fun idOrNull(kind: YamusicCarId, id: String?): String? = when (kind) {
    YamusicCarId.NONE -> ""
    YamusicCarId.STATION -> id?.takeIf { STATION_ID.matches(it) }
    YamusicCarId.PLAYLIST -> id?.let { v ->
        val at = v.lastIndexOf('_')
        v.takeIf { at > 0 && PLAYLIST_UID.matches(v.substring(0, at)) && PLAYLIST_KIND.matches(v.substring(at + 1)) }
    }
}

/**
 * The address of [command], or null for an unknown command, a missing or malformed [id].
 * [show] adds `ui=show` (the player comes to the front) except for the command that opens it.
 */
internal fun yamusicCarUri(command: String, id: String?, show: Boolean): String? {
    val c = yamusicCarCommand(command) ?: return null
    val value = idOrNull(c.idKind, id) ?: return null
    val uri = c.address.replace("{ID}", value)
    if (!show || !c.allowsShow) return uri
    return uri + (if ('?' in uri) "&" else "?") + "ui=show"
}

/** The fields of an action as the editor holds them and the payload stores them. */
internal data class YamusicCarPayload(
    val command: String = "wave",
    val id: String = "",
    val uid: String = "",
    val kind: String = "",
    val show: Boolean = false,
) {
    /** Only the fields that apply to [command]; `show` never for the command that opens the player. */
    fun toPayload(): String {
        val c = yamusicCarCommand(command)
        val json = JSONObject().put(KEY_COMMAND, command)
        when (c?.idKind) {
            YamusicCarId.STATION -> json.put(KEY_ID, id.trim())
            YamusicCarId.PLAYLIST -> json.put(KEY_UID, uid.trim()).put(KEY_KIND, kind.trim())
            else -> Unit
        }
        return json.put(KEY_SHOW, show && c?.allowsShow != false).toString()
    }

    /** Changing the command clears every ID field and the flag where it no longer applies. */
    fun withCommand(newCommand: String): YamusicCarPayload =
        YamusicCarPayload(command = newCommand, show = show && yamusicCarCommand(newCommand)?.allowsShow != false)

    /** The identifier the address carries: station id, or `UID_KIND`; null when the command has none. */
    fun idValue(): String? = when (yamusicCarCommand(command)?.idKind) {
        YamusicCarId.STATION -> id
        YamusicCarId.PLAYLIST -> yamusicCarPlaylistId(uid, kind)
        else -> null
    }

    companion object {
        /** Null when the payload is not JSON; a missing command reads as «wave». */
        fun fromPayload(payload: String?): YamusicCarPayload? {
            val json = try { JSONObject(payload ?: return null) } catch (_: Exception) { return null }
            return YamusicCarPayload(
                command = json.optString(KEY_COMMAND),
                id = json.optString(KEY_ID),
                uid = json.optString(KEY_UID),
                kind = json.optString(KEY_KIND),
                show = json.optBoolean(KEY_SHOW, false),
            )
        }
    }
}

internal fun YamusicCarPayload.uri(): String? = yamusicCarUri(command, idValue(), show)

/** The address of a saved action payload; null when the payload does not describe a valid command. */
internal fun yamusicCarUriFromPayload(payload: String?): String? = YamusicCarPayload.fromPayload(payload)?.uri()
