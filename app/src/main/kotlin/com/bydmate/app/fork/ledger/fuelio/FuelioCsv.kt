package com.bydmate.app.fork.ledger.fuelio

import java.time.LocalDate
import kotlin.math.round

class FuelioFormatException(message: String) : Exception(message)

data class FuelioFile(
    val vehicleName: String?,
    val refuels: List<FuelioRefuel>,
    val categories: Map<Int, String>,
    val costs: List<FuelioCost>,
    /** Log / cost rows dropped because their date or amount could not be read. */
    val skippedRows: Int = 0,
)

data class FuelioRefuel(
    val date: LocalDate,
    val odometerKm: Double?,
    val liters: Double,
    val total: Double?,
    val pricePerLiter: Double?,
    val full: Boolean,
    val missed: Boolean,
    val lat: Double?,
    val lon: Double?,
    val city: String?,
    val notes: String?,
    val uniqueId: String?,
)

data class FuelioCost(
    val title: String?,
    val date: LocalDate,
    val odometerKm: Double?,
    val categoryId: Int,
    val notes: String?,
    val amount: Double,
    val uniqueId: String?,
)

object FuelioCsv {
    private const val MILE_KM = 1.609344
    private const val US_GALLON_L = 3.785411784
    private const val UK_GALLON_L = 4.54609

    fun parse(text: String): FuelioFile {
        val sections = splitSections(RecordReader(text).read())
        if (!sections.containsKey("log") && !sections.containsKey("costs")) {
            throw FuelioFormatException("Not a Fuelio export: no ## Log or ## Costs section")
        }
        val vehicle = sections["vehicle"]?.rows()?.firstOrNull()?.get("name")?.blankToNull()
        val categories = HashMap<Int, String>()
        sections["costcategories"]?.rows()?.forEach { r ->
            val id = r["costtypeid"]?.trim()?.toIntOrNull()
            val name = r["name"]?.clean()
            if (id != null && !name.isNullOrEmpty()) categories[id] = name
        }
        var skipped = 0
        val refuels = sections["log"]?.let { parseLog(it) { skipped++ } }.orEmpty()
        val costs = sections["costs"]?.let { parseCosts(it) { skipped++ } }.orEmpty()
        return FuelioFile(vehicle, refuels, categories, costs, skipped)
    }

    // ---- sections ----

    private class Section(val header: List<String>, val data: List<List<String>>) {
        /** Rows keyed by normalized header name (see [normalize]). */
        fun rows(): List<Map<String, String>> {
            val keys = header.map(::normalize)
            return data.map { rec ->
                val m = HashMap<String, String>()
                keys.forEachIndexed { i, k -> if (k.isNotEmpty() && k !in m) m[k] = rec.getOrElse(i) { "" } }
                m
            }
        }
    }

    /** Lowercase, drop BOM, take the part before " (" — "Price (optional)" -> "price". */
    private fun normalize(h: String): String =
        h.replace("\uFEFF", "").trim().substringBefore(" (").trim().lowercase()

    private fun splitSections(records: List<List<String>>): Map<String, Section> {
        val result = LinkedHashMap<String, Section>()
        var i = 0
        while (i < records.size) {
            val first = records[i].firstOrNull()?.replace("\uFEFF", "")?.trim().orEmpty()
            if (records[i].size == 1 && first.startsWith("##")) {
                val name = first.removePrefix("##").trim().lowercase()
                val header = records.getOrNull(i + 1)
                    ?.takeIf { !(it.size == 1 && it[0].trim().startsWith("##")) }
                i += if (header != null) 2 else 1
                val data = ArrayList<List<String>>()
                while (i < records.size &&
                    !(records[i].size == 1 && records[i][0].replace("\uFEFF", "").trim().startsWith("##"))
                ) {
                    data.add(records[i]); i++
                }
                if (header != null && name !in result) result[name] = Section(header, data)
            } else i++
        }
        return result
    }

    // ---- Log ----

