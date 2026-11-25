package dev.nexterm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.ScreenHeader
import dev.nexterm.ui.theme.NexOutlinedButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.nexterm.R
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import dev.nexterm.profiles.AdapterPrefs
import dev.nexterm.settings.SettingsStore
import dev.nexterm.profiles.Profile
import dev.nexterm.profiles.ProfileStore
import dev.nexterm.session.ConnectTarget
import dev.nexterm.session.SessionManager
import dev.nexterm.transport.SerialConfigStore
import dev.nexterm.usb.typeLabel
import java.util.UUID

/**
 * The full-screen connection form, shown over the shell (no bar) until the shared dialog replaces it.
 * Serializable so it survives the Activity being recreated.
 */
data class ConnectRoute(
    /** A new connection to an attached device (path), or editing a saved profile (profileId). */
    val devicePath: String?,
    val profileId: String?,
    /** Creating a new profile for the device. */
    val newProfile: Boolean = false,
) : java.io.Serializable

/** Four tabs in a shell (bar or rail by window width) plus the connection form on top. State-based: no navigation library yet. */
@Composable
fun AppRoot(vm: DeviceListViewModel, openTerminalRequests: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configStore = remember { SerialConfigStore(context) }
    val profileStore = remember { ProfileStore.get(context) }
    val devices by vm.devices.collectAsStateWithLifecycle()
    val profiles by profileStore.profiles.collectAsStateWithLifecycle()
    val adapterPrefs = remember { AdapterPrefs.get(context) }
    val settingsStore = remember { SettingsStore.get(context) }
    val lastProfileIds by adapterPrefs.lastProfile.collectAsStateWithLifecycle()
    val active by SessionManager.active.collectAsStateWithLifecycle()
    // Track the active session's connected flag too, so the home banner/status dots update live.
    val noSession = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val activeConnected by (active?.connected ?: noSession).collectAsStateWithLifecycle()
    val noReason = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
    val endReason by (active?.endReason ?: noReason).collectAsStateWithLifecycle()
    // True while an ended session waits for its adapter (Auto-reconnect).
    val awaiting by (active?.awaitingAdapter ?: noSession).collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(if (SessionManager.active.value != null) Tab.Terminal else Tab.Home) }
    var connectRoute by rememberSaveable { mutableStateOf<ConnectRoute?>(null) }
    // Full-screen terminal: only while the Terminal tab shows a connected session; Back leaves it first.
    var terminalFull by rememberSaveable { mutableStateOf(false) }
    var terminalView by rememberSaveable { mutableStateOf(TermView.Console) }
    val fullScreenNow = terminalFull && tab == Tab.Terminal && active != null && activeConnected
    LaunchedEffect(tab, active, activeConnected) { if (!fullScreenNow && terminalFull) terminalFull = false }
    BackHandler(enabled = fullScreenNow) { terminalFull = false }
    val rootView = LocalView.current
    DisposableEffect(fullScreenNow) {
        val window = (rootView.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, rootView) }
        if (fullScreenNow) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val controller = remember {
        ConnectController(context, scope, vm, profileStore, configStore) {
            // "Open terminal after Connect" decides between the terminal and Home's active-session screen.
            connectRoute = null
            tab = if (settingsStore.settings.value.openTerminal) Tab.Terminal else Tab.Home
        }
    }

    // Auto-reconnect: the adapter is back while an ended session waits for it. Wait until the app is in front, so
    // Android's USB permission prompt (asked again after every re-plug) appears where the user can see it.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapterBack = active?.let { a -> devices.any { it.vidPid == a.target.vidPid } } == true
    LaunchedEffect(active, awaiting, adapterBack, endReason, activeConnected) {
        val s = active ?: return@LaunchedEffect
        if (!awaiting || !adapterBack || endReason == null || activeConnected) return@LaunchedEffect
        lifecycle.withResumed {
            if (SessionManager.active.value === s && s.awaitingAdapter.value && controller.status is ConnectStatus.Idle) {
                controller.connect(s.target, auto = true)
            }
        }
    }

    LaunchedEffect(openTerminalRequests) {
        if (openTerminalRequests > 0 && SessionManager.active.value != null) { connectRoute = null; tab = Tab.Terminal }
    }
    // Back from any other tab goes to Home; from Home it leaves the app. The connection form has its own back.
    BackHandler(enabled = connectRoute == null && tab != Tab.Home) { tab = Tab.Home }

    // POST_NOTIFICATIONS (API 33+) is asked once, before the first connect. The service works
    // either way; without it the ongoing notification is just hidden.
    var afterNotif by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotif?.invoke()
        afterNotif = null
    }
    fun connect(target: ConnectTarget) {
        target.profileId?.let { adapterPrefs.set(target.vidPid, it) }
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

    val appSettings by settingsStore.settings.collectAsStateWithLifecycle()
    NexShell(selected = tab, onSelect = { tab = it }, sessionLive = active != null && activeConnected, fullScreen = fullScreenNow, showLabels = appSettings.navLabels) {
        val sessionSummary = active?.let { a ->
            SessionSummary(
                a.title, activeConnected, a.target.profileId, a.target.profileName, a.target.vidPid, a.target.devicePath, a.target.config,
                stats = a.stats, endReason = endReason, waiting = awaiting,
            )
        }
        when (tab) {
            Tab.Home -> HomeScreen(
                vm = vm,
                profiles = profiles,
                lastProfileIds = lastProfileIds,
                session = sessionSummary,
                onOpenSession = { tab = Tab.Terminal },
                onDisconnect = { SessionManager.disconnect() },
                onReconnect = { active?.let { connect(it.target) } },
                onClose = { SessionManager.dismiss() },
                onConnect = { entry, p -> connect(ConnectTarget(entry.path, p.vidPid, p.config, p.id, p.name, p.driverOverride)) },
                onSetup = { connectRoute = ConnectRoute(it.path, null) },
                onPickProfile = { entry, p -> adapterPrefs.set(entry.vidPid, p.id) },
            )
            Tab.Profiles -> ProfilesScreen(
                profiles = profiles,
                devices = devices,
                session = sessionSummary,
                onConnect = { p ->
                    val usb = devices.firstOrNull { it.vidPid == p.vidPid }
                    when {
                        active != null && activeConnected -> Toast.makeText(context, "Disconnect the active session first", Toast.LENGTH_SHORT).show()
                        usb == null -> Toast.makeText(context, "Plug in the adapter first (${p.vidPid})", Toast.LENGTH_SHORT).show()
                        else -> connect(ConnectTarget(p.devicePath, p.vidPid, p.config, p.id, p.name, p.driverOverride))
                    }
                },
                onEdit = { p -> connectRoute = ConnectRoute(null, p.id) },
                onDelete = { p -> profileStore.delete(p.id) },
                onNew = {
                    val usable = devices.firstOrNull { it.usable }
                    if (usable == null) Toast.makeText(context, "Plug in a serial adapter first", Toast.LENGTH_SHORT).show()
                    else connectRoute = ConnectRoute(usable.path, null, newProfile = true)
                },
            )
            Tab.Terminal -> {
                val s = active
                if (s == null) {
                    NoSession(onGoHome = { tab = Tab.Home })
                } else {
                    TerminalScreen(
                        session = s,
                        full = terminalFull,
                        onFullChange = { terminalFull = it },
                        view = terminalView,
                        onViewChange = { terminalView = it },
                        onReconnect = { connect(s.target) },
                    )
                }
            }
            Tab.Settings -> SettingsScreen(settingsStore)
        }
    }

    // The connection form floats over the shell: a dialog on wide windows, a bottom sheet on narrow ones.
    connectRoute?.let { route ->
        val profile = route.profileId?.let { id -> profiles.firstOrNull { it.id == id } }
        if (profile == null && devices.isEmpty()) {
            LaunchedEffect(Unit) { connectRoute = null } // nothing to connect to any more
        } else {
            ProfileFormHost(
                formKey = route.toString(),
                mode = when {
                    profile != null -> FormMode.EDIT
                    route.newProfile -> FormMode.NEW
                    else -> FormMode.SETUP
                },
                devices = devices,
                profiles = profiles,
                editingId = profile?.id,
                initialDevicePath = profile?.devicePath ?: route.devicePath,
                savedDevice = profile?.let { it.vidPid to it.devicePath },
                initialDriver = profile?.driverOverride,
                initial = profile?.config ?: configStore.load(),
                initialProfileName = profile?.name ?: "",
                working = controller.status is ConnectStatus.Working,
                onConnect = { save, name, f ->
                    if (save) {
                        val p = Profile(UUID.randomUUID().toString(), name, f.devicePath, f.vidPid, f.config, f.driverOverride)
                        profileStore.upsert(p)
                        connect(ConnectTarget(f.devicePath, f.vidPid, f.config, p.id, p.name, f.driverOverride))
                    } else {
                        connect(ConnectTarget(f.devicePath, f.vidPid, f.config, null, null, f.driverOverride))
                    }
                },
                onSave = { name, f ->
                    profileStore.upsert(
                        Profile(
                            id = profile?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            devicePath = f.devicePath,
                            vidPid = f.vidPid,
                            config = f.config,
                            driverOverride = f.driverOverride,
                        ),
                    )
                    Toast.makeText(context, if (profile == null) "Profile created" else "Profile saved", Toast.LENGTH_SHORT).show()
                    connectRoute = null
                    tab = Tab.Profiles
                },
                onDismiss = { controller.dismiss(); connectRoute = null },
            )
        }
    }

    ConnectDialogs(controller)
}

