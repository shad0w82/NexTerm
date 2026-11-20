package dev.nextterm.transport

/**
 * A byte stream the terminal talks to. Serial is the only implementation in v1; SSH and Telnet
 * are expected to implement this same interface later.
 */
interface Transport {
    /** Short human description, e.g. "USB Serial · 115200 8N1". */
    val description: String

    /** Start delivering data. Callbacks may arrive on any thread. */
    fun start(listener: Listener)

    /** Queue bytes for sending. Must not block the caller. */
    fun write(data: ByteArray)

    /** Terminal size changed. No-op for serial (no window-size concept); SSH will send a pty resize. */
    fun resize(columns: Int, rows: Int) {}

    /** Close and release resources. Idempotent. */
    fun close()

    interface Listener {
        fun onData(data: ByteArray)
        /** Transport ended. [error] is null on a normal close. */
        fun onClosed(error: Throwable?)
        /** Informational message worth showing to the user (e.g. a setting the hardware rejected). */
        fun onNotice(message: String)
    }
}
