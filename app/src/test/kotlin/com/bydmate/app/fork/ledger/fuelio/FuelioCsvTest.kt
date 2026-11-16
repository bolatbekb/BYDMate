package com.bydmate.app.fork.ledger.fuelio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelioCsvTest {

    private val sample: String =
        javaClass.getResourceAsStream("/fuelio/fuelio_sample.csv")!!.bufferedReader().readText()

    @Test fun parsesAllRefuels() {
        val f = FuelioCsv.parse(sample)
        assertEquals(5, f.refuels.size)
        assertEquals("Test Car", f.vehicleName)
        assertEquals(false, f.refuels[2].full)
        assertEquals(true, f.refuels[3].missed)
        assertEquals("101", f.refuels[0].uniqueId)
        assertEquals(10.5, f.refuels[1].lat!!, 0.0)
        assertNull(f.refuels[0].lat)
        assertEquals(2.0, f.refuels[0].pricePerLiter!!, 0.0)
        assertEquals(60.0, f.refuels[0].total!!, 0.0)
    }

    @Test fun roundsFloatNoise() {
        val f = FuelioCsv.parse(sample)
        assertEquals(27.12, f.refuels[1].liters, 0.0)
        assertEquals(17955.04, f.refuels[4].odometerKm!!, 0.0)
    }

    @Test fun findsColumnsByHeaderName() {
        val text = """
            "## Log"
            "Notes","City","UniqueId","Fuel (l)","Date","Price (optional)","Odo (km)","Full"
            "n","Town","u1","12.5","2025-05-01","25","500","1"
        """.trimIndent()
        val r = FuelioCsv.parse(text).refuels.single()
        assertEquals(12.5, r.liters, 0.0)
        assertEquals(25.0, r.total!!, 0.0)
        assertEquals(500.0, r.odometerKm!!, 0.0)
        assertEquals("Town", r.city)
        assertEquals("n", r.notes)
        assertEquals("u1", r.uniqueId)
        assertEquals(true, r.full)
    }

    @Test fun acceptsLegacyHeaderSpellings() {
        val text = "\"## Log\"\n\"Data\",\"Odo (km)\",\"Fuel (litres)\",\"Full\"\n\"2025-05-01\",\"1\",\"9\",\"1\"\n"
        val r = FuelioCsv.parse(text).refuels.single()
        assertEquals(9.0, r.liters, 0.0)
    }

    @Test fun milesAndGallonsAreConverted() {
        val text = "\"## Log\"\n\"Date\",\"Odo (mi)\",\"Fuel (us gallons)\",\"Full\"\n\"2025-05-01\",\"100\",\"10\",\"1\"\n"
        val r = FuelioCsv.parse(text).refuels.single()
        assertEquals(160.93, r.odometerKm!!, 0.0)
        assertEquals(37.85, r.liters, 0.0)
    }

    @Test fun stripsBomFromCategoryNames() {
        assertEquals("Tickets/Fines", FuelioCsv.parse(sample).categories[8])
    }

    @Test fun skipsTemplateAndIncomeCosts() {
        val f = FuelioCsv.parse(sample)
        assertEquals(1, f.costs.size)
        val c = f.costs.single()
        assertEquals("Oil change", c.title)
        assertEquals(123.46, c.amount, 0.0)
        assertEquals("with \"filter\"", c.notes)
        assertEquals(1, c.categoryId)
    }

    @Test fun ignoresUnknownSections() {
        val f = FuelioCsv.parse(sample + "\"## Weird\"\n\"a\",\"b\"\n\"1\",\"2\"\n")
        assertEquals(5, f.refuels.size)
    }

    @Test fun emptyCityBecomesNull() {
        val f = FuelioCsv.parse(sample)
        assertNull(f.refuels[0].city)
        assertEquals("Testville", f.refuels[1].city)
    }

    @Test(expected = FuelioFormatException::class)
    fun rejectsNonFuelioText() {
        FuelioCsv.parse("hello,world\n1,2\n")
    }

    @Test fun countsRowsWithUnreadableDateOrAmount() {
        val text = """
            "## Log"
            "Date","Odo (km)","Fuel (l)","Full","Price"
            "2025-01-10","100","30.0","1","60.0"
            "garbage","200","30.0","1","60.0"
            "2025-01-11","300","abc","1","60.0"
            "## Costs"
            "CostTitle","Date","Cost","CostTypeID","isTemplate","isIncome"
            "ok","2025-02-20","10","1","0","0"
            "bad","2025-02-21","x","1","0","0"
            "tpl","nodate","5","1","1","0"
        """.trimIndent()
        val f = FuelioCsv.parse(text)
        assertEquals(1, f.refuels.size)
        assertEquals(1, f.costs.size)
        assertEquals(3, f.skippedRows)
        assertEquals(0, sampleSkipped())
    }

    private fun sampleSkipped() = FuelioCsv.parse(sample).skippedRows
}
