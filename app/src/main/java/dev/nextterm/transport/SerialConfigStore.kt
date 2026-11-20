package dev.nextterm.transport

import android.content.Context
import androidx.core.content.edit

/** Remembers the last-used connection parameters. Named profiles will build on this later. */
class SerialConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("last_serial_config", Context.MODE_PRIVATE)

    fun load(): SerialConfig {
        val d = SerialConfig()
        return SerialConfig(
            baudRate = prefs.getInt("baud", d.baudRate),
            dataBits = prefs.getInt("dataBits", d.dataBits),
            stopBits = prefs.getInt("stopBits", d.stopBits),
            parity = runCatching { Parity.valueOf(prefs.getString("parity", null)!!) }.getOrDefault(d.parity),
            flowControl = runCatching { FlowControl.valueOf(prefs.getString("flow", null)!!) }.getOrDefault(d.flowControl),
            txNewline = runCatching { TxNewline.valueOf(prefs.getString("txNewline", null)!!) }.getOrDefault(d.txNewline),
            rxLfAsCrLf = prefs.getBoolean("rxLfAsCrLf", d.rxLfAsCrLf),
            portIndex = 0,
        )
    }

    fun save(c: SerialConfig) = prefs.edit {
        putInt("baud", c.baudRate)
        putInt("dataBits", c.dataBits)
        putInt("stopBits", c.stopBits)
        putString("parity", c.parity.name)
        putString("flow", c.flowControl.name)
        putString("txNewline", c.txNewline.name)
        putBoolean("rxLfAsCrLf", c.rxLfAsCrLf)
    }
}
