package dev.nextterm.transport

import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import dev.nextterm.usb.typeLabel
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.hoho.android.usbserial.driver.UsbSerialPort.FlowControl as LibFlow

private const val TAG = "NextTerm.Serial"
private const val WRITE_TIMEOUT_MS = 2000
private const val WRITE_CHUNK = 4096

class SerialTransport private constructor(
    private val driver: UsbSerialDriver,
    private val port: UsbSerialPort,
    val config: SerialConfig,
    private val pendingNotices: List<String>,
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

    override fun start(listener: Transport.Listener) {
        this.listener = listener
        pendingNotices.forEach(listener::onNotice)

        // Reads go through the library's I/O manager. Its writeAsync() has a fixed buffer that
        // throws on overflow (large paste at low baud), so writes use our own queue + thread.
        ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                rxBytes.addAndGet(data.size.toLong())
                listener.onData(data)
            }
            override fun onRunError(e: Exception) = shutdown(e)
        }).also { it.start() }

        writer = Thread({ writeLoop() }, "NextTerm-serial-write").also { it.start() }
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
        runCatching { port.close() }.onFailure { Log.w(TAG, "port close", it) }
        listener?.onClosed(error)
    }

    companion object {
        /**
         * Opens [driver]'s port with [config]. Requires USB permission already granted.
         * Blocking (USB control transfers): call off the main thread.
         */
        @Throws(IOException::class)
        fun open(usbManager: UsbManager, driver: UsbSerialDriver, config: SerialConfig): SerialTransport {
            val device = driver.device
            val port = driver.ports.getOrNull(config.portIndex)
                ?: throw IOException("Port ${config.portIndex} does not exist (device has ${driver.ports.size})")
            val connection = usbManager.openDevice(device)
                ?: throw IOException(
                    if (!usbManager.hasPermission(device)) "USB permission not granted" else "openDevice failed",
                )
            val notices = mutableListOf<String>()
            try {
                port.open(connection)
                val stop = if (config.stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1
                port.setParameters(config.baudRate, config.dataBits, stop, config.parity.code)
                applyFlowControl(port, driver, config.flowControl, notices)
                // Most adapters need DTR/RTS asserted for the far end to talk; harmless otherwise.
                // Leave RTS alone with RTS/CTS because the chip drives it.
                runCatching { port.dtr = true }
                if (config.flowControl != FlowControl.RTS_CTS) runCatching { port.rts = true }
            } catch (e: Exception) {
                runCatching { port.close() }
                throw if (e is IOException) e else IOException(e.message ?: e.toString(), e)
            }
            Log.i(
                TAG,
                "opened ${device.deviceName} %04X:%04X driver=${driver.typeLabel()} port=${config.portIndex} ${config.summary} flow=${config.flowControl}"
                    .format(device.vendorId, device.productId),
            )
            return SerialTransport(driver, port, config, notices)
        }

        /** Apply flow control; if the chip can't do it, log, tell the user, and continue without. */
        private fun applyFlowControl(port: UsbSerialPort, driver: UsbSerialDriver, wanted: FlowControl, notices: MutableList<String>) {
            if (wanted == FlowControl.NONE) return
            val supported = runCatching { port.supportedFlowControl }.getOrDefault(emptySet())
            val candidates = when (wanted) {
                FlowControl.NONE -> emptyList()
                FlowControl.RTS_CTS -> listOf(LibFlow.RTS_CTS)
                // Prefer chip-handled XON/XOFF; fall back to inline (library filters XON/XOFF bytes).
                FlowControl.XON_XOFF -> listOf(LibFlow.XON_XOFF, LibFlow.XON_XOFF_INLINE)
            }
            val choice = candidates.firstOrNull { it in supported }
            if (choice == null) {
                val msg = "${driver.typeLabel()} does not support ${wanted.label} flow control " +
                    "(supports: ${supported.joinToString()}). Continuing without flow control."
                Log.w(TAG, msg)
                notices += msg
                return
            }
            try {
                port.flowControl = choice
                Log.i(TAG, "flow control set to $choice")
            } catch (e: Exception) { // IOException or UnsupportedOperationException
                val msg = "Setting ${wanted.label} flow control failed (${e.message ?: e.javaClass.simpleName}). Continuing without flow control."
                Log.w(TAG, msg, e)
                notices += msg
                runCatching { port.flowControl = LibFlow.NONE }
            }
        }
    }
}
