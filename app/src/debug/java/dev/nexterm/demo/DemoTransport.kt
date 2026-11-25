package dev.nexterm.demo

import android.util.Log
import dev.nexterm.transport.Line
import dev.nexterm.transport.LineStates
import dev.nexterm.transport.SerialConfig
import dev.nexterm.transport.Transport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * A pretend serial device with a tiny shell, for UI work without hardware (debug builds only).
 * Echoes typed characters like a real console, supports Backspace, and has a few commands that
 * exercise the terminal: colours, hex/binary output, a continuous data stream and a full-screen
 * view on the alternate screen (like htop).
 *
 * Everything runs on one scheduler thread, so no locking is needed for the shell state.
 */
class DemoTransport(private val config: SerialConfig, dtr: Boolean = true, rts: Boolean = false) : Transport {

    override val description: String = "NexTerm Demo · ${config.summary}"

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "NexTerm-demo") }
    private val closed = AtomicBoolean(false)
    private var listener: Transport.Listener? = null

    // Control lines: DTR and RTS are ours; the device answers CTS always, DSR when DTR is up, CD and RI never.
    private var dtrOn = dtr
    private var rtsOn = rts
    private fun lineSet() = LineStates(
        on = buildSet { if (dtrOn) { add(Line.DTR); add(Line.DSR) }; if (rtsOn) add(Line.RTS); add(Line.CTS) },
        supported = Line.entries.toSet(),
    )
    private val _lines = MutableStateFlow(lineSet())
    override val lines: StateFlow<LineStates> = _lines.asStateFlow()

    override fun setLine(line: Line, on: Boolean) {
        when (line) {
            Line.DTR -> dtrOn = on
            Line.RTS -> rtsOn = on
            else -> return
        }
        _lines.value = lineSet()
    }

    override fun sendBreak(durationMs: Long) {
        if (closed.get()) return
        exec.execute {
            this.line.clear()
            out("\r\n*** BREAK received ***\r\n"); prompt()
        }
    }

    private val line = StringBuilder()
    private var lastWasCr = false
    /** Like a real device: with echo off nothing typed comes back, so Local echo can be tried. */
    private var deviceEcho = true
    private var stream: ScheduledFuture<*>? = null
    private var top: ScheduledFuture<*>? = null
    private var cols = 80
    private var rows = 24
    private val startedAt = System.currentTimeMillis()

    override fun start(listener: Transport.Listener) {
        this.listener = listener
        boot()
    }

    override fun write(data: ByteArray) {
        if (closed.get()) return
        val copy = data.copyOf()
        exec.execute { copy.forEach { onByte(it.toInt() and 0xFF) } }
    }

    override fun resize(columns: Int, rows: Int) {
        // The view can be resized after the session ended (the bar under it changes), when the executor is gone.
        if (closed.get()) return
        exec.execute { cols = columns; this.rows = rows }
    }

    override fun close() = shutdown(null)

    private fun shutdown(error: Throwable?) {
        if (!closed.compareAndSet(false, true)) return
        exec.shutdownNow()
        listener?.onClosed(error)
    }

    // ---- output helpers -------------------------------------------------------------------

    private fun out(s: String) {
        if (!closed.get()) listener?.onData(s.toByteArray(Charsets.UTF_8))
    }

    private fun outBytes(b: ByteArray) {
        if (!closed.get()) listener?.onData(b)
    }

    private fun println(s: String = "") = out("$s\r\n")
    private fun prompt() = out("\u001b[1;32mdemo\u001b[0m> ")

    private fun later(ms: Long, block: () -> Unit) {
        if (!closed.get()) exec.schedule({ runCatching(block).onFailure { Log.w(TAG, "demo", it) } }, ms, TimeUnit.MILLISECONDS)
    }

    // ---- boot -----------------------------------------------------------------------------

    private fun boot() {
        val lines = listOf(
            "",
            "U-Boot 2024.01 (NexTerm demo board)",
            "",
            "CPU:   Simulated ARM Cortex-A7 @ 1.2 GHz",
            "DRAM:  512 MiB",
            "Serial: ${config.baudRate} baud, ${config.dataBits} data bits, parity ${config.parity.label.lowercase()}, ${config.stopBits} stop bit(s)",
            "Net:   eth0: demo-mac",
        )
        var t = 150L
        lines.forEach { l -> later(t) { println(l) }; t += 120 }
        later(t) { out("Hit any key to stop autoboot:  0\r\n") }
        later(t + 300) {
            println()
            println("Welcome to the \u001b[1mNexTerm demo device\u001b[0m. Type \u001b[36mhelp\u001b[0m for commands.")
            prompt()
        }
    }

    // ---- input handling -------------------------------------------------------------------

    private fun onByte(b: Int) {
        if (top != null) { // full-screen view: q or Ctrl-C leaves it
            if (b == 'q'.code || b == 3) stopTop()
            return
        }
        when (b) {
            '\r'.code -> { lastWasCr = true; enter() }
            '\n'.code -> { if (!lastWasCr) enter(); lastWasCr = false }
            3 -> { // Ctrl-C
                lastWasCr = false
                stopStream()
                line.clear()
                out("^C\r\n"); prompt()
            }
            8, 127 -> { // Backspace / DEL
                lastWasCr = false
                if (line.isNotEmpty()) { line.setLength(line.length - 1); if (deviceEcho) out("\b \b") }
            }
            27 -> lastWasCr = false // ignore escape sequences (arrows etc.) in this simple shell
            else -> {
                lastWasCr = false
                if (b in 32..126) { line.append(b.toChar()); if (deviceEcho) out(b.toChar().toString()) }
            }
        }
    }

    private fun enter() {
        if (deviceEcho) out("\r\n")
        val cmd = line.toString().trim()
        line.clear()
        if (cmd.isNotEmpty()) run(cmd) else prompt()
    }

    private fun run(cmdLine: String) {
        val parts = cmdLine.split(Regex("\\s+"))
        when (parts[0].lowercase()) {
            "help", "?" -> {
                println("Commands:")
                listOf(
                    "help" to "this list",
                    "version" to "firmware and link settings",
                    "uptime" to "time since connect",
                    "echo TEXT" to "print TEXT back",
                    "colors" to "ANSI colour and style test",
                    "hex" to "send a burst of binary bytes (try the Hex view)",
                    "stream" to "sensor readings every 500 ms (Ctrl-C to stop)",
                    "error" to "count one error in the session statistics",
                    "deviceecho on|off" to "make the device stop echoing what you type (tests Local echo)",
                    "lines N" to "print N numbered lines (tests Scrollback)",
                    "top" to "full-screen view on the alternate screen (q to quit)",
                    "lf" to "print lines ending in LF only (tests the LF setting)",
                    "clear" to "clear the screen",
                    "reboot" to "replay the boot log",
                    "exit" to "simulate the device going away",
                    "unplug [S]" to "unplug the adapter for S seconds (default 5), then plug it back in",
                ).forEach { (c, d) -> println("  \u001b[36m%-10s\u001b[0m %s".format(c, d)) }
                prompt()
            }
            "version" -> {
                println("demo-fw 1.4.2 (debug build of NexTerm)")
                println("link: ${config.summary}, flow ${config.flowControl.label}, Enter sends ${config.txNewline.label}")
                prompt()
            }
            "uptime" -> {
                val s = (System.currentTimeMillis() - startedAt) / 1000
                println("up %02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)); prompt()
            }
            "echo" -> { println(cmdLine.substringAfter(' ', "")); prompt() }
            "colors", "colours" -> {
                val names = listOf("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white")
                println(names.indices.joinToString(" ") { "\u001b[3${it}m${names[it]}\u001b[0m" })
                println(names.indices.joinToString(" ") { "\u001b[9${it}m${names[it]}\u001b[0m" })
                println("\u001b[1mbold\u001b[0m \u001b[3mitalic\u001b[0m \u001b[4munderline\u001b[0m \u001b[7mreverse\u001b[0m")
                println((0 until 24).joinToString("") { "\u001b[48;5;${232 + it}m \u001b[0m" })
                prompt()
            }
            "hex" -> {
                // Printable and non-printable bytes mixed, like a binary protocol frame.
                outBytes(byteArrayOf(0x02, 0x10, 0x01, 0x03, 0x00, 0x6B, 0x00, 0x03, 0x74, 0x17, 0x03, 0x0D, 0x0A))
                outBytes("OK len=13\r\n".toByteArray())
                prompt()
            }
            "deviceecho" -> {
                deviceEcho = parts.getOrNull(1)?.lowercase() != "off"
                println("device echo ${if (deviceEcho) "on" else "off"}"); prompt()
            }
            "lines" -> {
                val n = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 100_000) ?: 100
                out((1..n).joinToString("") { "line %05d\r\n".format(it) }); prompt()
            }
            "error" -> { listener?.onError(); println("Counted one error."); prompt() }
            "stream" -> {
                println("Streaming… press Ctrl-C to stop.")
                stream = exec.scheduleWithFixedDelay({
                    val t = 21.0 + Random.nextDouble(-0.5, 0.5)
                    val h = 40.0 + Random.nextDouble(-2.0, 2.0)
                    out("T=%.1fC  RH=%.1f%%  P=%d hPa\r\n".format(t, h, 1012 + Random.nextInt(3)))
                }, 0, 500, TimeUnit.MILLISECONDS)
            }
            "top" -> startTop()
            "lf" -> {
                // Bare LF, no CR: without "Received LF starts a new line" these step diagonally.
                out("line one\nline two\nline three\n\r"); prompt()
            }
            "clear" -> { out("\u001b[2J\u001b[H"); prompt() }
            "reboot" -> { println("Rebooting…"); boot() }
            "exit" -> {
                println("Device going away…")
                later(300) { shutdown(java.io.IOException("demo device closed the link")) }
            }
            "unplug" -> {
                val secs = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 600) ?: 5
                println("Unplugging for $secs s…")
                later(300) {
                    DemoDevices.unplug(secs)
                    shutdown(java.io.IOException("demo adapter unplugged"))
                }
            }
            else -> { println("unknown command: ${parts[0]} (try help)"); prompt() }
        }
    }

    private fun stopStream() {
        stream?.cancel(false)
        stream = null
    }

    // ---- full-screen view (alternate screen) -----------------------------------------------

    private fun startTop() {
        out("\u001b[?1049h\u001b[?25l") // alternate screen, hide cursor
        top = exec.scheduleWithFixedDelay({ drawTop() }, 0, 1, TimeUnit.SECONDS)
    }

    private fun stopTop() {
        top?.cancel(false)
        top = null
        out("\u001b[?25h\u001b[?1049l") // show cursor, back to the main screen (contents restored)
        prompt()
    }

    private fun drawTop() {
        val sb = StringBuilder("\u001b[H\u001b[2J")
        val up = (System.currentTimeMillis() - startedAt) / 1000
        // Pad the visible text only, then wrap it in reverse video, so the bar spans the full width.
        val header = " demo-top  ${cols}x$rows  up ${up}s".padEnd(cols.coerceAtLeast(20))
        sb.append("\u001b[7m$header\u001b[0m\r\n\r\n")
        val procs = listOf("init", "sensord", "netd", "logger", "shell", "watchdog")
        procs.forEachIndexed { i, p ->
            val cpu = Random.nextInt(0, 100)
            val bar = "#".repeat(cpu / 5).padEnd(20, '.')
            val color = when { cpu > 75 -> 31; cpu > 40 -> 33; else -> 32 }
            sb.append("  %-3d %-9s \u001b[%dm%s\u001b[0m %3d%%\r\n".format(i + 1, p, color, bar, cpu))
        }
        sb.append("\r\n  \u001b[2mq to quit · resize or rotate to see the size change\u001b[0m")
        out(sb.toString())
    }

    private companion object { const val TAG = "NexTerm.Demo" }
}
