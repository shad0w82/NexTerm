package dev.nexterm.demo

import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.nexterm.transport.SerialConfig
import dev.nexterm.transport.Transport
import dev.nexterm.usb.DriverSource
import dev.nexterm.usb.UsbDeviceEntry
import dev.nexterm.usb.VidPid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Debug builds only: a simulated serial device so the UI can be exercised without hardware
 * (the Android emulator has no USB host). Not present in release builds, and not shown on a real phone.
 */
object DemoDevices {
    const val PATH = "demo://nexterm"
    val VID_PID = VidPid(0xDE00, 0x0001)

    /** False while the demo device is "unplugged" (the demo command `unplug`): it leaves the device list and comes back later. */
    @Volatile var present = true
        private set

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits whenever the device appears or disappears, so the device list refreshes like it does on a real USB event. */
    val changes: Flow<Unit> = _changes

    /** The demo device is only for emulators (they have no USB host); a real phone running a debug build never shows it. */
    private val onEmulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.startsWith("generic")

    fun entry(): UsbDeviceEntry? = if (!present || !onEmulator) null else UsbDeviceEntry(
        usbDevice = null,
        driver = null,
        source = DriverSource.DEMO,
        hasPermission = true,
        vidPid = VID_PID,
        path = PATH,
        productName = "NexTerm Demo",
        manufacturerName = "Simulated device",
    )

    /** Takes the device away now and plugs it back in after [seconds]. */
    fun unplug(seconds: Int) {
        present = false
        _changes.tryEmit(Unit)
        Handler(Looper.getMainLooper()).postDelayed({
            present = true
            _changes.tryEmit(Unit)
        }, seconds * 1000L)
    }

    fun createTransport(config: SerialConfig, dtr: Boolean, rts: Boolean): Transport = DemoTransport(config, dtr, rts)
}
