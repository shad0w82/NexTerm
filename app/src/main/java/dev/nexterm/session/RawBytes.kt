package dev.nexterm.session

import java.io.ByteArrayOutputStream

/** What [parseRaw] made of a typed line: the bytes, or what is wrong with it (short enough for one line of the editor). */
class RawParse(val bytes: ByteArray?, val error: String?)

private const val ERR_PAIRS = "Use hex pairs like 01 03, or \"text\" in quotes."
private const val ERR_QUOTE = "A quote is not closed."
private const val ERR_ESCAPE = "Unknown escape. Use \\r \\n \\t \\\\ or \\\"."
private const val ERR_EMPTY = "Enter bytes, or \"text\" in quotes."

/**
 * Reads a line typed in "raw" mode: hex digits in pairs (any spacing, any case) mixed with text in quotes, like
 * `01 03 "OK" 0D 0A`. Both " and ' open a quote and the same mark closes it; inside, the text goes out as UTF-8
 * and `\r \n \t \\ \" \'` stand for the special characters. Nothing is added, so the bytes are exactly those written.
 */
fun parseRaw(text: String): RawParse {
    val out = ByteArrayOutputStream()
    val digits = StringBuilder()
    fun fail(message: String) = RawParse(null, message)
    // Turns the hex digits collected so far into bytes; false when they do not come in pairs.
    fun flush(): Boolean {
        if (digits.length % 2 != 0) return false
        var i = 0
        while (i < digits.length) {
            out.write((Character.digit(digits[i], 16) shl 4) or Character.digit(digits[i + 1], 16))
            i += 2
        }
        digits.setLength(0)
        return true
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c.isWhitespace() -> i++
            c == '"' || c == '\'' -> {
                if (!flush()) return fail(ERR_PAIRS)
                i++
                val quoted = StringBuilder()
                var closed = false
                while (i < text.length) {
                    val d = text[i]
                    if (d == c) { closed = true; i++; break }
                    if (d == '\\') {
                        val e = text.getOrNull(i + 1)
                        quoted.append(
                            when (e) {
                                'r' -> '\r'
                                'n' -> '\n'
                                't' -> '\t'
                                '\\', '"', '\'' -> e
                                else -> return fail(ERR_ESCAPE)
                            },
                        )
                        i += 2
                    } else {
                        quoted.append(d)
                        i++
                    }
                }
                if (!closed) return fail(ERR_QUOTE)
                out.write(quoted.toString().toByteArray(Charsets.UTF_8))
            }
            Character.digit(c, 16) >= 0 -> { digits.append(c); i++ }
            else -> return fail(ERR_PAIRS)
        }
    }
    if (!flush()) return fail(ERR_PAIRS)
    return if (out.size() == 0) fail(ERR_EMPTY) else RawParse(out.toByteArray(), null)
}