@Composable
private fun ConnectDialogs(controller: ConnectController) {
    when (val st = controller.status) {
        is ConnectStatus.ExplainPermission -> NexAlertDialog(
            title = "USB access needed",
            text = "Android will now ask whether NexTerm may access \"${st.deviceLabel}\". Tap OK there to open the port.\n\n" +
                "The permission lasts until the adapter is unplugged, so you'll be asked again after re-plugging it. " +
                "That's the trade-off of not letting the app auto-launch when an adapter is connected.",
            confirm = "Continue",
            onConfirm = controller::permissionContinue,
            onDismiss = controller::dismiss,
        )
        is ConnectStatus.Failed -> NexAlertDialog(
            title = "Couldn't connect",
            text = st.message,
            confirm = "OK",
            dismiss = null,
            onConfirm = controller::dismiss,
            onDismiss = controller::dismiss,
        )
        else -> Unit
    }
}

/** Terminal tab with nothing to show: the title, then the same dashed box as the other empty screens. */
@Composable
private fun NoSession(onGoHome: () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader("Terminal", wide)
        EmptyScreen(
            icon = painterResource(R.drawable.ic_nav_terminal),
            title = "No active session",
            text = "Connect an adapter from Home to open the terminal.",
            wide = wide,
        ) { NexOutlinedButton(onClick = onGoHome) { Text("Go to Home") } }
    }
}
