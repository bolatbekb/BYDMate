package com.bydmate.app.fork.cells.data

/**
 * The chosen car: everything of the battery check is hidden unless a real profile is chosen. The pack itself comes from
 * the car (its cell and probe counts from 0x43A); [cells] and [probes] only stand in until the first reading and in the
 * demo, [capacityKwh] only while the driver has not entered the capacity in the settings. The [id] is what the settings
 * keep: it never changes, so a chosen car stays chosen.
 */
enum class CarProfile(val id: String, val cells: Int, val probes: Int, val capacityKwh: Double) {
    NONE("none", 0, 0, 0.0),
    LEOPARD5_318("leopard5_318", 184, 24, 31.8),
    ;

    companion object {
        fun byId(id: String?): CarProfile = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class CheckView { WHAT_TO_DO, NORM_BY_CHARGE, RULER, EQUALIZATION }
