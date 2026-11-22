package com.bydmate.app.fork.cells.data

/** Supported pack layouts. Everything of the battery check is hidden unless a real profile is chosen. */
enum class CarProfile(val id: String, val cells: Int, val probes: Int, val capacityKwh: Double, val capacityAh: Double) {
    NONE("none", 0, 0, 0.0, 0.0),
    LEOPARD5_318("leopard5_318", 184, 24, 31.8, 54.0),
    ;

    companion object {
        fun byId(id: String?): CarProfile = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class CheckView { WHAT_TO_DO, NORM_BY_CHARGE, RULER, EQUALIZATION }
