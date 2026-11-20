package com.bydmate.app.fork.automation

import android.content.Context
import com.bydmate.app.R

// Fork: the words a «Я.Музыка Car» action shows: rule line, editor row, import preview.

/** The command's name in the app language, or null for an unknown command / unreadable payload. */
internal fun yamusicCarCommandName(payload: String?, lc: Context): String? =
    YamusicCarPayload.fromPayload(payload)?.let { yamusicCarCommand(it.command) }?.let { lc.getString(it.nameRes) }

/** The line a rule shows: «Я.Музыка Car: Играть»; null when the command is unknown. */
internal fun yamusicCarLine(payload: String?, lc: Context): String? =
    yamusicCarCommandName(payload, lc)?.let { lc.getString(R.string.yamusic_car_line, it) }

/** What the import preview shows after the kind name: «Играть», plus the ID and «показать плеер» when set. */
internal fun yamusicCarDetail(payload: String?, lc: Context): String? {
    val p = YamusicCarPayload.fromPayload(payload) ?: return null
    val name = yamusicCarCommandName(payload, lc) ?: return null
    val c = yamusicCarCommand(p.command)
    return listOfNotNull(
        name,
        p.idValue()?.takeIf { it.isNotBlank() && it != "_" }?.let { lc.getString(R.string.yamusic_car_detail_id, it) },
        lc.getString(R.string.yamusic_car_detail_show).takeIf { p.show && c?.allowsShow != false },
    ).joinToString(", ")
}
