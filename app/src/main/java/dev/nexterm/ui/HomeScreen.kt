package dev.nexterm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import dev.nexterm.ui.theme.NexTermTheme
import dev.nexterm.session.SessionStats
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nexterm.R
import dev.nexterm.profiles.Profile
import dev.nexterm.profiles.profileFor
import dev.nexterm.transport.FlowControl
import dev.nexterm.transport.SerialConfig
import com.hoho.android.usbserial.driver.UsbSerialPort
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexCard
import dev.nexterm.ui.theme.NexIconButton
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexOutlinedButton
import dev.nexterm.ui.theme.ScreenHeader
import dev.nexterm.usb.DriverSource
import dev.nexterm.usb.UsbDeviceEntry
import dev.nexterm.usb.VidPid
import dev.nexterm.usb.typeLabel

/** What Home needs to know about the open session, if any. */
data class SessionSummary(
    val title: String,
    val connected: Boolean,
    val profileId: String?,
    /** Profile name, or null for a quick connection. */
    val name: String? = null,
    val vidPid: VidPid? = null,
    val devicePath: String? = null,
    val config: SerialConfig? = null,
    /** Counters and traffic history of the session (for the tiles and the chart). */
    val stats: SessionStats? = null,
    /** Why the session ended when it was not the user's doing; shown in a banner. */
    val endReason: String? = null,
    /** The adapter is gone and Auto-reconnect is waiting for it to come back. */
    val waiting: Boolean = false,
)

/**
 * Home: with nothing open, the list of connected adapters (one card, one row each, details under the
 * row); with a session open, the session card and its buttons.
 */
