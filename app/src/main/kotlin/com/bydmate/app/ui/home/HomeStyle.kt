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
    HYBRID("hybrid", "Стойка RS"),
    CDRIVE("cdrive", "Стойка C-Drive"),
    GRAPHITE("graphite", "Graphite"),
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
        current = HomeStyle.entries.firstOrNull { it.id == id } ?: HomeStyle.STANDARD
        CassetteRotation.init(context)
        ReelCassetteRotation.init(context)
        DayMileage.init(context)
        GraphiteLookState.init(context)
    }

    fun select(context: Context, style: HomeStyle) {
        current = style
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, style.id).apply()
    }
}
