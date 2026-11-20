package dev.nextterm.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.nextterm.profiles.ProfileStore
import dev.nextterm.profiles.Resolution
import dev.nextterm.profiles.resolveDevice
import dev.nextterm.session.ConnectTarget
import dev.nextterm.session.SessionManager
import dev.nextterm.transport.SerialConfigStore
import dev.nextterm.usb.UsbDeviceEntry
import dev.nextterm.usb.requestUsbPermission
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

    fun dismiss() {
        status = ConnectStatus.Idle
        pending = null
    }

    fun connect(target: ConnectTarget) {
        vm.refresh()
        val label = target.profileName?.let { "profile '$it'" } ?: "device ${target.vidPid}"
        val (entry, notices) = when (val r = resolveDevice(target.devicePath, target.vidPid, vm.devices.value, label)) {
            Resolution.Missing -> {
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
        if (entry.hasPermission) open() else status = ConnectStatus.ExplainPermission(entry.productName ?: entry.vidPid.toString())
    }

    /** User accepted our explanation; now Android's own prompt appears. */
    fun permissionContinue() = open()

    private fun open() {
        val (entry, target, notices) = pending ?: return
        val driver = entry.driver ?: run { status = ConnectStatus.Failed("No driver for ${entry.vidPid}."); return }
        status = ConnectStatus.Working
        scope.launch {
            if (!requestUsbPermission(context, driver.device)) {
                status = ConnectStatus.Failed("USB permission was denied.")
                vm.refresh()
                return@launch
            }
            if (target.profileId == null) lastConfig.save(target.config)
            SessionManager.connectSerial(context, driver, target, notices)
                .onSuccess {
                    pending = null
                    status = ConnectStatus.Idle
                    vm.refresh()
                    onConnected()
                }
                .onFailure { status = ConnectStatus.Failed(it.message ?: it.toString()) }
        }
    }
}
