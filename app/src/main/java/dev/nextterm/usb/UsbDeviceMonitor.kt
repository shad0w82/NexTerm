package dev.nextterm.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Dynamic (runtime-registered) receiver for attach/detach. Deliberately not a manifest
 * filter, so plugging an adapter in never launches the app.
 */
class UsbDeviceMonitor(private val context: Context, private val onChange: (String) -> Unit) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            Log.i("NextTerm.Usb", "broadcast ${intent.action}")
            onChange(intent.action ?: "")
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // System broadcasts still reach NOT_EXPORTED receivers.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun stop() = runCatching { context.unregisterReceiver(receiver) }
}
