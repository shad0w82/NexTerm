package dev.nextterm.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Runtime USB permission. Without a USB_DEVICE_ATTACHED manifest filter Android can't remember
 * the grant, so it lasts until the adapter is unplugged (or the app restarts) and is asked again after.
 */
suspend fun requestUsbPermission(context: Context, device: UsbDevice): Boolean {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    if (usbManager.hasPermission(device)) return true
    val action = "${context.packageName}.USB_PERMISSION"
    return suspendCancellableCoroutine { cont ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                runCatching { context.unregisterReceiver(this) }
                val granted = usbManager.hasPermission(device) ||
                    intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i("NextTerm.Usb", "USB permission for ${device.deviceName}: granted=$granted")
                if (cont.isActive) cont.resume(granted)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        // The system fills in extras, so the PendingIntent must be mutable; and a mutable
        // PendingIntent must be explicit on API 34+, hence setPackage().
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val pi = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), flags)
        Log.i("NextTerm.Usb", "requesting USB permission for ${device.deviceName}")
        usbManager.requestPermission(device, pi)
    }
}
