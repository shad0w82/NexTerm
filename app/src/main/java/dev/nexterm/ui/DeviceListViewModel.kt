package dev.nexterm.ui

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nexterm.usb.DriverOverrideStore
import dev.nexterm.usb.DriverType
import dev.nexterm.usb.UsbDeviceEntry
import dev.nexterm.usb.UsbDeviceMonitor
import dev.nexterm.usb.VidPid
import dev.nexterm.usb.enumerate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class DeviceListViewModel(app: Application) : AndroidViewModel(app) {
    private val usbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager
    private val overrideStore = DriverOverrideStore(app)
    private val monitor = UsbDeviceMonitor(app) { refresh() }

    private val _devices = MutableStateFlow<List<UsbDeviceEntry>>(emptyList())
    val devices: StateFlow<List<UsbDeviceEntry>> = _devices.asStateFlow()
    val overrides = overrideStore.overrides

    init {
        monitor.start()
        // First emission skipped: the activity refreshes in onResume anyway (avoids a double probe at start).
        viewModelScope.launch { overrideStore.overrides.drop(1).collect { refresh() } }
        // The debug demo device has no USB broadcasts of its own; it announces when it is "unplugged" or "plugged in".
        viewModelScope.launch { dev.nexterm.demo.DemoDevices.changes.collect { refresh() } }
    }

    fun refresh() {
        _devices.value = enumerate(usbManager, overrideStore.overrides.value)
    }

    fun setOverride(vidPid: VidPid, driver: DriverType?) = overrideStore.set(vidPid, driver)

    override fun onCleared() {
        monitor.stop()
    }
}
