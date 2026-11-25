package dev.nexterm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.R
import dev.nexterm.profiles.Profile
import dev.nexterm.transport.FlowControl
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexCard
import dev.nexterm.ui.theme.NexIconButton
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexOutlinedButton
import dev.nexterm.ui.theme.NexTermTheme
import dev.nexterm.ui.theme.ScreenHeader
import dev.nexterm.usb.UsbDeviceEntry

/**
 * The Profiles tab: one list of every saved profile. A row connects with one tap of its button;
 * tapping the row opens its details (Edit, Delete) right under it.
 */
@Composable
fun ProfilesScreen(
    profiles: List<Profile>,
    devices: List<UsbDeviceEntry>,
    session: SessionSummary?,
    onConnect: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onNew: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val side = if (wide) 32.dp else 16.dp
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Profiles", wide) {
            if (wide && profiles.isNotEmpty()) NexButton(onClick = onNew) { Text("New profile") }
        }
        if (profiles.isEmpty()) {
            EmptyProfiles(wide, onNew)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!wide) {
                    item(key = "label") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Saved profiles", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 2.dp),
                            )
                            NexButton(onClick = onNew, small = true) { Text("New profile") }
                        }
                    }
                }
                item(key = "list") {
                    NexCard(Modifier.fillMaxWidth()) {
                        profiles.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val attached = devices.firstOrNull { it.vidPid == p.vidPid && it.path == p.devicePath }
                                ?: devices.firstOrNull { it.vidPid == p.vidPid }
                            ProfileRow(
                                profile = p,
                                adapter = attached,
                                connected = session?.connected == true && session.profileId == p.id,
                                open = openId == p.id,
                                wide = wide,
                                onToggle = { openId = if (openId == p.id) null else p.id },
                                onConnect = { onConnect(p) },
                                onEdit = { onEdit(p) },
                                onDelete = { deleting = p },
                            )
                        }
                    }
                }
            }
        }
    }

    deleting?.let { p ->
        NexAlertDialog(
            title = "Delete profile?",
            text = "\"${p.name}\" will be removed. This can't be undone.",
            confirm = "Delete",
            danger = true,
            onConfirm = { if (openId == p.id) openId = null; onDelete(p); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

private fun flowText(f: FlowControl) = if (f == FlowControl.NONE) "no flow control" else f.label

@Composable
private fun ProfileRow(
    profile: Profile,
    adapter: UsbDeviceEntry?,
    connected: Boolean,
    open: Boolean,
    wide: Boolean,
    onToggle: () -> Unit,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val green = MaterialTheme.colorScheme.primary
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (wide) 64.dp else 0.dp)
                // An open row is tinted and gets a green bar on its left edge, like the mockup.
                .background(if (open) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .drawBehind { if (open) drawRect(green, size = Size(3.dp.toPx(), size.height)) }
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show details", onClick = onToggle)
                .padding(horizontal = if (wide) 16.dp else 14.dp, vertical = if (wide) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_nav_profiles), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(if (wide) 14.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                if (wide) {
                    Text(
                        "${profile.config.summary} · ${flowText(profile.config.flowControl)}",
                        fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    // Narrow rows: parameters and flow control on two lines, like the design.
                    Text(profile.config.summary, fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1)
                    Text(flowText(profile.config.flowControl), fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    AdapterLine(profile, adapter)
                }
            }
            if (wide) {
                AdapterLine(profile, adapter)
                Spacer(Modifier.width(16.dp))
            } else Spacer(Modifier.width(8.dp))
            if (connected) ConnectedPill() else NexIconButton(onClick = onConnect, icon = painterResource(R.drawable.ic_plugs), label = "Connect", size = if (wide) 48.dp else 60.dp)
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (open) ProfileDetails(profile, adapter, wide, onEdit, onDelete)
    }
}

@Composable
private fun AdapterLine(profile: Profile, adapter: UsbDeviceEntry?) {
    val plugged = adapter != null
    val ok = NexTermTheme.colors.success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).let { if (plugged) it.background(ok, CircleShape) else it.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape) },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            (adapter?.productName?.let { "$it · " } ?: "") + profile.vidPid + if (plugged) "" else " · not connected",
            fontFamily = NexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectedPill() {
    val ok = Color(0xFF34D399)
    Text(
        "Connected", color = ok, fontSize = 12.sp,
        modifier = Modifier.border(BorderStroke(1.dp, NexTermTheme.colors.success), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ProfileDetails(profile: Profile, adapter: UsbDeviceEntry?, wide: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val c = profile.config
    val rows = listOf(
        "Adapter" to ((adapter?.productName?.let { "$it · " } ?: "") + profile.vidPid),
        "Baud rate" to "${c.baudRate}",
        "Data bits" to "${c.dataBits}",
        "Parity" to c.parity.label,
        "Stop bits" to "${c.stopBits}",
        "Flow control" to c.flowControl.label,
        "Enter sends" to c.txNewline.label,
    )
    Column(Modifier.fillMaxWidth().background(DetailsBg)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(start = if (wide) 54.dp else 14.dp, end = if (wide) 20.dp else 14.dp, top = 6.dp, bottom = if (wide) 18.dp else 14.dp)) {
            if (wide) {
                // Two columns of key/value rows, each row under a thin line.
                val half = (rows.size + 1) / 2
                Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                    listOf(rows.take(half), rows.drop(half)).forEach { col ->
                        Column(Modifier.weight(1f)) { col.forEachIndexed { i, (k, v) -> KeyValue(k, v, first = i == 0) } }
                    }
                }
            } else {
                rows.forEachIndexed { i, (k, v) -> KeyValue(k, v, first = i == 0) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NexOutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                NexOutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), danger = true) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun EmptyProfiles(wide: Boolean, onNew: () -> Unit) {
    EmptyScreen(
        icon = painterResource(R.drawable.ic_nav_profiles),
        title = "No profiles yet",
        text = "A profile saves the serial settings for an adapter, so you can connect with one tap.",
        wide = wide,
    ) { NexButton(onClick = onNew) { Text("New profile") } }
}
