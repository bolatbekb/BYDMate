package com.bydmate.app.fork.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.StringRes
import com.bydmate.app.R
import com.bydmate.app.data.automation.DispatchResult
import com.bydmate.app.util.AppStrings
import org.json.JSONObject

// Fork: the automation action «Я.Музыка Car» (kind `yamusic_car`). The commands of the player
// app `app.yamusic.car` are yamusiccar:// addresses; the action picks one from a list.

internal const val YAMUSIC_CAR_KIND = "yamusic_car"
internal const val YAMUSIC_CAR_PACKAGE = "app.yamusic.car"

/** What the identifier of a command looks like. */
enum class YamusicCarId { NONE, STATION, PLAYLIST }

/** One command of the list: [address] carries `{ID}` where the identifier goes. */
class YamusicCarCommand(
    val id: String,
    @StringRes val nameRes: Int,
    val address: String,
    val idKind: YamusicCarId = YamusicCarId.NONE,
    /** The «show the player» flag does not apply to the command that opens the player itself. */
    val allowsShow: Boolean = true,
)

val YAMUSIC_CAR_COMMANDS: List<YamusicCarCommand> = listOf(
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

fun yamusicCarCommand(id: String?): YamusicCarCommand? = YAMUSIC_CAR_COMMANDS.firstOrNull { it.id == id }

/** A playlist is `UID_KIND`: the owner's numeric uid and the playlist number. */
fun yamusicCarPlaylistId(uid: String, kind: String): String = "${uid}_$kind"

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
fun yamusicCarUri(command: String, id: String?, show: Boolean): String? {
    val c = yamusicCarCommand(command) ?: return null
    val value = idOrNull(c.idKind, id) ?: return null
    val uri = c.address.replace("{ID}", value)
    if (!show || !c.allowsShow) return uri
    return uri + (if ('?' in uri) "&" else "?") + "ui=show"
}

/** The address of a saved action payload; null when the payload does not describe a valid command. */
fun yamusicCarUriFromPayload(payload: String?): String? {
    val json = try { JSONObject(payload ?: return null) } catch (_: Exception) { return null }
    val command = json.optString("command")
    val id = when (yamusicCarCommand(command)?.idKind) {
        YamusicCarId.STATION -> json.optString("id")
        YamusicCarId.PLAYLIST -> yamusicCarPlaylistId(json.optString("uid"), json.optString("kind"))
        else -> null
    }
    return yamusicCarUri(command, id, json.optBoolean("show", false))
}

/** Runs the action: not installed or a bad command/ID is a failed result, the rest goes through [start]. */
fun dispatchYamusicCar(
    payload: String?,
    context: Context,
    strings: AppStrings,
    start: (Intent, String) -> DispatchResult,
): DispatchResult {
    val command = try { JSONObject(payload ?: "{}").optString("command") } catch (_: Exception) { "" }
    val uri = yamusicCarUriFromPayload(payload)
        ?: return DispatchResult(false, strings.get(R.string.yamusic_car_invalid))
    if (!isYamusicCarInstalled(context)) return DispatchResult(false, strings.get(R.string.yamusic_car_not_installed))
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .setPackage(YAMUSIC_CAR_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // The label never carries an ID value: only the command.
    return start(intent, "yamusic_car:$command")
}

private fun isYamusicCarInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(YAMUSIC_CAR_PACKAGE, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** The line a rule shows: «Я.Музыка Car: Играть». */
fun yamusicCarCommandName(payload: String?, lc: Context): String? {
    val command = try { JSONObject(payload ?: return null).optString("command") } catch (_: Exception) { return null }
    return yamusicCarCommand(command)?.let { lc.getString(it.nameRes) }
}
