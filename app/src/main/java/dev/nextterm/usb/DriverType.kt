package dev.nextterm.usb

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver

/** Drivers a user can force onto an unrecognized VID:PID. */
enum class DriverType(val label: String, val driverClass: Class<out UsbSerialDriver>) {
    CH34X("CH34x", Ch34xSerialDriver::class.java),
    FTDI("FTDI", FtdiSerialDriver::class.java),
    CP210X("CP210x", Cp21xxSerialDriver::class.java),
    PL2303("PL2303", ProlificSerialDriver::class.java),
    CDC("CDC-ACM", CdcAcmSerialDriver::class.java);

    companion object {
        fun fromName(name: String?): DriverType? = entries.firstOrNull { it.name == name }
    }
}

/** A USB vendor/product pair. */
data class VidPid(val vid: Int, val pid: Int) {
    override fun toString(): String = "%04X:%04X".format(vid, pid)
    val key: String get() = toString()

    companion object {
        fun parse(key: String): VidPid? {
            val parts = key.split(":")
            if (parts.size != 2) return null
            val v = parts[0].toIntOrNull(16) ?: return null
            val p = parts[1].toIntOrNull(16) ?: return null
            return VidPid(v, p)
        }
    }
}

/** Human label for whatever driver class the prober returned (including built-ins like GSM modem). */
fun UsbSerialDriver.typeLabel(): String =
    DriverType.entries.firstOrNull { it.driverClass == this::class.java }?.label
        ?: this::class.java.simpleName.removeSuffix("SerialDriver")
