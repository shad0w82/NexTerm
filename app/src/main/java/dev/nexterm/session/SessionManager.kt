package dev.nexterm.session

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.termux.terminal.TerminalSession
import dev.nexterm.settings.SettingsStore
import dev.nexterm.transport.Line
import dev.nexterm.transport.LineStates
import dev.nexterm.transport.SerialConfig
import dev.nexterm.transport.SerialTransport
import dev.nexterm.transport.Transport
import dev.nexterm.transport.TxNewline
import dev.nexterm.usb.DriverType
import dev.nexterm.usb.VidPid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "NexTerm.Session"
/** Room the Trace log gets per line of scrollback (an average line is well under this). */
private const val BYTES_PER_LINE = 100
const val BREAK_MS = 100L
private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

/** What to connect to: enough to find the device again after it is re-plugged. */
data class ConnectTarget(
    val devicePath: String?,
    val vidPid: VidPid,
    val config: SerialConfig,
    val profileId: String? = null,
    val profileName: String? = null,
    /** Driver forced for this connection; null = use the probed driver. */
    val driverOverride: DriverType? = null,
)

/** The terminal's size in character cells. */
data class TermSize(val cols: Int, val rows: Int)

/** One terminal bound to one transport. */
class ActiveSession(
    val transport: Transport,
    val terminal: TerminalSession,
    /** The target as actually connected (devicePath is the resolved path). */
    val target: ConnectTarget,
    /** Bytes, errors and the traffic history of this session. */
    val stats: SessionStats = SessionStats(),
    /** Every message that crossed the link: the Trace and Hex views. */
    val log: ByteLog = ByteLog(),
    /** The one way out for bytes: counts them, logs them and hands them to the transport. */
    private val sender: (ByteArray, Boolean) -> Unit = { bytes, _ -> transport.write(bytes) },
    private val _size: MutableStateFlow<TermSize> = MutableStateFlow(TermSize(0, 0)),
) {
    /** Columns and rows of the terminal as it is now; follows rotation, the keyboard and the text size. */
    val size: StateFlow<TermSize> = _size.asStateFlow()

    /** What the send field in Trace adds after the text; starts as the profile's setting and applies to this session only. */
    private val _lineEnding = MutableStateFlow(target.config.txNewline)
    val lineEnding: StateFlow<TxNewline> = _lineEnding.asStateFlow()

    fun cycleLineEnding() {
        val all = TxNewline.values()
        _lineEnding.value = all[(_lineEnding.value.ordinal + 1) % all.size]
    }

    /** The control lines (DTR, RTS, CTS, DSR, CD, RI) and which of them the adapter supports. */
    val lines: StateFlow<LineStates> get() = transport.lines

    /** Asserts or releases DTR or RTS, and notes it in the byte log like the mockup's Trace does. */
    fun setLine(line: Line, on: Boolean) {
        if (!_connected.value) return
        transport.setLine(line, on)
        log.addNote("${line.name} ${if (on) "on" else "off"}")
    }

    fun sendBreak() {
        if (!_connected.value) return
        transport.sendBreak(BREAK_MS)
        log.addNote("BREAK sent ($BREAK_MS ms)")
    }

    /**
     * Sends [bytes] as they are (the Trace and Hex send field, saved commands). With [echo] and Local echo on in
     * Settings the text is also drawn on the console. Ignored once the session has ended.
     */
    fun send(bytes: ByteArray, echo: Boolean = false) {
        if (_connected.value && bytes.isNotEmpty()) sender(bytes, echo)
    }

    internal val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    internal val _awaitingAdapter = MutableStateFlow(false)
    /**
     * True while the session has ended because the adapter was unplugged and Auto-reconnect is waiting for it to
     * come back. The foreground service stays alive for as long as this is true.
     */
    val awaitingAdapter: StateFlow<Boolean> = _awaitingAdapter.asStateFlow()
    internal val _endReason = MutableStateFlow<String?>(null)
    /** Why the session ended when it was not the user's doing (adapter unplugged, link error); null otherwise. */
    val endReason: StateFlow<String?> = _endReason.asStateFlow()
    val title: String get() = target.profileName?.let { "$it · ${transport.description}" } ?: transport.description
}

/**
 * Process-wide owner of the (single, in v1) session. The foreground service keeps the process
 * alive while a session is connected; this object holds the actual state so the UI can come and
 * go. Multiple sessions later = turn [active] into a list.
 */
object SessionManager {
    val clientProxy = SessionClientProxy()

    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    /** Opens a USB serial port and runs a session on it. */
    suspend fun connectSerial(
        context: Context,
        driver: UsbSerialDriver,
        target: ConnectTarget,
        notices: List<String> = emptyList(),
    ): Result<ActiveSession> {
        val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val path = driver.device.deviceName
        return connect(
            context, target.copy(devicePath = path), notices,
            // The library reports a pulled adapter as a failed USB request; check whether it's gone.
            isGone = { usbManager.deviceList.values.none { it.deviceName == path } },
        ) {
            val initial = SettingsStore.get(context).settings.value
            SerialTransport.open(usbManager, driver, target.config, initial.dtr, initial.rts)
        }
    }

