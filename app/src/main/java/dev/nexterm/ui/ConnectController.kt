package dev.nexterm.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.nexterm.demo.DemoDevices
import dev.nexterm.profiles.ProfileStore
import dev.nexterm.profiles.Resolution
import dev.nexterm.profiles.resolveDevice
import dev.nexterm.session.ConnectTarget
import dev.nexterm.session.SessionManager
import dev.nexterm.settings.SettingsStore
import dev.nexterm.transport.SerialConfigStore
import dev.nexterm.usb.UsbDeviceEntry
import dev.nexterm.usb.requestUsbPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface ConnectStatus {
    data object Idle : ConnectStatus
    data class ExplainPermission(val deviceLabel: String) : ConnectStatus
    data object Working : ConnectStatus
    data class Failed(val message: String) : ConnectStatus
}

/**
 * The single path every connection goes through: device card, profile, or Reconnect.
 * resolve device -> (explain) -> USB permission -> open -> terminal.
 */
class ConnectController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val vm: DeviceListViewModel,
    private val profiles: ProfileStore,
    private val lastConfig: SerialConfigStore,
    private val onConnected: () -> Unit,
) {
    var status by mutableStateOf<ConnectStatus>(ConnectStatus.Idle)
        private set

    private var pending: Triple<UsbDeviceEntry, ConnectTarget, List<String>>? = null

    /** The attempt in progress is Auto-reconnect's: no explanation first, no jump to another tab, and a failure ends the waiting. */
    private var auto = false

    fun dismiss() {
        status = ConnectStatus.Idle
        pending = null
        auto = false
    }

    /**
     * Finds the device again and connects. With [auto] (Auto-reconnect, the adapter just came back) a device that is
     * not there is no error, and the first attempt goes straight to Android's permission prompt.
     */
    fun connect(target: ConnectTarget, auto: Boolean = false) {
        this.auto = auto
        vm.refresh()
        val label = target.profileName?.let { "profile '$it'" } ?: "device ${target.vidPid}"
        val (entry, notices) = when (val r = resolveDevice(target.devicePath, target.vidPid, vm.devices.value, label, driverForced = target.driverOverride != null)) {
            Resolution.Missing -> {
                if (auto) return
                status = ConnectStatus.Failed(
                    "No attached device matches ${target.vidPid}" +
                        (target.profileName?.let { " (profile \"$it\")" } ?: "") + ". Is the adapter plugged in?",
                )
                return
            }
            is Resolution.Exact -> r.entry to emptyList()
            is Resolution.Fallback -> {
                // Remember the new path so the next connect is an exact match again.
                target.profileId?.let { id -> profiles.get(id)?.let { profiles.upsert(it.copy(devicePath = r.entry.path)) } }
                r.entry to listOf(r.notice)
            }
        }
        pending = Triple(entry, target, notices)
        if (entry.hasPermission || auto) open() else status = ConnectStatus.ExplainPermission(entry.productName ?: entry.vidPid.toString())
    }

    /** Debug-only simulated device: no USB permission, no driver. */
    private fun openDemo(target: ConnectTarget, notices: List<String>) {
        status = ConnectStatus.Working
        scope.launch {
            SessionManager.connect(context, target.copy(devicePath = DemoDevices.entry()?.path), notices, isGone = { !DemoDevices.present }) {
                val initial = SettingsStore.get(context).settings.value
                DemoDevices.createTransport(target.config, initial.dtr, initial.rts)
            }
                .onSuccess { connected() }
                .onFailure { fail(it.message ?: it.toString()) }
        }
    }

    /** User accepted our explanation; now Android's own prompt appears. */
    fun permissionContinue() = open()

    private fun open() {
        val (entry, target, notices) = pending ?: return
        if (entry.isDemo) return openDemo(target, notices)
        val usbDevice = entry.usbDevice!!
        val driver = target.driverOverride
            ?.let { forced -> runCatching { forced.instantiate(usbDevice) }.getOrNull() }
            ?.also { Log.i("NexTerm.Profile", "using forced driver ${target.driverOverride.label} for ${entry.vidPid}") }
            ?: entry.driver
            ?: run { fail("No driver for ${entry.vidPid}. Pick one under Driver."); return }
        status = ConnectStatus.Working
        scope.launch {
            if (!requestUsbPermission(context, driver.device)) {
                fail("USB permission was denied.")
                vm.refresh()
                return@launch
            }
            if (target.profileId == null) lastConfig.save(target.config)
            SessionManager.connectSerial(context, driver, target, notices)
                .onSuccess { connected() }
                .onFailure { fail(it.message ?: it.toString()) }
        }
    }

    private fun connected() {
        pending = null
        status = ConnectStatus.Idle
        vm.refresh()
        // Auto-reconnect resumes in place: the user stays where they are.
        if (auto) Toast.makeText(context, "Session resumed", Toast.LENGTH_SHORT).show() else onConnected()
        auto = false
    }

    private fun fail(message: String) {
        status = ConnectStatus.Failed(message)
        // A failed automatic attempt ends the waiting; otherwise closing this dialog would start it all over again.
        if (auto) SessionManager.stopWaiting()
        auto = false
    }
}
