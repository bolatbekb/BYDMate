package com.bydmate.app.ui.home.graphite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** strings_graphite.xml: the same keys and format arguments in all six languages; ru stays as it was. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GraphiteStringsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private val locales = listOf("values", "values-en", "values-zh", "values-pt", "values-pl", "values-be")
    private val entry = Regex("""<string name="([a-z0-9_]+)"[^>]*>([^<]*)</string>""")
    private val arg = Regex("""%(\d+\$)?[sd]""")

    private fun strings(dir: String): Map<String, String> {
        val res = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")
        val file = File(res, "$dir/strings_graphite.xml")
        assertTrue("$file is missing", file.isFile)
        return entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun sameKeysAndArgumentsInAllSixLanguages() {
        val ru = strings("values")
        assertTrue("the pattern matched nothing", "home_gr_energy_title" in ru)
        assertTrue("keys are home_gr_*", ru.keys.all { it.startsWith("home_gr_") })
        for (dir in locales) {
            val tr = strings(dir)
            assertEquals("$dir keys", ru.keys, tr.keys)
            for ((key, text) in tr) {
                assertTrue("$dir/$key is empty", text.isNotBlank())
                assertEquals("$dir/$key arguments", arg.findAll(ru.getValue(key)).map { it.value }.toList(), arg.findAll(text).map { it.value }.toList())
            }
        }
    }

    @Test fun onlyRussianAndBelarusianUseCyrillic() {
        for (dir in listOf("values-en", "values-zh", "values-pt", "values-pl")) {
            for ((key, text) in strings(dir)) assertFalse("$dir/$key: $text", text.any { it in 'Ѐ'..'ӿ' })
        }
    }

    @Test @Config(qualifiers = "ru")
    fun russianIsAsBefore() {
        assertEquals("Расход энергии", ctx.getString(R.string.home_gr_energy_title))
        assertEquals("Нет поездок за период", ctx.getString(R.string.home_gr_energy_empty))
        assertEquals("68% на электротяге", ctx.getString(R.string.home_gr_energy_ev_share, "68"))
        assertEquals("Одометр 12 345 км", ctx.getString(R.string.home_gr_odometer, "12 345 км"))
        assertEquals("До 100%", ctx.getString(R.string.home_gr_footer_to_full))
        assertEquals("Текущая поездка", ctx.getString(R.string.home_gr_trip_current))
        assertEquals("Последняя поездка", ctx.getString(R.string.home_gr_trip_last))
        assertEquals(
            "Цвета главной Graphite; остальное приложение — по «Оформлению»",
            ctx.getString(R.string.home_gr_set_scheme_desc),
        )
    }

    @Test @Config(qualifiers = "en")
    fun englishReadsEnglish() {
        assertEquals("Energy use", ctx.getString(R.string.home_gr_energy_title))
        assertEquals("68% electric", ctx.getString(R.string.home_gr_energy_ev_share, "68"))
        assertEquals("100% in", ctx.getString(R.string.home_gr_footer_to_full))
        assertEquals("Current trip", ctx.getString(R.string.home_gr_trip_current))
        assertEquals("Last trip", ctx.getString(R.string.home_gr_trip_last))
        assertEquals("Day", ctx.getString(R.string.home_gr_period_day))
    }
}
