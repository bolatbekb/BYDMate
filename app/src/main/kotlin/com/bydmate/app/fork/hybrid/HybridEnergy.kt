package com.bydmate.app.fork.hybrid

/**
 * Net battery energy of a hybrid trip, and what of it is paid for. The adapter the author's trip recording and pricing
 * call through one-line `// fork` hooks; nothing here runs on an EV (the callers check the car type first).
 *
 * The car's lifetime electricity counter (`Statistic.STATISTIC_TOTAL_ELEC_CONSUMPTION`) is net: it falls when regen or
 * the engine puts energy back into the pack. Leopard 5 drive log 2026-11-23: it went down on every regen +1 % SOC, and
 * over four drives it moved with the SOC × 31.8 kWh. A trip that ends with more energy in the pack than it started with
 * therefore has a negative figure: it is kept with its minus (the pack gained), and its electricity costs nothing — the
 * engine's share is already in the fuel, a downhill one was never bought.
 */
object HybridEnergy {

    /** Bound of a gain when the pack capacity is not set: more than any pack, less than a counter reset. */
    const val FALLBACK_CAPACITY_KWH = 100.0

    /** SOC slack, percentage points: each SOC reading is rounded to 1 %, a regen tick moves it by one. */
    const val SOC_SLACK_PP = 2

    /** How far the gain may run ahead of the SOC rise × capacity (the SOC curve is not linear, the capacity not exact). */
    const val GAIN_PER_SOC_RISE_MAX = 1.5

    /**
     * [kwh] when a hybrid trip can really have gained that much (negative, finite, at most one pack), else null. With
     * both SOC readings known the gain must also fit the SOC: a SOC that fell by more than [SOC_SLACK_PP], or a gain
     * far beyond the SOC rise, means the counter glitched or was reset, not that the pack filled up.
     */
    fun plausibleGain(kwh: Double, socStart: Int?, socEnd: Int?, capacityKwh: Double): Double? {
        if (!kwh.isFinite() || kwh >= 0.0) return null
        val capacity = capacityKwh.takeIf { it > 0.0 } ?: FALLBACK_CAPACITY_KWH
        val gain = -kwh
        if (gain > capacity) return null
        if (socStart != null && socEnd != null) {
            val rise = socEnd - socStart
            if (rise < -SOC_SLACK_PP) return null
            if (gain > (rise + SOC_SLACK_PP) / 100.0 * capacity * GAIN_PER_SOC_RISE_MAX) return null
        }
        return kwh
    }

    /** kWh a trip's electricity is priced on: what it took out of the pack; a trip that gained energy pays 0. */
    fun billableKwh(kwh: Double): Double = kwh.coerceAtLeast(0.0)
}
