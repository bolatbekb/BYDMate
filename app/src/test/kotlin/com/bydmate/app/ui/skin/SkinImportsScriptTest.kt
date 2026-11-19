package com.bydmate.app.ui.skin

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs tools/skin-imports.sh on a scratch copy of the repo layout: star imports, aliases, idempotency. */
class SkinImportsScriptTest {
    @get:Rule val tmp = TemporaryFolder()
    private val repo = File("..").canonicalFile

    private fun scratch(files: Map<String, String>, list: String): File {
        val root = tmp.newFolder()
        File(root, "tools").mkdirs()
        File(repo, "tools/skin-imports.sh").copyTo(File(root, "tools/skin-imports.sh"))
        File(root, "tools/skin-imports.list").writeText(list)
        files.forEach { (p, t) -> File(root, p).apply { parentFile!!.mkdirs(); writeText(t) } }
        return root
    }

    private fun run(root: File, mode: String): Pair<Int, String> {
        val p = ProcessBuilder("bash", "tools/skin-imports.sh", mode).directory(root).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return p.waitFor() to out
    }

    private val star = """
        package a
        import androidx.compose.material3.*
        import androidx.compose.foundation.shape.*
        import androidx.compose.runtime.Composable

        @Composable fun X() {
            // Slider( in a comment does not count
            Card(shape = RoundedCornerShape(8.dp)) { ElevatedCard() {} }
            Switch(checked = true, onCheckedChange = {})
        }
    """.trimIndent() + "\n"

    private val alias = """
        package a
        import androidx.compose.material3.Card as M3Card
        import androidx.compose.material3.Switch
        fun Y() { M3Card() {}; Switch() }
    """.trimIndent() + "\n"

    private val list = "[imports]\nS.kt\nA.kt\n"

    @Test fun starFileGetsExplicitImportsForUsedNamesOnly() {
        val root = scratch(mapOf("S.kt" to star, "A.kt" to alias), list)
        assertEquals(1, run(root, "--check").first)
        run(root, "--apply")
        val text = File(root, "S.kt").readText()
        val lines = text.lines()
        assertTrue("import com.bydmate.app.ui.skin.Card // fork: skin" in lines)
        assertTrue("import com.bydmate.app.ui.skin.Switch // fork: skin" in lines)
        assertTrue("import com.bydmate.app.ui.skin.RoundedCornerShape // fork: skin" in lines)
        assertFalse(text.contains("skin.Slider"))
        assertFalse(text.contains("skin.AlertDialog"))
        assertTrue("star import stays", "import androidx.compose.material3.*" in lines)
        // added after the last import line
        assertEquals("import androidx.compose.runtime.Composable", lines[lines.indexOfFirst { it.startsWith("import com.bydmate") } - 1])
    }

    @Test fun applyIsIdempotent() {
        val root = scratch(mapOf("S.kt" to star, "A.kt" to alias), list)
        run(root, "--apply")
        val once = File(root, "S.kt").readText() to File(root, "A.kt").readText()
        val second = run(root, "--apply")
        assertTrue(second.second, second.second.contains("0 file(s) rewritten"))
        assertEquals(once, File(root, "S.kt").readText() to File(root, "A.kt").readText())
    }

    @Test fun aliasIsReportedAndNeverTouched() {
        val root = scratch(mapOf("A.kt" to alias), "[imports]\nA.kt\n")
        run(root, "--apply")
        val text = File(root, "A.kt").readText()
        assertTrue(text.contains("import androidx.compose.material3.Card as M3Card"))
        assertTrue(text.contains("import com.bydmate.app.ui.skin.Switch // fork: skin"))
        val (code, out) = run(root, "--check")
        assertEquals(out, 1, code)
        assertTrue(out, out.contains("alias, review by hand"))
    }

