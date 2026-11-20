package dev.nextterm.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

private const val TAG = "NextTerm.Probe"

/** Extra VID:PIDs the stock table misses. 1A86:7522 is a CH340 variant (stock only has 7523/5523). */
val EXTRA_PRODUCTS: Map<VidPid, DriverType> = mapOf(
    VidPid(0x1A86, 0x7522) to DriverType.CH34X,
)

enum class DriverSource { DEFAULT_TABLE, CUSTOM_TABLE, USER_OVERRIDE, NONE }

data class UsbDeviceEntry(
    val device: UsbDevice,
    val driver: UsbSerialDriver?,
    val source: DriverSource,
    val hasPermission: Boolean,
) {
    val vidPid = VidPid(device.vendorId, device.productId)
    val path: String get() = device.deviceName
    val productName: String? get() = runCatching { device.productName }.getOrNull()
    val manufacturerName: String? get() = runCatching { device.manufacturerName }.getOrNull()
}

/**
 * Builds the prober used everywhere in the app: library default table, then our extra
 * products, then user overrides. addProduct() replaces existing keys, so later layers win.
 */
fun buildProber(overrides: Map<VidPid, DriverType>): UsbSerialProber {
    val table: ProbeTable = UsbSerialProber.getDefaultProbeTable()
    EXTRA_PRODUCTS.forEach { (vp, dt) -> table.addProduct(vp.vid, vp.pid, dt.driverClass) }
    overrides.forEach { (vp, dt) -> table.addProduct(vp.vid, vp.pid, dt.driverClass) }
    return UsbSerialProber(table)
}

/** Enumerates every attached USB device (serial or not) so unrecognized ones can be overridden. */
fun enumerate(usbManager: UsbManager, overrides: Map<VidPid, DriverType>): List<UsbDeviceEntry> {
    val prober = buildProber(overrides)
    return usbManager.deviceList.values
        .sortedBy { it.deviceName }
        .map { dev ->
            val vp = VidPid(dev.vendorId, dev.productId)
            val driver = runCatching { prober.probeDevice(dev) }
                .onFailure { Log.w(TAG, "probe failed for ${dev.deviceName} $vp", it) }
                .getOrNull()
            val source = when {
                driver == null -> DriverSource.NONE
                vp in overrides -> DriverSource.USER_OVERRIDE
                vp in EXTRA_PRODUCTS -> DriverSource.CUSTOM_TABLE
                else -> DriverSource.DEFAULT_TABLE
            }
            UsbDeviceEntry(dev, driver, source, usbManager.hasPermission(dev)).also {
                Log.i(
                    TAG,
                    "device ${dev.deviceName} vid:pid=$vp name=${it.productName} " +
                        "driver=${driver?.typeLabel() ?: "none"} source=$source ports=${driver?.ports?.size ?: 0}",
                )
            }
        }
}

