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
        assertTrue("unknown sections ${l.keys}", (l.keys - setOf("imports", "background", "skip")).isEmpty())
        val hooked = l["imports"].orEmpty() + l["background"].orEmpty()
        assertTrue("in [skip] and hooked: ${l["skip"].orEmpty().filter { it in hooked }}", l["skip"].orEmpty().none { it in hooked })
    }

    private val comment = { l: String -> l.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") } }

    /** The lines that are code: no import, no comment line. */
    private fun body(lines: List<String>) = lines.filterNot { it.startsWith("import ") || comment(it) }

    /** A call of [name]: `Name(`, or `Name {` (a trailing lambda), not after a dot or inside a longer name. */
    private fun calls(name: String) = Regex("""(^|[^A-Za-z0-9_.])$name\s*[({]""")

    /** `androidx.compose.material3.FilterChip(`: a fully qualified call bypasses every import. */
    private fun fqnProblems(path: String, lines: List<String>): List<String> {
        val code = body(lines)
        return rawImports.mapNotNull { (name, raw) ->
            val fqn = Regex("""(^|[^A-Za-z0-9_.])${Regex.escape(raw.removePrefix("import "))}\s*[({]""")
            if (code.any { fqn.containsMatchIn(it) }) {
                "$path: fully qualified $name( bypasses the skin import (write com.bydmate.app.ui.skin.$name( // fork: skin)"
            } else {
                null
            }
        }
    }

    /**
     * A swapped shape in a top-level val is made once and keeps the skin of its first use; a getter
     * (`val X get() = RoundedCornerShape(…) // fork: skin, live`) is made at every read.
     */
    private fun frozenProblems(path: String, lines: List<String>): List<String> {
        val shapes = rawImports.filterValues { "foundation.shape" in it }.keys
        val topVal = Regex("""^((private|internal|public|const)\s+)*val\s""")
        return lines.withIndex().filter { (i, l) ->
            val decl = topVal.containsMatchIn(l) && !Regex("""\sget\s*\(\s*\)""").containsMatchIn(l)
            val next = i > 0 && topVal.containsMatchIn(lines[i - 1]) && lines[i - 1].trimEnd().endsWith("=")
            (decl || next) && shapes.any { Regex("""(^|[^A-Za-z0-9_.])$it\s*\(""").containsMatchIn(l) }
        }.map { (i, _) -> "$path:${i + 1}: frozen shape in a top-level val (make it a getter: // fork: skin, live)" }
    }

    /** A star import (material3.* / foundation.shape.*) needs the explicit fork import for each name used. */
    private fun starProblems(path: String, lines: List<String>): List<String> {
        val code = body(lines)
        return rawImports.keys.filter { name ->
            val star = if ("foundation.shape" in rawImports.getValue(name)) {
                "import androidx.compose.foundation.shape.*"
            } else {
                "import androidx.compose.material3.*"
            }
            val used = code.any { calls(name).containsMatchIn(it) }
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
            problems += fqnProblems(path, lines)
            problems += frozenProblems(path, lines)
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

    /**
     * The author's Kotlin files: tools/skin-author-files.txt, generated by `tools/skin-imports.sh --apply`
     * from the upstream base (git merge-base HEAD upstream/main). The tests run without git (CI has no
     * upstream remote), so the list is committed; `--check` fails when it is stale.
     */
    private fun authorFiles(): List<String> {
        val file = File(repo, "tools/skin-author-files.txt")
        assertTrue("missing ${file.path}: run tools/skin-imports.sh --apply", file.isFile)
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    /**
     * The guard of the unlisted author files. An author screen that imports a swapped M3 or shape name
     * (directly, through a star import, as an alias) or calls one fully qualified stays M3 under every
     * skin, and no other test sees it: a new or changed upstream screen after a merge.
     */
    @Test fun everyAuthorFileWithASwappedNameIsListedOrSkipped() {
        val l = list()
        val known = (l["imports"].orEmpty() + l["skip"].orEmpty()).toSet()
        val authors = authorFiles()
        assertTrue("tools/skin-author-files.txt lists no files", authors.size > known.size)
        val problems = mutableListOf<String>()
        authors.filter { it !in known }.forEach { path ->
            val f = File(repo, path)
            if (!f.isFile) return@forEach // the fork removed it
            val lines = f.readLines().map { it.trimEnd() }
            val code = body(lines)
            val found = rawImports.filter { (name, raw) ->
                val star = raw.substringBeforeLast('.') + ".*"
                lines.any { it == raw || it.startsWith("$raw ") } ||
                    (star in lines && code.any { calls(name).containsMatchIn(it) })
            }.keys + fqnProblems(path, lines).map { it.substringAfter("fully qualified ").substringBefore('(') }
            if (found.isNotEmpty()) problems += "$path: ${found.toSortedSet()}"
        }
        if (problems.isNotEmpty()) {
            fail(
                "author files that use swapped names but are not in tools/skin-imports.list: add each to [imports] " +
                    "and run tools/skin-imports.sh --apply (or to [skip], a Compose window outside BYDMateTheme):\n" +
                    problems.joinToString("\n"),
            )
        }
    }

    /**
     * An author declaration named like a swapped name (`fun Card(`) would be shadowed by the explicit
     * skin import in its package, silently. None today; one after a merge needs a look by hand.
     */
    @Test fun noAuthorFileDeclaresASwappedName() {
        val names = rawImports.keys.joinToString("|")
        val decl = Regex("""\bfun\s+(<[^>]*>\s*)?([\w.]+\.)?($names)\s*\(""")
        val problems = authorFiles().mapNotNull { path ->
            val f = File(repo, path)
            if (!f.isFile) return@mapNotNull null
            body(f.readLines()).firstNotNullOfOrNull { decl.find(it)?.groupValues?.get(3) }?.let { "$path: declares $it(" }
        }
        if (problems.isNotEmpty()) fail("review by hand, the skin import would shadow these:\n" + problems.joinToString("\n"))
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
