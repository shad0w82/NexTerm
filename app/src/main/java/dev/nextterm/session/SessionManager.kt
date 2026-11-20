package dev.nextterm.session

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.termux.terminal.TerminalSession
import dev.nextterm.transport.SerialConfig
import dev.nextterm.transport.SerialTransport
import dev.nextterm.transport.Transport
import dev.nextterm.transport.TxNewline
import dev.nextterm.usb.VidPid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "NextTerm.Session"
private const val TRANSCRIPT_ROWS = 5000
private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

/** What to connect to: enough to find the device again after it is re-plugged. */
data class ConnectTarget(
    val devicePath: String?,
    val vidPid: VidPid,
    val config: SerialConfig,
    val profileId: String? = null,
    val profileName: String? = null,
)

/** One terminal bound to one transport. */
class ActiveSession(
    val transport: Transport,
    val terminal: TerminalSession,
    /** The target as actually connected (devicePath is the resolved path). */
    val target: ConnectTarget,
) {
    internal val _connected = MutableStateFlow(true)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
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

    /** Opens the port (off main), builds the terminal session (on main), starts the service. */
    suspend fun connectSerial(
        context: Context,
        driver: UsbSerialDriver,
        target: ConnectTarget,
        notices: List<String> = emptyList(),
    ): Result<ActiveSession> {
        val config = target.config
        val app = context.applicationContext
        val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager
        // Close any current link, but keep the old (disconnected) terminal on screen until the new
        // session replaces it, so a failed Reconnect doesn't throw the user out of the terminal.
        disconnect()

        val transport = withContext(Dispatchers.IO) {
            runCatching { SerialTransport.open(usbManager, driver, config) }
        }.getOrElse {
            Log.e(TAG, "open failed", it)
            return Result.failure(it)
        }

        val newline = config.txNewline
        val terminal = TerminalSession(TRANSCRIPT_ROWS, clientProxy) { data, off, len ->
            transport.write(translateNewlines(data, off, len, newline))
        }
        terminal.setNewlineIsCrLf(config.rxLfAsCrLf)
        terminal.setSizeListener { cols, rows -> transport.resize(cols, rows) }

        val session = ActiveSession(transport, terminal, target.copy(devicePath = driver.device.deviceName))
        _active.value = session

        terminal.appendLocalMessage("\u001b[2m[NextTerm] Connected: ${session.title}\u001b[0m\r\n")
        notices.forEach { terminal.appendLocalMessage("\u001b[33m[NextTerm] $it\u001b[0m\r\n") }
        transport.start(object : Transport.Listener {
            override fun onData(data: ByteArray) = terminal.feed(data, 0, data.size)
            override fun onClosed(error: Throwable?) {
                session._connected.value = false
                // The library reports a pulled adapter as a failed USB request, and the read error
                // can beat the DETACHED event by a few ms, so look at the device list a bit later.
                mainHandler.postDelayed({
                    val reason = when {
                        error == null -> null
                        usbManager.deviceList.values.none { it.deviceName == driver.device.deviceName } -> "adapter unplugged"
                        else -> error.message ?: error.javaClass.simpleName
                    }
                    Log.i(TAG, "session closed: ${reason ?: "by user"}")
                    terminal.close(reason)
                }, if (error == null) 0L else 500L)
            }
            override fun onNotice(message: String) {
                // Called from the opener thread or the writer thread; emulator access must be on main.
                mainHandler.post {
                    terminal.appendLocalMessage("\u001b[33m[NextTerm] $message\u001b[0m\r\n")
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
        _active.value = null
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
