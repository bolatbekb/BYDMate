package com.bydmate.app.fork.ledger.fuelio

import com.bydmate.app.fork.ledger.data.ExpenseCategoryEntity
import com.bydmate.app.fork.ledger.data.ExpenseEntity
import com.bydmate.app.fork.ledger.data.LedgerRepository
import com.bydmate.app.fork.ledger.data.RefuelEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class FuelioPreview(
    val newRefuels: Int,
    val duplicateRefuels: Int,
    val newCosts: Int,
    val duplicateCosts: Int,
    val newCategories: List<String>,
    val skippedRows: Int = 0,
)

data class FuelioResult(val addedRefuels: Int, val addedCosts: Int)

class FuelioImporter(private val repo: LedgerRepository) {

    suspend fun preview(raw: FuelioFile): FuelioPreview {
        val file = dedupe(raw)
        val existingRefuel = repo.refuels.getAllAsc().mapNotNull { it.importKey }.toSet()
        val existingCost = existingKeysOfExpenses()
        val newR = file.refuels.count { refuelKey(it) !in existingRefuel }
        val newC = file.costs.count { costKey(it) !in existingCost }
        val known = repo.categories.getAll()
        val newCats = LinkedHashMap<String, String>()
        file.costs.filter { costKey(it) !in existingCost }.forEach { c ->
            val name = file.categories[c.categoryId]?.cleaned() ?: return@forEach
            if (BUILTIN_MAP[name.lowercase()] == null &&
                known.none { it.name.equals(name, ignoreCase = true) }
            ) newCats.putIfAbsent(name.lowercase(), name)
        }
        return FuelioPreview(
            newR, raw.refuels.size - newR, newC, raw.costs.size - newC,
            newCats.values.toList(), raw.skippedRows,
        )
    }

    suspend fun apply(raw: FuelioFile): FuelioResult {
        val file = dedupe(raw)
        var addedR = 0
        for (r in file.refuels) {
            val id = repo.refuels.insert(
                RefuelEntity(
                    date = noon(r.date), odometerKm = r.odometerKm, liters = r.liters,
                    totalCost = r.total, pricePerLiter = r.pricePerLiter, fullTank = r.full,
                    missedBefore = r.missed, carFuelTotalL = null,
                    tankPercentBefore = null, tankPercentAfter = null,
                    lat = r.lat, lon = r.lon, place = r.city, note = r.notes,
                    source = "fuelio", status = "confirmed", importKey = refuelKey(r),
                )
            )
            if (id != -1L) addedR++
        }
        val idByFuelioCat = HashMap<Int, Long>()
        val existingCost = existingKeysOfExpenses()
        var addedC = 0
        for (c in file.costs) {
            val key = costKey(c)
            if (key in existingCost) continue
            val catId = idByFuelioCat.getOrPut(c.categoryId) { resolveCategory(file.categories[c.categoryId]) }
            val id = repo.expenses.insert(
                ExpenseEntity(
                    date = noon(c.date), odometerKm = c.odometerKm, categoryId = catId,
                    amount = c.amount, title = c.title, note = c.notes,
                    source = "fuelio", importKey = key,
                )
            )
            if (id != -1L) addedC++
        }
        return FuelioResult(addedR, addedC)
    }

    /** Rows sharing an import key inside one file count (and are written) once. */
    private fun dedupe(f: FuelioFile) = f.copy(
        refuels = f.refuels.distinctBy(::refuelKey),
        costs = f.costs.distinctBy(::costKey),
    )

    private suspend fun resolveCategory(rawName: String?): Long {
        val all = repo.categories.getAll()
        val name = rawName?.cleaned().orEmpty()
        BUILTIN_MAP[name.lowercase()]?.let { key ->
            repo.categories.getByBuiltinKey(key)?.let { return it.id }
        }
        if (name.isNotEmpty()) {
            all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.id }
        }
        if (name.isEmpty()) {
            repo.categories.getByBuiltinKey("other")?.let { return it.id }
        }
        return repo.categories.insert(
            ExpenseCategoryEntity(
                name = name.ifEmpty { "Fuelio" }, builtinKey = null, color = null,
                sort = (all.maxOfOrNull { it.sort } ?: 0) + 1,
            )
        )
    }

    private suspend fun existingKeysOfExpenses(): Set<String> =
        repo.expenses.observeAll().first().mapNotNull { it.importKey }.toSet()

    private fun String.cleaned() = replace("﻿", "").trim()

    private fun noon(d: LocalDate): Long =
        d.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun refuelKey(r: FuelioRefuel) =
        "fuelio:r:" + (r.uniqueId ?: "${r.date}:${r.odometerKm}:${r.liters}")

    private fun costKey(c: FuelioCost) =
        "fuelio:c:" + (c.uniqueId ?: "${c.date}:${c.odometerKm}:${c.amount}:${c.categoryId}")

    private companion object {
        val BUILTIN_MAP = mapOf(
            "service" to "service", "maintenance" to "repair", "insurance" to "insurance",
            "registration" to "registration", "parking" to "parking", "wash" to "wash",
            "tolls" to "tolls", "tickets/fines" to "fines", "tuning" to "tuning",
            "accessories" to "accessories", "purchase" to "other",
        )
    }
}
