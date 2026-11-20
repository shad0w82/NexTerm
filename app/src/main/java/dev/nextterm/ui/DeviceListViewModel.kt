package dev.nextterm.ui

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nextterm.usb.DriverOverrideStore
import dev.nextterm.usb.DriverType
import dev.nextterm.usb.UsbDeviceEntry
import dev.nextterm.usb.UsbDeviceMonitor
import dev.nextterm.usb.VidPid
import dev.nextterm.usb.enumerate
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
    }

    fun refresh() {
        _devices.value = enumerate(usbManager, overrideStore.overrides.value)
    }

    fun setOverride(vidPid: VidPid, driver: DriverType?) = overrideStore.set(vidPid, driver)

    override fun onCleared() {
        monitor.stop()
    }
}
