package dev.nexterm.transport

import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.hoho.android.usbserial.util.XonXoffFilter
import dev.nexterm.usb.typeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.hoho.android.usbserial.driver.UsbSerialPort.ControlLine as LibLine
import com.hoho.android.usbserial.driver.UsbSerialPort.FlowControl as LibFlow

private const val TAG = "NexTerm.Serial"
private const val WRITE_TIMEOUT_MS = 2000
private const val WRITE_CHUNK = 4096
private const val LINE_POLL_MS = 250L

class SerialTransport private constructor(
    private val driver: UsbSerialDriver,
    private val port: UsbSerialPort,
    val config: SerialConfig,
    private val pendingNotices: List<String>,
    /**
     * With XON_XOFF_INLINE (FTDI, PL2303) the chip honours XOFF for sending, but the XON/XOFF
     * bytes still arrive in the RX stream; this strips them and tracks the paused state.
     */
    private val xonFilter: XonXoffFilter?,
) : Transport {

    override val description: String =
        "${runCatching { driver.device.productName }.getOrNull() ?: driver.typeLabel()} · ${config.summary}"

    private val closed = AtomicBoolean(false)
    private val rxBytes = AtomicLong()
    private val txBytes = AtomicLong()
    private var ioManager: SerialInputOutputManager? = null
    private val writeQueue = LinkedBlockingQueue<ByteArray>()
    private var writer: Thread? = null
    private var listener: Transport.Listener? = null

    // ---- control lines ----
    private val supportedLines: Set<Line> = runCatching { port.supportedControlLines.mapNotNull(::fromLib).toSet() }.getOrDefault(emptySet())
    private val _lines = MutableStateFlow(LineStates(readLines(), supportedLines))
    override val lines: StateFlow<LineStates> = _lines.asStateFlow()
    /** USB control requests (set a line, BREAK) go through one thread so they never overlap or block the UI. */
    private val lineExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "NexTerm-serial-lines") }
    private var poller: Thread? = null

    private fun fromLib(l: LibLine): Line? = when (l) {
        LibLine.DTR -> Line.DTR
        LibLine.RTS -> Line.RTS
        LibLine.CTS -> Line.CTS
        LibLine.DSR -> Line.DSR
        LibLine.CD -> Line.CD
        LibLine.RI -> Line.RI
        else -> null
    }

    private fun readLines(): Set<Line> = runCatching { port.controlLines.mapNotNull(::fromLib).toSet() }.getOrDefault(emptySet())

    override fun setLine(line: Line, on: Boolean) {
        if (closed.get() || line !in supportedLines) return
        lineExecutor.execute {
            try {
                when (line) {
                    Line.DTR -> port.dtr = on
                    Line.RTS -> port.rts = on
                    else -> return@execute
                }
                _lines.value = _lines.value.copy(on = if (on) _lines.value.on + line else _lines.value.on - line)
            } catch (e: Exception) {
                Log.w(TAG, "set $line failed", e)
                listener?.onNotice("Could not change $line: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun sendBreak(durationMs: Long) {
        if (closed.get()) return
        lineExecutor.execute {
            try {
                port.setBreak(true)
                Thread.sleep(durationMs)
                port.setBreak(false)
            } catch (_: InterruptedException) {
                runCatching { port.setBreak(false) }
            } catch (e: Exception) {
                Log.w(TAG, "BREAK failed", e)
                listener?.onNotice("BREAK failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Only adapters that report inputs (CTS, DSR, CD, RI) need watching; DTR and RTS are tracked as we set them. */
    private fun pollLines() {
        try {
            while (!closed.get()) {
                Thread.sleep(LINE_POLL_MS)
                if (closed.get()) break
                val now = readLines()
                // Keep what we last set for the two outputs if the adapter can't read them back.
                val merged = now.filter { it != Line.DTR && it != Line.RTS }.toMutableSet().apply {
                    _lines.value.on.filterTo(this) { (it == Line.DTR || it == Line.RTS) }
                }
                if (merged != _lines.value.on) _lines.value = _lines.value.copy(on = merged)
            }
        } catch (_: InterruptedException) {
        }
    }

    override fun start(listener: Transport.Listener) {
        this.listener = listener
        pendingNotices.forEach(listener::onNotice)

        // Reads go through the library's I/O manager. Its writeAsync() has a fixed buffer that
        // throws on overflow (large paste at low baud), so writes use our own queue + thread.
        ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                rxBytes.addAndGet(data.size.toLong())
                val payload = xonFilter?.let { f ->
                    val wasOn = f.xon
                    f.filter(data).also { if (f.xon != wasOn) Log.i(TAG, if (f.xon) "XON: sending resumed" else "XOFF: device paused sending") }
                } ?: data
                if (payload.isNotEmpty()) listener.onData(payload)
            }
            override fun onRunError(e: Exception) = shutdown(e)
        }).also { it.start() }

        writer = Thread({ writeLoop() }, "NexTerm-serial-write").also { it.start() }
        if (supportedLines.any { it != Line.DTR && it != Line.RTS }) {
            poller = Thread({ pollLines() }, "NexTerm-serial-poll").also { it.start() }
        }
    }

    private fun writeLoop() {
        try {
            while (!closed.get()) {
                val data = writeQueue.take()
                if (closed.get()) break
                var off = 0
                while (off < data.size) {
                    val n = minOf(WRITE_CHUNK, data.size - off)
                    val chunk = if (off == 0 && n == data.size) data else data.copyOfRange(off, off + n)
                    try {
                        port.write(chunk, WRITE_TIMEOUT_MS)
                    } catch (e: SerialTimeoutException) {
                        // Typically RTS/CTS holding us off. Drop the rest of this write and keep going.
                        Log.w(TAG, "write timeout after ${e.bytesTransferred}/${chunk.size} bytes", e)
                        listener?.onError()
                        listener?.onNotice("TX timeout (${e.bytesTransferred}/${chunk.size} bytes sent). Is flow control holding the line?")
                        break
                    }
                    txBytes.addAndGet(n.toLong())
                    off += n
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: IOException) {
            shutdown(e)
        }
    }

    override fun write(data: ByteArray) {
        if (!closed.get()) writeQueue.offer(data)
    }

    override fun close() = shutdown(null)

    private fun shutdown(error: Throwable?) {
        if (!closed.compareAndSet(false, true)) return
        Log.i(TAG, "closing ${port.device.deviceName} rx=${rxBytes.get()}B tx=${txBytes.get()}B" + (error?.let { " due to: $it" } ?: " (user disconnect)"))
        ioManager?.listener = null
        ioManager?.stop()
        writer?.interrupt()
        poller?.interrupt()
        lineExecutor.shutdownNow()
        runCatching { port.close() }.onFailure { Log.w(TAG, "port close", it) }
        listener?.onClosed(error)
    }

    companion object {
        /**
         * Opens [driver]'s port with [config]. Requires USB permission already granted.
         * Blocking (USB control transfers): call off the main thread.
         */
        @Throws(IOException::class)
        fun open(
            usbManager: UsbManager, driver: UsbSerialDriver, config: SerialConfig,
            /** DTR and RTS as they are when the port opens (Settings, Connection). */
            dtr: Boolean = true, rts: Boolean = false,
        ): SerialTransport {
            val device = driver.device
            val port = driver.ports.getOrNull(config.portIndex)
                ?: throw IOException("Port ${config.portIndex} does not exist (device has ${driver.ports.size})")
            val connection = usbManager.openDevice(device)
                ?: throw IOException(
                    if (!usbManager.hasPermission(device)) "USB permission not granted" else "openDevice failed",
                )
            val notices = mutableListOf<String>()
            var applied = LibFlow.NONE
            try {
                port.open(connection)
                val stop = if (config.stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1
                port.setParameters(config.baudRate, config.dataBits, stop, config.parity.code)
                applied = applyFlowControl(port, driver, config.flowControl, notices)
                // The user decides where DTR and RTS start (some boards reset when they change).
                // Leave RTS alone with RTS/CTS because the chip drives it.
                runCatching { port.dtr = dtr }
                if (config.flowControl != FlowControl.RTS_CTS) runCatching { port.rts = rts }
            } catch (e: Exception) {
                runCatching { port.close() }
                throw if (e is IOException) e else IOException(e.message ?: e.toString(), e)
            }
            Log.i(
                TAG,
                "opened ${device.deviceName} %04X:%04X driver=${driver.typeLabel()} port=${config.portIndex} ${config.summary} flow=${config.flowControl}"
                    .format(device.vendorId, device.productId),
            )
            return SerialTransport(driver, port, config, notices, if (applied == LibFlow.XON_XOFF_INLINE) XonXoffFilter() else null)
        }

        /** Apply flow control; if the chip can't do it, log, tell the user, and continue without. */
        /** Returns the library mode actually in effect. */
        private fun applyFlowControl(port: UsbSerialPort, driver: UsbSerialDriver, wanted: FlowControl, notices: MutableList<String>): LibFlow {
            if (wanted == FlowControl.NONE) return LibFlow.NONE
            val supported = runCatching { port.supportedFlowControl }.getOrDefault(emptySet())
            val candidates = when (wanted) {
                FlowControl.NONE -> emptyList()
                FlowControl.RTS_CTS -> listOf(LibFlow.RTS_CTS)
                // Prefer chip-handled XON/XOFF; fall back to inline, where we strip XON/XOFF from RX ourselves (xonFilter).
                FlowControl.XON_XOFF -> listOf(LibFlow.XON_XOFF, LibFlow.XON_XOFF_INLINE)
            }
            val choice = candidates.firstOrNull { it in supported }
            if (choice == null) {
                val msg = "${driver.typeLabel()} does not support ${wanted.label} flow control " +
                    "(supports: ${supported.joinToString()}). Continuing without flow control."
                Log.w(TAG, msg)
                notices += msg
                return LibFlow.NONE
            }
            try {
                port.flowControl = choice
                Log.i(TAG, "flow control set to $choice")
                return choice
            } catch (e: Exception) { // IOException or UnsupportedOperationException
                val msg = "Setting ${wanted.label} flow control failed (${e.message ?: e.javaClass.simpleName}). Continuing without flow control."
                Log.w(TAG, msg, e)
                notices += msg
                runCatching { port.flowControl = LibFlow.NONE }
                return LibFlow.NONE
            }
        }
    }
}