    /**
     * Opens a transport (off main) via [open], builds the terminal session (on main), and starts
     * the foreground service. Transport-agnostic: serial today, demo in debug builds, SSH later.
     */
    suspend fun connect(
        context: Context,
        target: ConnectTarget,
        notices: List<String> = emptyList(),
        isGone: () -> Boolean = { false },
        open: () -> Transport,
    ): Result<ActiveSession> {
        val config = target.config
        val app = context.applicationContext
        // Close any current link, but keep the old (disconnected) terminal on screen until the new
        // session replaces it, so a failed Reconnect doesn't throw the user out of the terminal.
        disconnect()

        val transport = withContext(Dispatchers.IO) { runCatching(open) }.getOrElse {
            Log.e(TAG, "open failed", it)
            return Result.failure(it)
        }

        val newline = config.txNewline
        val settings = SettingsStore.get(app).settings
        // Scrollback sets how many lines the console and Trace keep; it applies from the next connection.
        val scrollback = settings.value.scrollback
        val stats = SessionStats()
        val log = ByteLog(maxEntries = scrollback, maxBytes = scrollback * BYTES_PER_LINE)
        lateinit var terminal: TerminalSession
        val send: (ByteArray, Boolean) -> Unit = { bytes, echo ->
            stats.addTx(bytes.size)
            log.addTx(bytes)
            transport.write(bytes)
            // Local echo is read every time, so the switch in Settings works during a session too.
            if (echo && settings.value.localEcho) {
                val shown = LocalEcho.render(bytes)
                if (shown.isNotEmpty()) terminal.feed(shown, 0, shown.size)
            }
        }
        terminal = TerminalSession(scrollback, clientProxy) { data, off, len ->
            send(translateNewlines(data, off, len, newline), true)
        }
        terminal.setNewlineIsCrLf(config.rxLfAsCrLf)
        val size = MutableStateFlow(TermSize(terminal.emulator.mColumns, terminal.emulator.mRows))
        terminal.setSizeListener { cols, rows ->
            size.value = TermSize(cols, rows)
            transport.resize(cols, rows)
        }

        val session = ActiveSession(transport, terminal, target, stats, log, send, size)
        _active.value = session

        log.addNote("Connected: ${session.title}")
        terminal.appendLocalMessage("\u001b[2m[NexTerm] Connected: ${session.title}\u001b[0m\r\n")
        notices.forEach {
            log.addNote(it)
            terminal.appendLocalMessage("\u001b[33m[NexTerm] $it\u001b[0m\r\n")
        }
        transport.start(object : Transport.Listener {
            override fun onData(data: ByteArray) {
                stats.addRx(data.size)
                log.addRx(data)
                terminal.feed(data, 0, data.size)
            }
            override fun onError() = stats.addError()
            override fun onClosed(error: Throwable?) {
                stats.end()
                // An end with an error may be an unplug. Raise the flag before "connected" drops, so the service never
                // sees a moment with neither and stops; the check below takes it back if the adapter is still there.
                if (error != null && settings.value.autoReconnect) session._awaitingAdapter.value = true
                session._connected.value = false
                // A read error can beat the USB DETACHED event by a few ms, so decide a bit later.
                mainHandler.postDelayed({
                    val gone = error != null && isGone()
                    if (!gone) session._awaitingAdapter.value = false
                    val reason = when {
                        error == null -> null
                        gone -> "adapter unplugged"
                        else -> error.message ?: error.javaClass.simpleName
                    }
                    Log.i(TAG, "session closed: ${reason ?: "by user"}")
                    log.addNote(if (reason == null) "Disconnected" else "Disconnected: $reason")
                    if (reason != null) {
                        val device = transport.description.substringBefore(" · ")
                        session._endReason.value =
                            if (gone) "The $device adapter was unplugged." else "The connection was lost: $reason."
                    }
                    terminal.close(reason)
                }, if (error == null) 0L else 500L)
            }
            override fun onNotice(message: String) {
                // Called from the opener thread or the writer thread; emulator access must be on main.
                log.addNote(message)
                mainHandler.post {
                    terminal.appendLocalMessage("\u001b[33m[NexTerm] $message\u001b[0m\r\n")
                }
            }
        })

        ContextCompat.startForegroundService(app, Intent(app, SessionService::class.java))
        return Result.success(session)
    }

    /** Close the transport; the terminal stays visible with a "[Disconnected]" line. */
    fun disconnect() {
        _active.value?.transport?.close()
    }

    /** Forget the session entirely (after disconnect). */
    fun dismiss() {
        val s = _active.value ?: return
        if (s.connected.value) s.transport.close()
        s._awaitingAdapter.value = false
        _active.value = null
    }

    /** Stop waiting for the adapter to come back; the ended session stays on screen. */
    fun stopWaiting() {
        _active.value?._awaitingAdapter?.value = false
    }

    private fun translateNewlines(data: ByteArray, off: Int, len: Int, mode: TxNewline): ByteArray {
        // The view and paste() turn every newline into CR, so mapping CR covers Enter and pastes.
        if (mode == TxNewline.CR) return data.copyOfRange(off, off + len)
        val out = java.io.ByteArrayOutputStream(len + 8)
        for (i in off until off + len) {
            val b = data[i]
            if (b == '\r'.code.toByte()) out.write(mode.bytes) else out.write(b.toInt())
        }
        return out.toByteArray()
    }
}
