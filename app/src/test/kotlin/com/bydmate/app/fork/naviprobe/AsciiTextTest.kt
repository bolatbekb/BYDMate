package com.bydmate.app.fork.naviprobe

import com.bydmate.app.diagnostics.TraceSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsciiTextTest {

    @Test fun `ascii passes as is`() {
        assertEquals("Turn right in 300 m", AsciiText.encode("Turn right in 300 m"))
    }

    @Test fun `cyrillic is transliterated, case kept`() {
        assertEquals("Cherez 350 m povorot napravo", AsciiText.encode("Через 350 м поворот направо"))
        assertEquals("ul. Shchorsa", AsciiText.encode("ул. Щорса"))
        assertEquals("Qazaq koeshesi", AsciiText.encode("Қазақ көшесі"))
    }

    @Test fun `non-breaking and thin spaces become plain spaces`() {
        assertEquals("350 m", AsciiText.encode("350 м"))
        assertEquals("1,2 km", AsciiText.encode("1,2 км"))
    }

    @Test fun `other non-ascii is escaped, never dropped`() {
        assertEquals("A\\u2192B", AsciiText.encode("A→B"))
    }

    @Test fun `null, empty and blank are absent`() {
        assertNull(AsciiText.encode(null))
        assertNull(AsciiText.encode(""))
        assertNull(AsciiText.encode(" \n "))
    }

    @Test fun `long text is capped at 80 chars with a cut mark`() {
        val out = AsciiText.encode("щ".repeat(100))!!
        assertEquals(AsciiText.MAX_CHARS, out.length)
        assertTrue(out.endsWith("~"))
    }

    @Test fun `a vin is masked`() {
        assertEquals("VIN <vin>", AsciiText.encode("VIN LGXCE4CB0P0123456"))
    }

    @Test fun `encoded text survives the trace sanitizer as text, not as a placeholder`() {
        val value = AsciiText.encode("Через 350 м поворот на ул. Ленина")!!
        assertEquals("Cherez_350_m_povorot_na_ul._Lenina", TraceSanitizer.value(value))
        assertEquals("0,0,2560,1440", TraceSanitizer.value("0,0,2560,1440"))
    }

    @Test fun `every output char is printable ascii`() {
        val out = AsciiText.encode("Ёжик\t→ 5 км 🚗")!!
        assertTrue(out, out.all { it.code in 0x20..0x7E })
    }
}
