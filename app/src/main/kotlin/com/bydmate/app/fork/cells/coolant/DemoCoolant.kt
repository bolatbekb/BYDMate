package com.bydmate.app.fork.cells.coolant

import android.os.SystemClock
import com.bydmate.app.data.remote.DiParsData
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.exp
import kotlin.math.roundToInt

private const val START_C = 40.0
private const val RUNNING_C = 88.0
private const val DEFAULT_OUTSIDE_C = 7
private const val WARM_TAU_S = 300.0
private const val COOL_TAU_S = 1800.0

/** Demo coolant: a first-order model, warms toward 88 °C while the engine runs, cools toward the outside air. Never touches the helper. */
class DemoCoolant(
    private val live: StateFlow<DiParsData?>,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : CoolantReader {
    private var temp = START_C
    private var lastMs = clock()

    override suspend fun readC(): Int? {
        val now = clock()
        val dtS = ((now - lastMs).coerceAtLeast(0L)) / 1000.0
        lastMs = now
        val car = live.value
        val running = (car?.engineRpm ?: 0) > 0
        val target = if (running) RUNNING_C else (car?.exteriorTemp ?: DEFAULT_OUTSIDE_C).toDouble()
        val tau = if (running) WARM_TAU_S else COOL_TAU_S
        temp = target + (temp - target) * exp(-dtS / tau)
        return coolantOrNull(temp.roundToInt())
    }
}
