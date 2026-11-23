package com.bydmate.app.fork.cells.collect

enum class MeasureKind { AFTER_PARKING, REST_IN_SESSION, AFTER_CHARGE, LOAD, MANUAL }

enum class Relax { NOT_NEEDED, PENDING, SETTLED, RELAXING, UNKNOWN }

/** One reading of the car state. [wallMs] is the wall clock, [elapsedMs] the monotonic one (counts sleep). */
data class LiveSample(
    val wallMs: Long,
    val elapsedMs: Long,
    val powerOn: Boolean,
    val soc: Int?,
    val currentA: Double?,
    val packV: Int?,
    val charging: Boolean,
)

/** Last stored snapshot of the previous session. */
data class SessionAnchor(val wallMs: Long, val elapsedMs: Long, val soc: Int?)

data class Due(
    val scheduled: Boolean,
    val afterParking: Boolean,
    val rest60: Boolean,
    val load: Boolean,
    val relaxCheck: Boolean,
    val keepalive: Boolean,
    val windowStartMs: Long,
) {
    val wantsSnapshot: Boolean get() = scheduled || afterParking || rest60 || load || relaxCheck
}

data class RelaxUpdate(val measurementId: Long, val relax: Relax, val dropMv: Int?)

data class Step(val due: Due, val relax: List<RelaxUpdate>)

data class StoredSnapshot(val id: Long, val elapsedMs: Long, val vmaxMv: Int, val soc: Int?, val measure: MeasureKind?)

private const val TOP_VMAX_MV = 3400
private const val TOP_SOC = 95
private const val SETTLED_DROP_MV = 3

fun measureFor(due: Due, vmaxMv: Int, soc: Int?): MeasureKind? {
    val top = vmaxMv > TOP_VMAX_MV || (soc ?: 0) >= TOP_SOC
    return when {
        due.afterParking -> if (top) MeasureKind.AFTER_CHARGE else MeasureKind.AFTER_PARKING
        due.rest60 -> if (top) MeasureKind.AFTER_CHARGE else MeasureKind.REST_IN_SESSION
        due.load -> MeasureKind.LOAD
        else -> null
    }
}

fun relaxOutcome(firstVmaxMv: Int, secondVmaxMv: Int): Relax =
    if (firstVmaxMv - secondVmaxMv > SETTLED_DROP_MV) Relax.RELAXING else Relax.SETTLED
