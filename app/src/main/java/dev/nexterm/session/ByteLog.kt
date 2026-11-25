package dev.nexterm.session

import android.os.SystemClock

/** Which way a message went. */
enum class Dir { RX, TX }

/**
 * One message in the [ByteLog]: some bytes that went one way, or (when [dir] is null) a short note from the
 * app itself, like "Connected". Immutable, so a snapshot can be shown while more data arrives.
 */
class LogEntry(
    /** Never changes for a message, even while more bytes are added to it; lets a list keep its place. */
    val id: Long,
    /** Milliseconds since the session started, when the first byte of this message went by. */
    val t: Long,
    val dir: Dir?,
    val bytes: ByteArray,
    val note: String = "",
)

/**
 * Everything that crossed the link, as messages: the data behind the Trace and Hex views. A message is a run of
 * bytes in one direction; a line feed ends it, and so does a pause, so a line that the adapter hands over in
 * several pieces is still one message. Old messages drop off the front when the log gets long. Safe to call from
 * any thread (the receive thread writes, the main thread reads).
 */
class ByteLog(
    /** Messages kept; older ones drop off the front (the Scrollback setting). */
    private val maxEntries: Int = 1000,
    /** Bytes kept in all, so a stream of long binary messages cannot fill the memory. */
    private val maxBytes: Int = 128 * 1024,
) {
    private val startedAt = SystemClock.elapsedRealtime()
    private val entries = ArrayDeque<LogEntry>()
    private var lastByteAt = 0L
    private var totalBytes = 0
    private var nextId = 0L

    /** Changes with every addition, so a view can tell when there is something new to show. */
    @Volatile var version = 0L
        private set

    fun addRx(data: ByteArray) = add(Dir.RX, data)

    fun addTx(data: ByteArray) = add(Dir.TX, data)

    @Synchronized
    fun addNote(text: String) {
        entries.addLast(LogEntry(nextId++, elapsed(), null, EMPTY, text))
        trim()
        version++
    }

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    /**
     * Adds what one read or one write handed over. A chunk with several lines becomes several messages (cut after each
     * LF, or after a CR that is not followed by LF); the last piece stays open and joins the next chunk if that comes soon.
     */
    @Synchronized
    private fun add(dir: Dir, data: ByteArray) {
        if (data.isEmpty()) return
        val now = elapsed()
        var start = 0
        for (i in data.indices) {
            val b = data[i]
            if (b == LF || (b == CR && i + 1 < data.size && data[i + 1] != LF)) {
                addPiece(dir, data, start, i + 1, now)
                start = i + 1
            }
        }
        if (start < data.size) addPiece(dir, data, start, data.size, now)
        lastByteAt = now
        trim()
        version++
    }

    /** One piece of a chunk: joins the message still open from before, or starts a new one (never longer than [MAX_MESSAGE]). */
    private fun addPiece(dir: Dir, data: ByteArray, from: Int, to: Int, now: Long) {
        var f = from
        while (f < to) {
            val end = minOf(to, f + MAX_MESSAGE)
            val piece = data.copyOfRange(f, end)
            val last = entries.lastOrNull()
            if (last != null && last.dir == dir && now - lastByteAt <= JOIN_MS && !endsMessage(last.bytes, piece) &&
                last.bytes.size + piece.size <= MAX_MESSAGE
            ) {
                entries[entries.lastIndex] = LogEntry(last.id, last.t, dir, last.bytes + piece)
            } else {
                entries.addLast(LogEntry(nextId++, now, dir, piece))
            }
            totalBytes += piece.size
            f = end
        }
    }

    private fun trim() {
        while (entries.size > maxEntries || (totalBytes > maxBytes && entries.size > 1)) {
            totalBytes -= entries.removeFirst().bytes.size
        }
    }

    /** A message is over once it ends in LF, or in CR unless the next piece starts with the LF of a CR+LF. */
    private fun endsMessage(last: ByteArray, next: ByteArray): Boolean {
        val b = last.lastOrNull() ?: return false
        return b == LF || (b == CR && next.first() != LF)
    }

    private fun elapsed() = SystemClock.elapsedRealtime() - startedAt

    private companion object {
        const val CR: Byte = 13
        const val LF: Byte = 10
        val EMPTY = ByteArray(0)

        /** Bytes of the same direction closer together than this belong to one message. */
        const val JOIN_MS = 250L
        const val MAX_MESSAGE = 4096
    }
}
