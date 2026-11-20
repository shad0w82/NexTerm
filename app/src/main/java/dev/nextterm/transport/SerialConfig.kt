package dev.nextterm.transport

import com.hoho.android.usbserial.driver.UsbSerialPort

enum class Parity(val label: String, val short: Char, val code: Int) {
    NONE("None", 'N', UsbSerialPort.PARITY_NONE),
    EVEN("Even", 'E', UsbSerialPort.PARITY_EVEN),
    ODD("Odd", 'O', UsbSerialPort.PARITY_ODD),
    MARK("Mark", 'M', UsbSerialPort.PARITY_MARK),
    SPACE("Space", 'S', UsbSerialPort.PARITY_SPACE),
}

enum class FlowControl(val label: String) { NONE("None"), RTS_CTS("RTS/CTS"), XON_XOFF("XON/XOFF") }

/** What the Enter key sends. */
enum class TxNewline(val label: String, val bytes: ByteArray) {
    CR("CR", byteArrayOf('\r'.code.toByte())),
    LF("LF", byteArrayOf('\n'.code.toByte())),
    CRLF("CR+LF", byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())),
}

data class SerialConfig(
    val baudRate: Int = 115200,
    val dataBits: Int = 8,
    val stopBits: Int = 1, // 1 or 2
    val parity: Parity = Parity.NONE,
    val flowControl: FlowControl = FlowControl.NONE,
    val txNewline: TxNewline = TxNewline.CR,
    /** Display a received bare LF as CR+LF (for devices that only send "\n"). */
    val rxLfAsCrLf: Boolean = false,
    /** Port index for multi-port adapters (e.g. FT2232/FT4232). */
    val portIndex: Int = 0,
) {
    /** Conventional "115200 8N1" notation. */
    val summary: String get() = "$baudRate $dataBits${parity.short}$stopBits"

    companion object {
        val BAUD_PRESETS = listOf(300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600, 1500000)
    }
}
