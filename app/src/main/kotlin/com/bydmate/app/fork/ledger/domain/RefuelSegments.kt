package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.fork.ledger.data.RefuelEntity

data class RefuelSegment(
    val from: RefuelEntity,
    val to: RefuelEntity,
    val distanceKm: Double?,
    val litersBought: Double,
    val litersBurnedCar: Double?,
    val per100Receipts: Double?,
    val per100Car: Double?,
    /** Percent: (bought - burned) / burned * 100. */
    val mismatchPct: Double?,
    val fuelCost: Double?,
    val costPerKm: Double?,
    val complete: Boolean,
)

object RefuelSegments {
    fun build(refuels: List<RefuelEntity>): List<RefuelSegment> {
        val list = refuels
            .filter { it.status == "confirmed" }
            .sortedWith(compareBy<RefuelEntity> { it.date }.thenBy { it.odometerKm ?: Double.MAX_VALUE })
        val out = mutableListOf<RefuelSegment>()
        for (i in list.indices) {
            val j = nextFull(list, i) ?: continue
            val inside = list.subList(i + 1, j + 1)
            if (inside.none { it.missedBefore }) out += segment(list[i], list[j], inside)
        }
        return out
    }

    /** Index of the full tank that closes a segment opened at [i]; null when [i] is no full tank or none follows. */
    private fun nextFull(list: List<RefuelEntity>, i: Int): Int? {
        if (!list[i].fullTank) return null
        var j = i + 1
        while (j < list.size && !list[j].fullTank) j++
        return j.takeIf { it < list.size }
    }

    private fun segment(a: RefuelEntity, b: RefuelEntity, inside: List<RefuelEntity>): RefuelSegment {
        val bought = inside.sumOf { it.liters }
        val oa = a.odometerKm
        val ob = b.odometerKm
        val dist = if (oa != null && ob != null) ob - oa else null
        if (dist == null || dist <= 0.0) {
            return RefuelSegment(a, b, null, bought, null, null, null, null, null, null, false)
        }
        val ca = a.carFuelTotalL
        val cb = b.carFuelTotalL
        val burned = if (ca != null && cb != null && cb - ca >= 0.0) cb - ca else null
        val costs = inside.map { it.totalCost }
        val cost = if (costs.all { it != null }) costs.sumOf { it!! } else null
        return RefuelSegment(
            from = a,
            to = b,
            distanceKm = dist,
            litersBought = bought,
            litersBurnedCar = burned,
            per100Receipts = bought / dist * 100.0,
            per100Car = burned?.let { it / dist * 100.0 },
            mismatchPct = burned?.takeIf { it > 0.0 }?.let { (bought - it) / it * 100.0 },
            fuelCost = cost,
            costPerKm = cost?.let { it / dist },
            complete = true,
        )
    }
}
