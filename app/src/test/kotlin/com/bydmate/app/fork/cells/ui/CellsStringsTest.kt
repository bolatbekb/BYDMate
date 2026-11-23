package com.bydmate.app.fork.cells.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** strings_fork_cells.xml: the same keys and format arguments in all six languages. */
class CellsStringsTest {
    private val locales = listOf("values", "values-en", "values-zh", "values-pt", "values-pl", "values-be")
    private val entry = Regex("""<string name="([a-z0-9_]+)"[^>]*>([^<]*)</string>""")
    private val arg = Regex("""%(\d+\$)?[sd]""")

    private fun strings(dir: String): Map<String, String> {
        val res = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")
        val file = File(res, "$dir/strings_fork_cells.xml")
        assertTrue("$file is missing", file.isFile)
        return entry.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun sameKeysAndArgumentsInAllSixLanguages() {
        val ru = strings("values")
        assertTrue("the pattern matched nothing", "fork_cells_settings_header" in ru)
        assertTrue("keys are fork_cells_*", ru.keys.all { it.startsWith("fork_cells_") })
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

    @Test fun noForbiddenWords() {
        val bad = listOf("dm-i", "dm-o")
        for (dir in locales) {
            for ((key, text) in strings(dir)) {
                val low = text.lowercase()
                assertTrue("$dir/$key", bad.none { it in low })
            }
        }
    }
}
