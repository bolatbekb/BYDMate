package com.bydmate.app.fork.mycar

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Мой автомобиль» own settings in a small prefs file: the user's car name and the one-shot flag that
 * records the model was autofilled from the head unit once (so a later manual «Не выбрано» sticks).
 * The model itself, battery size and tank live in their existing stores — this holds only what is new.
 * fork-local.
 */
class MyCarSettings(private val prefs: SharedPreferences) {

    private val nameState = MutableStateFlow(prefs.getString(KEY_NAME, "").orEmpty())
    val carName: StateFlow<String> = nameState.asStateFlow()

    fun setCarName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
        nameState.value = name
    }

    val modelAutofilled: Boolean get() = prefs.getBoolean(KEY_MODEL_AUTOFILLED, false)

    fun markModelAutofilled() = prefs.edit().putBoolean(KEY_MODEL_AUTOFILLED, true).apply()

    companion object {
        const val PREFS_NAME = "bydmate_fork_my_car"
        private const val KEY_NAME = "car_name"
        private const val KEY_MODEL_AUTOFILLED = "model_autofilled"
    }
}
