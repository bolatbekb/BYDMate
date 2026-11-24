package com.bydmate.app.fork.naviprobe

/**
 * Screen text as the trace journal can keep it. The journal replaces any non-ASCII value with
 * `<text>` (TraceSanitizer), and this probe exists to read the navigator's Russian text, so the
 * text is transliterated first (Cyrillic, Kazakh and Belarusian letters), Unicode spaces become
 * plain spaces, any other character becomes a `\uXXXX` escape. A VIN is masked. At most
 * [MAX_CHARS] chars, a cut one ends with `~`.
 */
internal object AsciiText {
    const val MAX_CHARS = 80
    private const val CUT_MARK = "~"
    // Reading stops here: a long text is cut anyway, and its tail costs nothing.
    private const val READ_LIMIT = MAX_CHARS * 2
    private val VIN = Regex("""\b[A-HJ-NPR-Z0-9]{17}\b""")

    fun encode(value: CharSequence?, max: Int = MAX_CHARS): String? {
        if (value.isNullOrEmpty()) return null
        val sb = StringBuilder(minOf(value.length, READ_LIMIT) + 8)
        for (ch in value) {
            if (sb.length >= READ_LIMIT) break
            append(sb, ch)
        }
        val text = VIN.replace(sb.toString().trim(), "<vin>")
        if (text.isEmpty()) return null
        return if (text.length <= max) text else text.take(max - CUT_MARK.length) + CUT_MARK
    }

    private fun append(sb: StringBuilder, ch: Char) {
        when {
            ch.code in PRINTABLE -> sb.append(ch)
            Character.isWhitespace(ch) || Character.isSpaceChar(ch) -> sb.append(' ')
            else -> {
                val latin = LETTERS[ch.lowercaseChar()]
                when {
                    latin == null -> sb.append("\\u").append(ch.code.toString(HEX).padStart(4, '0'))
                    ch.isUpperCase() -> sb.append(latin.replaceFirstChar { it.uppercaseChar() })
                    else -> sb.append(latin)
                }
            }
        }
    }

    private val PRINTABLE = 0x20..0x7E
    private const val HEX = 16

    private val LETTERS: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "",
        'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        // Kazakh
        'ә' to "ae", 'ғ' to "gh", 'қ' to "q", 'ң' to "ng", 'ө' to "oe", 'ұ' to "u", 'ү' to "ue",
        'һ' to "h", 'і' to "i",
        // Belarusian, Ukrainian
        'ў' to "w", 'є' to "ye", 'ї' to "yi", 'ґ' to "g",
    )
}
