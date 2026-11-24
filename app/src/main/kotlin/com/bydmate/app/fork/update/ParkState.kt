package com.bydmate.app.fork.update

/**
 * «На стоянке» for the updater: stricter than the automation park gate on purpose. The owner
 * wanted both conditions, not one selector — gear P AND speed 0. fork-local.
 *
 * The automation park gate ([com.bydmate.app.data.automation.AutomationEngine], `requirePark`)
 * treats gear == 1 as P off the same live poll; this adds speed == 0 so a rolling car in P
 * (coasting, selector glitch) is never «parked» for the purpose of offering an install.
 */
object ParkState {

    /** Gear code for Park, matching the automation engine (`data.gear == 1`). */
    const val GEAR_PARK = 1

    /**
     * Parked only when both are known and agree: gear P and speed exactly 0. Unknown gear or
     * speed (null) is never parked — we do not offer an install on uncertain state.
     */
    fun isParked(gear: Int?, speed: Int?): Boolean = gear == GEAR_PARK && speed == 0
}