@Composable
fun HomeScreen(
    vm: DeviceListViewModel,
    profiles: List<Profile>,
    lastProfileIds: Map<String, String>,
    session: SessionSummary?,
    onOpenSession: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
    onConnect: (UsbDeviceEntry, Profile) -> Unit,
    onSetup: (UsbDeviceEntry) -> Unit,
    onPickProfile: (UsbDeviceEntry, Profile) -> Unit,
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val overrides by vm.overrides.collectAsStateWithLifecycle()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val width = LocalConfiguration.current.screenWidthDp
    // Phone sideways (wide but short): the session screen shrinks so it fits without scrolling.
    val compact = wide && LocalConfiguration.current.screenHeightDp < 500 && session != null
    val side = if (compact) 24.dp else if (wide) 32.dp else 16.dp
    val layout = if (compact) StatLayout.COMPACT else if (wide) StatLayout.WIDE else StatLayout.PHONE
    val columns = if (!wide) 2 else if (width < 840) 3 else 6
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var driverFor by remember { mutableStateOf<UsbDeviceEntry?>(null) }
    var statsVersion by remember { mutableIntStateOf(0) }
    var dismissedReason by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            when {
                session == null || !wide -> "Home"
                session.connected -> "Active session"
                else -> "Session ended"
            },
            wide, compact = compact, titleSp = if (compact) 18f else null,
        ) {
            if (session == null) {
                IconButton(onClick = vm::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh adapters", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (wide) {
                // Wide windows: the session's buttons sit in the title row, like the mockup.
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    if (session.connected) {
                        NexButton(onClick = onOpenSession, small = compact) { Text("Open terminal") }
                        NexOutlinedButton(onClick = { session.stats?.reset(); statsVersion++ }, small = compact) { Text("Reset counters") }
                        NexOutlinedButton(onClick = onDisconnect, danger = true, small = compact) { Text("Disconnect") }
                    } else {
                        NexOutlinedButton(onClick = onClose, small = compact) { Text("Close") }
                        NexButton(onClick = onReconnect, small = compact) { Text("Reconnect") }
                    }
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            if (session != null) {
                if (!wide) item(key = "session-label") {
                    Text(
                        if (session.connected) "Active session" else "Session ended", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp),
                    )
                }
                val reason = session.endReason
                if (!session.connected && reason != null && reason != dismissedReason) item(key = "notice") {
                    NoticeBanner(
                        if (session.waiting) "$reason Waiting for it to come back; the session will resume on its own." else reason,
                        onDismiss = { dismissedReason = reason },
                    )
                }
                item(key = "session") {
                    val adapter = devices.firstOrNull { it.vidPid == session.vidPid }
                    SessionCard(
                        session = session, adapter = adapter, open = openKey == "session", wide = wide, compact = compact,
                        onToggle = { openKey = if (openKey == "session") null else "session" },
                    )
                }
                session.stats?.let { stats ->
                    item(key = "stats") {
                        val snapshot by rememberStatsSnapshot(stats, live = session.connected, version = statsVersion)
                        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                            StatTiles(snapshot, ended = !session.connected, columns = columns, layout = layout)
                            TrafficCard(snapshot, legend = wide, dim = !session.connected, layout = layout)
                        }
                    }
                }
                if (!wide) item(key = "session-actions") {
                    if (session.connected) {
                        // Narrow windows: Open terminal and Reset counters side by side, Disconnect full width under them.
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                NexButton(onClick = onOpenSession, modifier = Modifier.weight(1f)) { Text("Open terminal") }
                                NexOutlinedButton(onClick = { session.stats?.reset(); statsVersion++ }, modifier = Modifier.weight(1f)) { Text("Reset counters") }
                            }
                            NexOutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth(), danger = true) { Text("Disconnect") }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            NexButton(onClick = onReconnect, modifier = Modifier.weight(1f)) { Text("Reconnect") }
                            NexOutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
                        }
                    }
                }
            } else {
                item(key = "label") {
                    Text(
                        "Connected adapters", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp),
                    )
                }
                if (devices.isEmpty()) {
                    item(key = "empty") { NoAdapters(wide, onRefresh = vm::refresh) }
                } else {
                    item(key = "list") {
                        NexCard(Modifier.fillMaxWidth()) {
                            devices.forEachIndexed { i, entry ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val key = "d:" + entry.path
                                val candidates = profiles.filter { it.vidPid == entry.vidPid }
                                val profile = profileFor(entry.vidPid, profiles, lastProfileIds)
                                AdapterRow(
                                    entry = entry, profile = profile, candidates = candidates,
                                    open = openKey == key, wide = wide, sessionActive = false,
                                    onToggle = { openKey = if (openKey == key) null else key },
                                    onConnect = { if (profile != null) onConnect(entry, profile) },
                                    onSetup = { onSetup(entry) },
                                    onPickProfile = { onPickProfile(entry, it) },
                                    onChangeDriver = { driverFor = entry },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    driverFor?.let { entry ->
        DriverPickerDialog(
            entry = entry,
            current = overrides[entry.vidPid],
            onDismiss = { driverFor = null },
            onSave = { vm.setOverride(entry.vidPid, it); driverFor = null },
        )
    }
}

@Composable
private fun AdapterRow(
    entry: UsbDeviceEntry,
    profile: Profile?,
    candidates: List<Profile>,
    open: Boolean,
    wide: Boolean,
    sessionActive: Boolean,
    onToggle: () -> Unit,
    onConnect: () -> Unit,
    onSetup: () -> Unit,
    onPickProfile: (Profile) -> Unit,
    onChangeDriver: () -> Unit,
) {
    val green = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (wide) 64.dp else 0.dp)
                .background(if (open) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .drawBehind { if (open) drawRect(green, size = Size(3.dp.toPx(), size.height)) }
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show details", onClick = onToggle)
                .padding(horizontal = if (wide) 16.dp else 14.dp, vertical = if (wide) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_usb), contentDescription = null, tint = muted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(if (wide) 14.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.productName ?: "USB device", fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.isDemo) {
                    Text("Simulated · debug build", fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1)
                } else if (wide) {
                    Text("${entry.vidPid} · ${entry.path}", fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(entry.vidPid.toString(), fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1)
                }
                if (!wide) {
                    Spacer(Modifier.height(6.dp))
                    if (entry.usable) ProfileChip(profile?.name) else NoDriverNote()
                }
            }
            if (wide) {
                if (entry.usable) ProfileChip(profile?.name) else NoDriverNote()
                Spacer(Modifier.width(16.dp))
            } else Spacer(Modifier.width(8.dp))
            when {
                !entry.usable -> NexOutlinedButton(onClick = onChangeDriver, small = !wide) { Text("Choose driver…") }
                profile != null -> NexIconButton(onClick = onConnect, icon = painterResource(R.drawable.ic_plugs), label = "Connect", size = if (wide) 48.dp else 60.dp)
                else -> NexIconButton(onClick = onSetup, icon = painterResource(R.drawable.ic_add_link), label = "Set up…", size = if (wide) 48.dp else 60.dp)
            }
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = muted, modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (open) AdapterDetails(entry, profile, candidates, wide, sessionActive, onPickProfile, onChangeDriver)
    }
}

@Composable
private fun NoDriverNote() {
    Text("no driver detected", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, maxLines = 1)
}

/** Everything the app knows about the adapter, a profile selector, and Change driver…. */
@Composable
private fun AdapterDetails(
    entry: UsbDeviceEntry,
    profile: Profile?,
    candidates: List<Profile>,
    wide: Boolean,
    sessionActive: Boolean,
    onPickProfile: (Profile) -> Unit,
    onChangeDriver: () -> Unit,
    /** On the card of a session (running or ended): the name of its profile and its parameters, shown as text instead of the selector. */
    sessionProfile: Pair<String, String>? = null,
) {
    val serial = if (entry.hasPermission) runCatching { entry.usbDevice?.serialNumber }.getOrNull() else null
    val rows = listOf(
        "Manufacturer" to (entry.manufacturerName ?: "not provided"),
        "Product" to (entry.productName ?: "not provided"),
        "Serial number" to (serial ?: "not provided"),
        "VID:PID" to entry.vidPid.toString(),
        "Path" to entry.path,
        "Driver" to driverText(entry),
        "Control lines" to controlLinesText(entry),
        "Flow control" to flowControlText(entry),
        "USB permission" to if (entry.isDemo) "not needed" else if (entry.hasPermission) "Granted" else "Not requested",
    )
    Column(Modifier.fillMaxWidth().background(DetailsBg)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(start = if (wide) 54.dp else 14.dp, end = if (wide) 20.dp else 14.dp, top = 8.dp, bottom = if (wide) 18.dp else 14.dp)) {
            if (entry.usable) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
                    Text("Profile", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(top = 9.dp))
                    if (sessionProfile != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterEnd) { Text(sessionProfile.first, fontSize = 14.sp) }
                            if (sessionProfile.second.isNotEmpty()) {
                                Text(
                                    sessionProfile.second, fontFamily = NexMono, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    } else if (candidates.isEmpty()) {
                        Text("none yet · Set up… creates one", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 9.dp))
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            SelectField(
                                options = candidates, selected = profile ?: candidates.first(), display = { it.name },
                                onSelect = onPickProfile, modifier = Modifier.width(210.dp), label = "Profile",
                            )
                            val p = profile ?: candidates.first()
                            Text(
                                p.config.summary + " · " + if (p.config.flowControl == FlowControl.NONE) "no flow control" else p.config.flowControl.label,
                                fontFamily = NexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (wide) {
                val half = (rows.size + 1) / 2
                Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    listOf(rows.take(half), rows.drop(half)).forEach { col ->
                        Column(Modifier.weight(1f)) { col.forEachIndexed { i, (k, v) -> KeyValue(k, v, first = i == 0) } }
                    }
                }
            } else {
                rows.forEachIndexed { i, (k, v) -> KeyValue(k, v, first = i == 0) }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val hint = if (sessionActive) "To change the driver, disconnect first." else "If the chip isn't recognized, pick the driver yourself."
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NexOutlinedButton(onClick = onChangeDriver, enabled = !sessionActive) { Text("Change driver…") }
                    Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NexOutlinedButton(onClick = onChangeDriver, enabled = !sessionActive, modifier = Modifier.fillMaxWidth()) { Text("Change driver…") }
                    Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The control lines the adapter's driver can drive or read, in the order DTR, RTS, CTS, DSR, CD, RI. */
private fun controlLinesText(e: UsbDeviceEntry): String {
    if (e.isDemo) return "DTR RTS CTS DSR CD RI"
    val port = e.driver?.ports?.firstOrNull() ?: return "unknown without a driver"
    val supported = runCatching { port.supportedControlLines }.getOrNull() ?: return "unknown"
    val order = listOf(
        UsbSerialPort.ControlLine.DTR, UsbSerialPort.ControlLine.RTS, UsbSerialPort.ControlLine.CTS,
        UsbSerialPort.ControlLine.DSR, UsbSerialPort.ControlLine.CD, UsbSerialPort.ControlLine.RI,
    )
    // Spaces, not commas: the six names then fit on one line even on a narrow phone with large text.
    return order.filter { it in supported }.joinToString(" ") { it.name }.ifEmpty { "none" }
}

/** The flow control modes the driver offers (the library's XON/XOFF and XON/XOFF inline both show as XON/XOFF). */
private fun flowControlText(e: UsbDeviceEntry): String {
    if (e.isDemo) return "None, RTS/CTS, XON/XOFF"
    val port = e.driver?.ports?.firstOrNull() ?: return "unknown without a driver"
    val supported = runCatching { port.supportedFlowControl }.getOrNull() ?: return "unknown"
    val modes = buildList {
        if (UsbSerialPort.FlowControl.NONE in supported) add("None")
        if (UsbSerialPort.FlowControl.RTS_CTS in supported) add("RTS/CTS")
        if (UsbSerialPort.FlowControl.DTR_DSR in supported) add("DTR/DSR")
        if (UsbSerialPort.FlowControl.XON_XOFF in supported || UsbSerialPort.FlowControl.XON_XOFF_INLINE in supported) add("XON/XOFF")
    }
    return modes.joinToString(", ").ifEmpty { "None" }
}

private fun driverText(e: UsbDeviceEntry): String = when (e.source) {
    DriverSource.USER_OVERRIDE -> (e.driver?.typeLabel() ?: "forced") + " · chosen by you"
    DriverSource.CUSTOM_TABLE -> (e.driver?.typeLabel() ?: "") + " · added by NexTerm"
    DriverSource.DEFAULT_TABLE -> e.driver?.typeLabel() ?: "built-in"
    DriverSource.DEMO -> "demo"
    DriverSource.NONE -> "none detected"
}

/** The open session as a card: profile name, adapter, parameters, and the same details as the adapter row. */
@Composable
private fun SessionCard(session: SessionSummary, adapter: UsbDeviceEntry?, open: Boolean, wide: Boolean, compact: Boolean, onToggle: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cfg = session.config
    val flow = if (cfg == null) "" else if (cfg.flowControl == FlowControl.NONE) "no flow control" else cfg.flowControl.label
    NexCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show details", onClick = onToggle)
                .padding(horizontal = if (compact) 14.dp else if (wide) 20.dp else 16.dp, vertical = if (compact) 8.dp else if (wide) 18.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (wide) {
                LedDot(session.connected, if (compact) 10.dp else 12.dp)
                Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            session.name ?: "Quick connect", fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!session.connected) AdapterChip(adapter != null)
                    }
                    Text(
                        listOfNotNull(adapter?.productName, session.vidPid?.toString(), session.devicePath).joinToString(" · "),
                        fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (cfg != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(cfg.summary, fontFamily = NexMono, fontSize = 13.sp)
                        Text(flow, fontSize = 12.sp, color = muted)
                    }
                }
            } else {
                // Narrow cards: LED, name and (once ended) the adapter chip on the first line, then the adapter and the parameters.
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LedDot(session.connected, 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            session.name ?: "Quick connect", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        if (!session.connected) AdapterChip(adapter != null)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(adapter?.productName, session.vidPid?.toString()).joinToString(" · "),
                        fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (cfg != null) Text("${cfg.summary} · $flow", fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = muted, modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (open && adapter != null) {
            AdapterDetails(
                entry = adapter, profile = null, candidates = emptyList(), wide = wide, sessionActive = session.connected,
                onPickProfile = {}, onChangeDriver = {},
                sessionProfile = (session.name ?: "Quick connect") to (if (cfg == null) "" else "${cfg.summary} · $flow"),
            )
        }
    }
}

/** "Adapter connected" in a green pill or a dashed grey "Adapter unplugged", on the card of an ended session. */
@Composable
private fun AdapterChip(present: Boolean) {
    if (present) {
        Text(
            "Adapter connected", color = Color(0xFF34D399), fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.border(BorderStroke(1.dp, NexTermTheme.colors.success), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
        )
    } else {
        val line = Color(0xFF4B5563)
        Text(
            "Adapter unplugged", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.drawBehind {
                drawRoundRect(line, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
            }.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NoAdapters(wide: Boolean, onRefresh: () -> Unit) {
    NexEmptyBox(
        icon = painterResource(R.drawable.ic_usb_off),
        title = "No adapter connected",
        text = "Plug in the serial adapter with an OTG cable. Android asks for permission to use it when you tap Connect.",
        wide = wide,
    ) { NexOutlinedButton(onClick = onRefresh) { Text("Refresh list") } }
}
