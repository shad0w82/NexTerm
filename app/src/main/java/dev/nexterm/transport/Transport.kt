package dev.nexterm.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The modem control lines of a serial port: DTR and RTS are ours to set, the other four are the device's answer. */
enum class Line { DTR, RTS, CTS, DSR, CD, RI }

/** Which lines are asserted right now and which of them this adapter can handle at all. */
data class LineStates(val on: Set<Line> = emptySet(), val supported: Set<Line> = emptySet())

private val NO_LINES: StateFlow<LineStates> = MutableStateFlow(LineStates())

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

    /** The control lines; empty and unsupported for transports that have none (SSH, Telnet). */
    val lines: StateFlow<LineStates> get() = NO_LINES

    /** Assert or release DTR or RTS. May block on USB, so it runs off the caller's thread. */
    fun setLine(line: Line, on: Boolean) {}

    /** Hold a BREAK condition on the line for [durationMs], then release it. Returns at once. */
    fun sendBreak(durationMs: Long = 100) {}

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
        /** Something went wrong but the link carries on (a write timed out, say): counted in the session's Errors. */
        fun onError() {}
    }
}