    private fun parseLog(s: Section, onSkip: () -> Unit): List<FuelioRefuel> {
        val keys = s.header
        val odoMiles = keys.any { it.trim().lowercase().startsWith("odo") && it.lowercase().contains("(mi") }
        val fuelHeader = keys.firstOrNull { normalize(it) == "fuel" }?.lowercase().orEmpty()
        val fuelFactor = when {
            fuelHeader.contains("us gal") -> US_GALLON_L
            fuelHeader.contains("uk gal") || fuelHeader.contains("imp") -> UK_GALLON_L
            else -> 1.0
        }
        val odoFactor = if (odoMiles) MILE_KM else 1.0
        return s.rows().mapNotNull { r ->
            val date = parseDate(r["date"] ?: r["data"]) ?: run { onSkip(); return@mapNotNull null }
            val liters = r["fuel"]?.num() ?: run { onSkip(); return@mapNotNull null }
            val ppl = r["volumeprice"]?.num()
            FuelioRefuel(
                date = date,
                odometerKm = r["odo"]?.num()?.let { round2(it * odoFactor) },
                liters = round2(liters * fuelFactor),
                total = r["price"]?.num()?.let(::round2),
                pricePerLiter = ppl?.takeIf { it > 0 }?.let { round2(it / fuelFactor) },
                full = r["full"].isTrue(),
                missed = r["missed"].isTrue(),
                lat = r["latitude"]?.num(),
                lon = r["longitude"]?.num(),
                city = r["city"]?.clean()?.blankToNull(),
                notes = r["notes"]?.clean()?.blankToNull(),
                uniqueId = r["uniqueid"]?.trim()?.blankToNull(),
            )
        }
    }

    // ---- Costs ----

    private fun parseCosts(s: Section, onSkip: () -> Unit): List<FuelioCost> {
        val odoMiles = s.header.any { it.trim().lowercase().startsWith("odo") && it.lowercase().contains("(mi") }
        val odoFactor = if (odoMiles) MILE_KM else 1.0
        return s.rows().mapNotNull { r ->
            if (r["istemplate"].isTrue() || r["isincome"].isTrue()) return@mapNotNull null
            val date = parseDate(r["date"] ?: r["data"]) ?: run { onSkip(); return@mapNotNull null }
            val amount = r["cost"]?.num() ?: run { onSkip(); return@mapNotNull null }
            FuelioCost(
                title = r["costtitle"]?.clean()?.blankToNull(),
                date = date,
                odometerKm = r["odo"]?.num()?.let { round2(it * odoFactor) },
                categoryId = r["costtypeid"]?.trim()?.toIntOrNull() ?: 0,
                notes = r["notes"]?.clean()?.blankToNull(),
                amount = round2(amount),
                uniqueId = r["uniqueid"]?.trim()?.blankToNull(),
            )
        }
    }

    // ---- helpers ----

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0

    private fun parseDate(raw: String?): LocalDate? {
        val s = raw?.replace("\uFEFF", "")?.trim().orEmpty()
        if (s.length < 10) return null
        return runCatching { LocalDate.parse(s.substring(0, 10)) }.getOrNull()
    }

    /** RFC-4180-ish: quoted fields, "" escape, newlines inside quotes. Blank lines are skipped. */
    private class RecordReader(private val text: String) {
        private val records = ArrayList<List<String>>()
        private var fields = ArrayList<String>()
        private val sb = StringBuilder()
        private var inQuotes = false
        private var wasQuoted = false

        fun read(): List<List<String>> {
            var i = 0
            while (i < text.length) {
                i = if (inQuotes) quoted(i) else plain(i)
            }
            if (sb.isNotEmpty() || fields.isNotEmpty() || wasQuoted) endRecord()
            return records
        }

        /** The character at [i] inside quotes; returns the index after it (after both of a `""` pair). */
        private fun quoted(i: Int): Int {
            if (text[i] != '"') {
                sb.append(text[i])
                return i + 1
            }
            if (i + 1 < text.length && text[i + 1] == '"') {
                sb.append('"')
                return i + 2
            }
            inQuotes = false
            return i + 1
        }

        /** The character at [i] outside quotes; returns the index after it. */
        private fun plain(i: Int): Int {
            when (val c = text[i]) {
                '"' -> {
                    inQuotes = true
                    wasQuoted = true
                }
                ',' -> endField()
                '\r' -> Unit
                '\n' -> endRecord()
                else -> sb.append(c)
            }
            return i + 1
        }

        private fun endField() {
            fields.add(sb.toString())
            sb.setLength(0)
            wasQuoted = false
        }

        private fun endRecord() {
            endField()
            if (!(fields.size == 1 && fields[0].isEmpty())) records.add(fields)
            fields = ArrayList()
        }
    }
}

private fun String?.isTrue(): Boolean = (this?.trim()?.toDoubleOrNull() ?: 0.0) != 0.0
private fun String.clean(): String = replace("\uFEFF", "").trim()
private fun String.blankToNull(): String? = trim().ifEmpty { null }
private fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()
