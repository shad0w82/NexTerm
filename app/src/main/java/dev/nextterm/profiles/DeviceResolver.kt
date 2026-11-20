package dev.nextterm.profiles

import android.util.Log
import dev.nextterm.usb.UsbDeviceEntry
import dev.nextterm.usb.VidPid

/** Where a saved connection should go now. */
sealed interface Resolution {
    data class Exact(val entry: UsbDeviceEntry) : Resolution
    /** The remembered path is gone (re-plugged adapter gets a new path); matched by VID:PID instead. */
    data class Fallback(val entry: UsbDeviceEntry, val previousPath: String?, val candidates: Int) : Resolution {
        val notice: String get() =
            "${previousPath ?: "(no saved path)"} not found; using ${entry.path} (same VID:PID ${entry.vidPid})" +
                if (candidates > 1) ". $candidates devices match; picked the first." else ""
    }
    data object Missing : Resolution
}

/**
 * Exact path first, but only if the device there still has the same VID:PID (paths are reused by
 * other devices after re-plugging). Otherwise the first attached device with a driver and the same
 * VID:PID.
 */
fun resolveDevice(path: String?, vidPid: VidPid, devices: List<UsbDeviceEntry>, label: String): Resolution {
    val exact = devices.firstOrNull { it.path == path && it.vidPid == vidPid && it.driver != null }
    if (exact != null) {
        Log.i(TAG, "$label: exact match at ${exact.path}")
        return Resolution.Exact(exact)
    }
    val matches = devices.filter { it.vidPid == vidPid && it.driver != null }
    if (matches.isEmpty()) {
        Log.w(TAG, "$label: no attached device with VID:PID $vidPid (wanted path $path)")
        return Resolution.Missing
    }
    return Resolution.Fallback(matches.first(), path, matches.size).also {
        Log.w(TAG, "$label: ${it.notice}")
    }
}

private const val TAG = "NextTerm.Profile"
