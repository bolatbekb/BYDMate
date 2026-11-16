package com.bydmate.app.ui.home.cdrive

/** Gear letter for the C-Drive header: 1=P, 2=R, 3=N, 4=D, anything else a dash. */
fun gearLetter(gear: Int?): String = when (gear) {
    1 -> "P"
    2 -> "R"
    3 -> "N"
    4 -> "D"
    else -> "—"
}

enum class ReadyShown { READY, ON, OFF }

/**
 * Turns the car's power state (0=OFF, 1=ON, 2=DRIVE) into the lamp. A rise shows at once;
 * a fall to a lower state shows only after it has lasted [holdMs], because pressing the start
 * button on a car that is already on dips the state to 0 for ~0.6 s. Pure: time is passed in.
 */
class ReadyLamp(private val holdMs: Long = 1_000) {
    private var shown: ReadyShown? = null
    private var lowerSince: Long? = null

    fun update(powerState: Int?, nowMs: Long): ReadyShown {
        val target = when (powerState) {
            2 -> ReadyShown.READY
            1 -> ReadyShown.ON
            else -> ReadyShown.OFF
        }
        val cur = shown
        if (cur == null || rank(target) >= rank(cur)) {
            shown = target
            lowerSince = null
            return target
        }
        val since = lowerSince ?: nowMs.also { lowerSince = it }
        if (nowMs - since >= holdMs) {
            shown = target
            lowerSince = null
            return target
        }
        return cur
    }

    private fun rank(s: ReadyShown) = when (s) {
        ReadyShown.OFF -> 0
        ReadyShown.ON -> 1
        ReadyShown.READY -> 2
    }
}
