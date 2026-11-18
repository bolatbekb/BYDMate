package com.bydmate.app.ui.home.graphite

import kotlin.math.PI
import kotlin.math.sin

/* ---------------------------------------------------------------- media card motion (pure) */

// Per bar: two periods (s) and phases, so no two bars move together.
private val EqSlow = floatArrayOf(0.71f, 0.53f, 0.83f, 0.61f)
private val EqFast = floatArrayOf(0.29f, 0.37f, 0.23f, 0.41f)
private val EqPhase = floatArrayOf(0.0f, 1.7f, 3.1f, 4.4f)

/** Height of equalizer bar [bar] (0..3) at [tSec], 0.25..1.0: two sines, 60 / 40. */
internal fun eqLevel(bar: Int, tSec: Float): Float {
    val i = bar.mod(EqSlow.size)
    val w = 2 * PI.toFloat()
    val s = 0.6f * sin(w * tSec / EqSlow[i] + EqPhase[i]) + 0.4f * sin(w * tSec / EqFast[i] + 2 * EqPhase[i])
    return (0.625f + 0.375f * s).coerceIn(0.25f, 1f)
}
