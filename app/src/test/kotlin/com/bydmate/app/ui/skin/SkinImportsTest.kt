package com.bydmate.app.ui.skin

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The skin hooks in the author files, as tools/skin-imports.list names them. After an upstream
 * merge `tools/skin-imports.sh --apply` restores them; this test fails when one is missing.
 */
class SkinImportsTest {
    private val repo = File("..").canonicalFile
    // The swapped names are read from tools/skin-imports.sh (SKIN_M3_NAMES / SKIN_SHAPE_NAMES): one list.
    private val rawImports: Map<String, String> = run {
        val script = File(repo, "tools/skin-imports.sh").readText()
        fun names(v: String) = Regex("""(?m)^$v="([^"]*)"""").find(script)!!.groupValues[1].split(' ').filter { it.isNotEmpty() }
        names("SKIN_M3_NAMES").associateWith { "import androidx.compose.material3.$it" } +
            names("SKIN_SHAPE_NAMES").associateWith { "import androidx.compose.foundation.shape.$it" }
    }
    private val rawBackground = "background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))"

    /** section name -> paths from the repo root; `#` starts a note. */
    private fun list(): Map<String, List<String>> {
        val file = File(repo, "tools/skin-imports.list")
        assertTrue("missing ${file.path}", file.isFile)
        val sections = linkedMapOf<String, MutableList<String>>()
        var section = ""
        file.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { line ->
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.removeSurrounding("[", "]")
            } else {
                sections.getOrPut(section) { mutableListOf() } += line
            }
        }
        return sections
    }

    @Test fun theListHasBothSections() {
        val l = list()
        assertTrue("no [imports] entries", l["imports"].orEmpty().isNotEmpty())
        assertTrue("no [background] entries", l["background"].orEmpty().isNotEmpty())
        assertTrue("unknown sections ${l.keys}", (l.keys - setOf("imports", "background")).isEmpty())
    }

    /** A star import (material3.* / foundation.shape.*) needs the explicit fork import for each name used. */
    private fun starProblems(path: String, lines: List<String>): List<String> {
        val comment = { l: String -> l.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") } }
        val body = lines.filterNot { it.startsWith("import ") || comment(it) }
        return rawImports.keys.filter { name ->
            val star = if ("foundation.shape" in rawImports.getValue(name)) {
                "import androidx.compose.foundation.shape.*"
            } else {
                "import androidx.compose.material3.*"
            }
            val used = body.any { Regex("""(^|[^A-Za-z0-9_.])$name\s*\(""").containsMatchIn(it) }
            star in lines && used && "import com.bydmate.app.ui.skin.$name // fork: skin" !in lines
        }.map { "$path: star import, $it( used without the explicit skin import" }
    }

    @Test fun everyListedFileUsesTheSkinImports() {
        val problems = mutableListOf<String>()
        list()["imports"].orEmpty().forEach { path ->
            val f = File(repo, path)
            if (!f.isFile) { problems += "$path: missing"; return@forEach }
            val lines = f.readLines().map { it.trimEnd() }
            rawImports.forEach { (name, raw) ->
                if (raw in lines) problems += "$path: raw import of $name"
            }
            problems += starProblems(path, lines)
            if (lines.any { l -> rawImports.values.any { Regex("^" + Regex.escape(it) + "\\s+as\\s").containsMatchIn(l) } }) {
                problems += "$path: alias import, review by hand"
            }
            if (rawImports.keys.none { "import com.bydmate.app.ui.skin.$it // fork: skin" in lines }) {
                problems += "$path: no skin import at all (drop it from the list?)"
            }
        }
        if (problems.isNotEmpty()) fail("run tools/skin-imports.sh --apply:\n" + problems.joinToString("\n"))
    }

    @Test fun everyListedScreenDrawsTheSkinBackground() {
        val problems = mutableListOf<String>()
        list()["background"].orEmpty().forEach { path ->
            val f = File(repo, path)
            if (!f.isFile) { problems += "$path: missing"; return@forEach }
            val text = f.readText()
            if (rawBackground in text) problems += "$path: raw background gradient"
            if (".skinBackground() // fork: skin background" !in text) problems += "$path: no skinBackground hook"
            if ("import com.bydmate.app.ui.skin.skinBackground // fork: skin" !in text) {
                problems += "$path: no skinBackground import"
            }
        }
        if (problems.isNotEmpty()) fail("run tools/skin-imports.sh --apply:\n" + problems.joinToString("\n"))
    }

    /** A hook added by hand must be listed too, or the next upstream merge loses it silently. */
    @Test fun everyHookedFileIsListed() {
        val l = list()
        val hook = Regex("""^import com\.bydmate\.app\.ui\.skin\.(\w+) // fork: skin$""")
        val problems = mutableListOf<String>()
        File(repo, "app/src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val path = f.relativeTo(repo).invariantSeparatorsPath
            val lines = f.readLines()
            val names = lines.mapNotNull { hook.find(it.trim())?.groupValues?.get(1) }.toSet()
            if ((names intersect rawImports.keys).isNotEmpty() && path !in l["imports"].orEmpty()) {
                problems += "$path: imports ${names intersect rawImports.keys} but is not in [imports]"
            }
            if ("skinBackground" in names && path !in l["background"].orEmpty()) {
                problems += "$path: draws skinBackground but is not in [background]"
            }
        }
        if (problems.isNotEmpty()) fail("add to tools/skin-imports.list:\n" + problems.joinToString("\n"))
    }
}
