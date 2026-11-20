package dev.nextterm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nextterm.profiles.Profile
import dev.nextterm.profiles.ProfileStore
import dev.nextterm.session.ConnectTarget
import dev.nextterm.session.SessionManager
import dev.nextterm.transport.SerialConfigStore
import dev.nextterm.usb.typeLabel
import java.util.UUID

sealed interface Route {
    data object Devices : Route
    /** New connection to an attached device (path), or editing a saved profile (profileId). */
    data class Connect(val devicePath: String?, val profileId: String?) : Route
    data object Terminal : Route
}

/** Tiny state-based navigation; three screens don't justify a navigation library yet. */
@Composable
fun AppRoot(vm: DeviceListViewModel, openTerminalRequests: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configStore = remember { SerialConfigStore(context) }
    val profileStore = remember { ProfileStore.get(context) }
    val devices by vm.devices.collectAsStateWithLifecycle()
    val profiles by profileStore.profiles.collectAsStateWithLifecycle()
    val active by SessionManager.active.collectAsStateWithLifecycle()

    var route by remember { mutableStateOf<Route>(if (SessionManager.active.value != null) Route.Terminal else Route.Devices) }
    val controller = remember {
        ConnectController(context, scope, vm, profileStore, configStore) { route = Route.Terminal }
    }

    LaunchedEffect(openTerminalRequests) {
        if (openTerminalRequests > 0 && SessionManager.active.value != null) route = Route.Terminal
    }

    // POST_NOTIFICATIONS (API 33+) is asked once, before the first connect. The service works
    // either way; without it the ongoing notification is just hidden.
    var afterNotif by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotif?.invoke()
        afterNotif = null
    }
    fun connect(target: ConnectTarget) {
        val prefs = context.getSharedPreferences("ui", 0)
        val needNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("askedNotif", false)
        if (needNotif) {
            prefs.edit { putBoolean("askedNotif", true) }
            afterNotif = { controller.connect(target) }
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            controller.connect(target)
        }
    }

    when (val r = route) {
        Route.Devices -> DeviceListScreen(
            vm = vm,
            profiles = profiles,
            activeSessionTitle = active?.title,
            onOpenSession = { route = Route.Terminal },
            onDeviceClick = { route = Route.Connect(it.path, null) },
            onProfileConnect = { p -> connect(ConnectTarget(p.devicePath, p.vidPid, p.config, p.id, p.name)) },
            onProfileEdit = { p -> route = Route.Connect(null, p.id) },
            onProfileDelete = { p -> profileStore.delete(p.id) },
        )

        is Route.Connect -> {
            val profile = r.profileId?.let { id -> profiles.firstOrNull { it.id == id } }
            // Device for a new connection: the tapped path. For a profile: wherever it resolves now.
            val entry = if (profile != null) {
                devices.firstOrNull { it.path == profile.devicePath && it.vidPid == profile.vidPid }
                    ?: devices.firstOrNull { it.vidPid == profile.vidPid }
            } else devices.firstOrNull { it.path == r.devicePath }

            if (profile == null && entry == null) {
                LaunchedEffect(Unit) { route = Route.Devices } // device unplugged while on this screen
            } else {
                val vidPid = profile?.vidPid ?: entry!!.vidPid
                val info = ConnectDeviceInfo(
                    name = entry?.productName ?: profile?.name ?: "USB device",
                    detail = listOfNotNull(
                        vidPid.toString(),
                        entry?.driver?.typeLabel() ?: if (entry != null) "no driver" else null,
                        entry?.path ?: "not attached",
                    ).joinToString(" · "),
                    ports = entry?.driver?.ports?.size ?: 1,
                    cannotConnectReason = when {
                        entry == null -> "This profile's device ($vidPid) isn't attached. You can still edit and save it."
                        entry.driver == null -> "This device has no driver. Go back and use \"Set driver…\" first."
                        else -> null
                    },
                )
                ConnectScreen(
                    screenKey = r.toString(),
                    title = if (profile != null) "Profile: ${profile.name}" else "Connect",
                    device = info,
                    initial = profile?.config ?: configStore.load(),
                    initialProfileName = profile?.name ?: "",
                    working = controller.status is ConnectStatus.Working,
                    onConnect = { cfg ->
                        connect(ConnectTarget(entry?.path ?: profile?.devicePath, vidPid, cfg, profile?.id, profile?.name))
                    },
                    onSaveProfile = { name, cfg ->
                        val saved = Profile(
                            id = profile?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            devicePath = entry?.path ?: profile?.devicePath,
                            vidPid = vidPid,
                            config = cfg,
                        )
                        profileStore.upsert(saved)
                        Toast.makeText(context, "Saved profile \"$name\"", Toast.LENGTH_SHORT).show()
                        route = Route.Devices
                    },
                    onBack = { controller.dismiss(); route = Route.Devices },
                )
            }
        }

        Route.Terminal -> {
            val s = active
            if (s == null) {
                LaunchedEffect(Unit) { route = Route.Devices }
            } else {
                TerminalScreen(
                    session = s,
                    onBack = { route = Route.Devices },
                    onDisconnect = { SessionManager.disconnect() },
                    onReconnect = { connect(s.target) },
                    onClose = { SessionManager.dismiss(); route = Route.Devices },
                )
            }
        }
    }

    ConnectDialogs(controller)
}

@Composable
private fun ConnectDialogs(controller: ConnectController) {
    when (val st = controller.status) {
        is ConnectStatus.ExplainPermission -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("USB access needed") },
            text = {
                Text(
                    "Android will now ask whether NextTerm may access \"${st.deviceLabel}\". Tap OK there to open the port.\n\n" +
                        "The permission lasts until the adapter is unplugged, so you'll be asked again after re-plugging it. " +
                        "That's the trade-off of not letting the app auto-launch when an adapter is connected.",
                )
            },
            confirmButton = { TextButton(onClick = controller::permissionContinue) { Text("Continue") } },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("Cancel") } },
        )
        is ConnectStatus.Failed -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("Couldn't connect") },
            text = { Text(st.message) },
            confirmButton = { TextButton(onClick = controller::dismiss) { Text("OK") } },
        )
        else -> Unit
    }
}
