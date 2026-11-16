package com.bydmate.app.fork.ledger.domain

import kotlin.math.round

object RefuelMath {
    private fun r2(v: Double): Double = round(v * 100.0) / 100.0

    /** Any two of (liters, total, price) give the third; returns (liters, total, price) or null. */
    fun complete(liters: Double?, total: Double?, price: Double?): Triple<Double, Double, Double>? {
        return when {
            liters != null && total != null && price != null -> Triple(liters, total, price)
            liters != null && total != null -> {
                if (liters == 0.0) null else Triple(liters, total, r2(total / liters))
            }
            liters != null && price != null -> Triple(liters, r2(liters * price), price)
            total != null && price != null -> {
                if (price == 0.0) null else Triple(r2(total / price), total, price)
            }
            else -> null
        }
    }
}
