package dev.nexterm.demo

import dev.nexterm.transport.SerialConfig
import dev.nexterm.transport.Transport
import dev.nexterm.usb.UsbDeviceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Release builds have no demo device. The debug source set provides the real one. */
object DemoDevices {
    fun entry(): UsbDeviceEntry? = null
    val present: Boolean get() = false
    val changes: Flow<Unit> = emptyFlow()
    fun createTransport(config: SerialConfig, dtr: Boolean, rts: Boolean): Transport = throw UnsupportedOperationException("No demo device in release builds")
}
