package dev.nexterm.session

import java.io.ByteArrayOutputStream

/**
 * What to draw on the console for bytes that were just sent, when Local echo is on: the text itself, a new line for
 * Enter (CR, LF or CR+LF count as one), the cursor stepping back for Backspace. Cursor keys, Ctrl combinations and
 * other control bytes show nothing.
 */
object LocalEcho {
    private val NEW_LINE = byteArrayOf(13, 10)
    private val ERASE = byteArrayOf(8, 32, 8)

    fun render(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 8)
        var afterCr = false
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == 0x1B -> { i = skipEscape(bytes, i); afterCr = false; continue }
                b == 0x0D -> { out.write(NEW_LINE); afterCr = true; i++; continue }
                b == 0x0A -> if (!afterCr) out.write(NEW_LINE)
                b == 0x08 || b == 0x7F -> out.write(ERASE)
                b == 0x09 || b >= 0x20 -> out.write(b)
                // other control bytes (Ctrl-C and the like): nothing to show
            }
            afterCr = false
            i++
        }
        return out.toByteArray()
    }

    /** Index after the escape sequence that starts at [from]: ESC [ params final, ESC O x, or ESC and one byte (Alt+key). */
    private fun skipEscape(bytes: ByteArray, from: Int): Int {
        var j = from + 1
        if (j >= bytes.size) return j
        val c = bytes[j].toInt().toChar()
        if (c == '[' || c == 'O') {
            j++
            while (j < bytes.size && (bytes[j].toInt() and 0xFF) in 0x30..0x3F) j++
            if (j < bytes.size && (bytes[j].toInt() and 0xFF) in 0x40..0x7E) j++
        } else {
            j++
        }
        return j
    }
}
