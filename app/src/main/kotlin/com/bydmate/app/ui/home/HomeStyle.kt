package com.bydmate.app.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bydmate.app.ui.home.graphite.GraphiteLookState

/** Which home screen the Dashboard tab shows. Fork-local; stored in its own prefs file. */
enum class HomeStyle(val id: String, val title: String) {
    STANDARD("standard", "Стандартная"),
    KENWOOD("kenwood", "Стойка KX"),
    ONKYO("onkyo", "Стойка Integra"),
    CDRIVE("cdrive", "Стойка C-Drive"),
    GRAPHITE("graphite", "Graphite"),
    ;

    companion object {
        /** «Стойка RS» (hybrid) was removed in favour of C-Drive; anything unknown is the author's screen. */
        fun fromId(id: String?): HomeStyle =
            if (id == REMOVED_RS) CDRIVE else entries.firstOrNull { it.id == id } ?: STANDARD

        internal const val REMOVED_RS = "hybrid"
    }
}

object HomeStyleState {
    private const val PREFS = "bydmate_home"
    private const val KEY = "home_style"

    var current: HomeStyle by mutableStateOf(HomeStyle.STANDARD)
        private set

    fun init(context: Context) {
        val id = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        current = HomeStyle.fromId(id)
        if (id == HomeStyle.REMOVED_RS) select(context, current)
        CassetteRotation.init(context)
        DayMileage.init(context)
        GraphiteLookState.init(context)
    }

    fun select(context: Context, style: HomeStyle) {
        current = style
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style.id).apply()
    }
}
