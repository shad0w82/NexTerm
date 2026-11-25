package dev.nexterm.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawBytesTest {
    private fun ok(text: String, vararg expected: Int) {
        val r = parseRaw(text)
        assertNull("'$text' should parse, got: ${r.error}", r.error)
        assertArrayEquals("'$text'", ByteArray(expected.size) { expected[it].toByte() }, r.bytes)
    }

    private fun bad(text: String, error: String? = null) {
        val r = parseRaw(text)
        assertNull("'$text' should not parse", r.bytes)
        if (error != null) assertEquals(error, r.error)
    }

    @Test fun hexAsBefore() {
        ok("01 03 00 6B", 0x01, 0x03, 0x00, 0x6B)
        ok("0103006b", 0x01, 0x03, 0x00, 0x6B)
        ok("  ff  0A ", 0xFF, 0x0A)
    }

    @Test fun hexErrors() {
        bad("")
        bad("   ")
        bad("0")
        bad("01 0")
        bad("0G")
        bad("hello")
    }

    @Test fun textInQuotes() {
        ok("\"OK\"", 0x4F, 0x4B)
        ok("'OK'", 0x4F, 0x4B)
        ok("01 03 \"OK\" 0D 0A", 0x01, 0x03, 0x4F, 0x4B, 0x0D, 0x0A)
        ok("\"OK\"0D", 0x4F, 0x4B, 0x0D)
        ok("0D\"OK\"", 0x0D, 0x4F, 0x4B)
        ok("\"A\" \"B\"", 0x41, 0x42)
        ok("\"+++\"", 0x2B, 0x2B, 0x2B)
    }

    @Test fun otherQuoteInsideIsPlainText() {
        ok("\"it's\"", 'i'.code, 't'.code, '\''.code, 's'.code)
        ok("'say \"hi\"'", *"say \"hi\"".map { it.code }.toIntArray())
    }

    @Test fun escapes() {
        ok("\"AT\\r\\n\"", 0x41, 0x54, 0x0D, 0x0A)
        ok("\"a\\tb\"", 0x61, 0x09, 0x62)
        ok("\"\\\\\"", 0x5C)
        ok("\"\\\"\"", 0x22)
        ok("'\\''", 0x27)
        bad("\"a\\xb\"", "Unknown escape. Use \\r \\n \\t \\\\ or \\\".")
        bad("\"a\\")
    }

    @Test fun utf8() {
        ok("\"é\"", 0xC3, 0xA9)
        ok("\"€\"", 0xE2, 0x82, 0xAC)
        ok("\"😀\"", 0xF0, 0x9F, 0x98, 0x80)
    }

    @Test fun quoteErrors() {
        bad("\"OK", "A quote is not closed.")
        bad("'OK\"", "A quote is not closed.")
        bad("0\"OK\"")
        bad("\"\"", "Enter bytes, or \"text\" in quotes.")
        bad("01 \"OK\" 0")
    }
}
