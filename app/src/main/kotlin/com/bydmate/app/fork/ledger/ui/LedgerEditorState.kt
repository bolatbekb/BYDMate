package com.bydmate.app.fork.ledger.ui

import com.bydmate.app.fork.ledger.data.RefuelEntity
import com.bydmate.app.fork.ledger.domain.Prefill
import com.bydmate.app.fork.ledger.domain.RefuelMath
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.round

enum class RefuelField {
    ODOMETER, LITERS, TOTAL, PRICE,
    /** All three given but liters × price does not match total. */
    MISMATCH,
}

/**
 * Refuel form: text fields as typed, plus the hidden car fuel counter. A prefill never
 * overwrites an odometer the user typed ([odometerEditedByUser]).
 */
data class LedgerEditorState(
    val dateMs: Long,
    val odometer: String = "",
    val liters: String = "",
    val total: String = "",
    val price: String = "",
    val fullTank: Boolean = true,
    val missedBefore: Boolean = false,
    val note: String = "",
    val carFuelTotalL: Double? = null,
    val odometerEditedByUser: Boolean = false,
    /** The stored entry being edited (a draft or a confirmed refuel); null for a new one. */
    val base: RefuelEntity? = null,
    /** Detector's liters estimate of a draft: a hint only, never receipt data. */
    val sensorLiters: Double? = null,
    /** Odometer and car counter read at the same moment; the counter holds only near that odometer. */
    val pairedOdometerKm: Double? = null,
    val pairedCarFuelTotalL: Double? = null,
) {
    fun applyPrefill(p: Prefill): LedgerEditorState {
        val odo = if (odometerEditedByUser) odometer else p.odometerKm?.let(::plain).orEmpty()
        return copy(odometer = odo, pairedOdometerKm = p.odometerKm, pairedCarFuelTotalL = p.carFuelTotalL)
            .withPairedCounter()
    }

    fun onOdometerTyped(text: String): LedgerEditorState =
        copy(odometer = text, odometerEditedByUser = true).withPairedCounter()

    /** An odometer more than 1 km off the paired one makes the entry receipt-only (no car counter). */
    private fun withPairedCounter(): LedgerEditorState {
        val typed = num(odometer)
        val paired = pairedOdometerKm
        val matches = paired == null || (typed != null && abs(typed - paired) <= PAIR_KM)
        return copy(carFuelTotalL = if (matches) pairedCarFuelTotalL else null)
    }

    fun errors(): Set<RefuelField> = buildSet {
        if (bad(odometer)) add(RefuelField.ODOMETER)
        if (bad(liters)) add(RefuelField.LITERS)
        if (bad(total)) add(RefuelField.TOTAL)
        if (bad(price)) add(RefuelField.PRICE)
        if (isEmpty()) {
            val l = num(liters)
            val t = num(total)
            val p = num(price)
            if (l != null && t != null && p != null && abs(l * p - t) > maxOf(0.05, t * 0.005)) {
                add(RefuelField.MISMATCH)
            }
        }
    }

    /** Null while a field is invalid or fewer than two of liters / total / price are given. */
    fun toRefuel(): RefuelEntity? {
        if (errors().isNotEmpty()) return null
        val (l, t, p) = RefuelMath.complete(num(liters), num(total), num(price)) ?: return null
        val odo = num(odometer)
        val src = base ?: RefuelEntity(
            date = dateMs, odometerKm = null, liters = 0.0, totalCost = null, pricePerLiter = null,
            fullTank = fullTank, carFuelTotalL = null, tankPercentBefore = null, tankPercentAfter = null,
            lat = null, lon = null, place = null, note = null,
            source = "manual", status = "confirmed", importKey = null,
        )
        return src.copy(
            date = dateMs,
            odometerKm = odo,
            liters = r2(l),
            totalCost = r2(t),
            pricePerLiter = r2(p),
            fullTank = fullTank,
            missedBefore = missedBefore,
            carFuelTotalL = carFuelTotalL,
            note = note.trim().ifEmpty { null },
        )
    }

    companion object {
        fun new(nowMs: Long) = LedgerEditorState(dateMs = nowMs)

        /**
         * "+ Refuel" while a fresh detected draft is open confirms that draft; otherwise a blank entry.
         * An older draft (often a false positive left alone) is confirmed only from its own row, so a
         * receipt typed today never lands on its date and odometer.
         */
        fun forNewRefuel(refuels: List<RefuelEntity>, nowMs: Long): LedgerEditorState =
            refuels.filter { isOpenDraft(it) && abs(nowMs - it.date) <= ADD_ROUTES_TO_DRAFT_MS }
                .maxWithOrNull(compareBy<RefuelEntity> { it.date }.thenBy { it.id })?.let(::from) ?: new(nowMs)

        /** Same rule as RefuelDao.openDraft. */
        fun isOpenDraft(e: RefuelEntity): Boolean = e.status == "draft" && e.source == "detected"

        private const val ADD_ROUTES_TO_DRAFT_MS = 24 * 3_600_000L
        private const val PAIR_KM = 1.0

        /** Price is left blank (derived) when it matches total / liters, so editing one field recomputes it. */
        fun from(e: RefuelEntity): LedgerEditorState {
            val t = e.totalCost
            val p = e.pricePerLiter
            val derived = t != null && p != null && e.liters > 0.0 && abs(r2(t / e.liters) - p) < 0.005
            // a draft's liters come from the tank sensor, not a receipt
            val draft = e.status == "draft"
            return LedgerEditorState(
                dateMs = e.date,
                odometer = e.odometerKm?.let(::plain).orEmpty(),
                liters = if (e.liters > 0.0 && !draft) plain(e.liters) else "",
                sensorLiters = if (draft && e.liters > 0.0) e.liters else null,
                total = t?.let(::plain).orEmpty(),
                price = if (derived) "" else p?.let(::plain).orEmpty(),
                fullTank = e.fullTank,
                missedBefore = e.missedBefore,
                note = e.note.orEmpty(),
                carFuelTotalL = e.carFuelTotalL,
                base = e,
                pairedOdometerKm = e.odometerKm,
                pairedCarFuelTotalL = e.carFuelTotalL,
            )
        }

        fun num(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

        /** Non-blank and either unparseable or not positive. */
        fun bad(text: String): Boolean = text.isNotBlank() && (num(text)?.let { it <= 0.0 } ?: true)

        fun plain(v: Double): String =
            BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

        private fun r2(v: Double): Double = round(v * 100.0) / 100.0
    }
}