    /** `Card { … }` is a call too: a trailing lambda without parentheses. */
    @Test fun starFileTrailingLambdaCallGetsTheImport() {
        val lambda = "package a\nimport androidx.compose.material3.*\n\nfun Z() {\n    Card {\n    }\n}\n"
        val root = scratch(mapOf("L.kt" to lambda), "[imports]\nL.kt\n")
        assertEquals(1, run(root, "--check").first)
        run(root, "--apply")
        assertTrue("import com.bydmate.app.ui.skin.Card // fork: skin" in File(root, "L.kt").readLines())
        assertEquals(0, run(root, "--check").first)
    }

    @Test fun fullyQualifiedCallIsReportedAndNeverTouched() {
        val fqn = "package a\nimport com.bydmate.app.ui.skin.Text // fork: skin\n\nfun F() {\n" +
            "    androidx.compose.material3.FilterChip(selected = true, onClick = {}, label = { Text(\"x\") })\n" +
            "    androidx.compose.material3.FilterChipDefaults.filterChipColors()\n}\n"
        val root = scratch(mapOf("F.kt" to fqn), "[imports]\nF.kt\n")
        run(root, "--apply")
        assertEquals(fqn, File(root, "F.kt").readText())
        val (code, out) = run(root, "--check")
        assertEquals(out, 1, code)
        assertTrue(out, out.contains("F.kt:5  fully qualified, review by hand: FilterChip("))
        assertFalse(out, out.contains("FilterChipDefaults"))
        // the marked rewrite passes
        File(root, "F.kt").writeText(fqn.replace("androidx.compose.material3.FilterChip(", "com.bydmate.app.ui.skin.FilterChip( // fork: skin\n       "))
        assertEquals(0, run(root, "--check").first)
    }

    @Test fun frozenTopLevelShapeIsReported() {
        fun file(decl: String) = "package a\nimport com.bydmate.app.ui.skin.RoundedCornerShape // fork: skin\n\n$decl\n" +
            "object O {\n    val IN_OBJECT = RoundedCornerShape(4.dp)\n}\n\nfun G() {\n    val local = RoundedCornerShape(2.dp)\n}\n"
        val root = scratch(mapOf("G.kt" to file("private val SHAPE = RoundedCornerShape(12.dp)")), "[imports]\nG.kt\n")
        val (code, out) = run(root, "--check")
        assertEquals(out, 1, code)
        assertTrue(out, out.contains("G.kt:4  frozen shape"))
        assertTrue(out, out.contains("G.kt:6  frozen shape"))
        assertFalse("a local val is made per call", out.contains("G.kt:10"))
        File(root, "G.kt").writeText(
            file("private val SHAPE get() = RoundedCornerShape(12.dp) // fork: skin, live")
                .replace("val IN_OBJECT = ", "val IN_OBJECT get() = "),
        )
        val after = run(root, "--check")
        assertEquals(after.second, 0, after.first)
    }

    @Test fun skipSectionIsIgnoredButMustNameRealUnhookedFiles() {
        val raw = "package a\nimport androidx.compose.material3.Text\n"
        val root = scratch(mapOf("S.kt" to star, "W.kt" to raw), "[imports]\nS.kt\n\n[skip]\nW.kt\n")
        run(root, "--apply")
        assertEquals("a skipped file is never rewritten", raw, File(root, "W.kt").readText())
        val ok = run(root, "--check")
        assertEquals(ok.second, 0, ok.first)
        assertTrue(ok.second, ok.second.contains("1 skipped"))
        File(root, "tools/skin-imports.list").writeText("[imports]\nS.kt\n\n[skip]\nS.kt\nGone.kt\n")
        val (code, out) = run(root, "--check")
        assertEquals(out, 1, code)
        assertTrue(out, out.contains("S.kt  in [skip] and also hooked"))
        assertTrue(out, out.contains("Gone.kt  missing (in [skip])"))
    }

    @Test fun starFileAfterApplyPassesCheck() {
        val root = scratch(mapOf("S.kt" to star), "[imports]\nS.kt\n")
        run(root, "--apply")
        val (code, out) = run(root, "--check")
        assertEquals(out, 0, code)
    }
}
