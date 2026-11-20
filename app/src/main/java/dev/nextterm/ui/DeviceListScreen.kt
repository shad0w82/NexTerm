package dev.nextterm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nextterm.profiles.Profile
import dev.nextterm.usb.DriverSource
import dev.nextterm.usb.DriverType
import dev.nextterm.usb.UsbDeviceEntry
import dev.nextterm.usb.typeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    vm: DeviceListViewModel,
    profiles: List<Profile>,
    activeSessionTitle: String?,
    onOpenSession: () -> Unit,
    onDeviceClick: (UsbDeviceEntry) -> Unit,
    onProfileConnect: (Profile) -> Unit,
    onProfileEdit: (Profile) -> Unit,
    onProfileDelete: (Profile) -> Unit,
) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val overrides by vm.overrides.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<UsbDeviceEntry?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NextTerm") },
                actions = { TextButton(onClick = vm::refresh) { Text("Refresh") } },
            )
        },
        bottomBar = {
            if (activeSessionTitle != null) {
                Card(
                    onClick = onOpenSession,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Session open", fontWeight = FontWeight.SemiBold)
                        Text("$activeSessionTitle · tap to return to the terminal", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (profiles.isNotEmpty()) {
                item { SectionHeader("Profiles") }
                items(profiles, key = { "p:" + it.id }) { p ->
                    ProfileCard(
                        profile = p,
                        status = profileStatus(p, devices),
                        onConnect = { onProfileConnect(p) },
                        onEdit = { onProfileEdit(p) },
                        onDelete = { deleting = p },
                    )
                }
            }
            item { SectionHeader("USB devices") }
            if (devices.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Text("No USB devices attached.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.padding(4.dp))
                        Text("Plug an adapter in through an OTG cable. The list refreshes automatically.")
                    }
                }
            } else {
                items(devices, key = { "d:" + it.path }) { entry ->
                    DeviceCard(entry, onClick = { onDeviceClick(entry) }, onDriverClick = { editing = entry })
                }
            }
        }
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete profile?") },
            text = { Text("\"${p.name}\" will be removed. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onProfileDelete(p); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    editing?.let { entry ->
        DriverOverrideDialog(
            entry = entry,
            current = overrides[entry.vidPid],
            onDismiss = { editing = null },
            onPick = { vm.setOverride(entry.vidPid, it); editing = null },
        )
    }
}

@Composable
private fun DeviceCard(entry: UsbDeviceEntry, onClick: () -> Unit, onDriverClick: () -> Unit) {
    val recognized = entry.driver != null
    Card(
        onClick = onClick,
        enabled = recognized,
        colors = if (recognized) CardDefaults.cardColors()
        else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                entry.productName ?: "Unknown device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            entry.manufacturerName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.padding(4.dp))
            Text(
                "VID:PID  0x%04X:0x%04X".format(entry.vidPid.vid, entry.vidPid.pid),
                fontFamily = FontFamily.Monospace,
            )
            Text(entry.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.padding(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (recognized) "Driver: ${entry.driver!!.typeLabel()} (${entry.driver.ports.size} port)"
                        else "Driver: not recognized",
                        fontWeight = FontWeight.Medium,
                    )
                    Text(sourceLabel(entry.source), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onDriverClick) { Text(if (recognized) "Change…" else "Set driver…") }
            }
        }
    }
}

private fun sourceLabel(s: DriverSource) = when (s) {
    DriverSource.DEFAULT_TABLE -> "matched by library default table · tap to connect"
    DriverSource.CUSTOM_TABLE -> "matched by NextTerm custom probe table · tap to connect"
    DriverSource.USER_OVERRIDE -> "forced by your driver override · tap to connect"
    DriverSource.NONE -> "tap \"Set driver…\" to force one"
}

@Composable
private fun DriverOverrideDialog(
    entry: UsbDeviceEntry,
    current: DriverType?,
    onDismiss: () -> Unit,
    onPick: (DriverType?) -> Unit,
) {
    var choice by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Driver for ${entry.vidPid}") },
        text = {
            Column {
                Text("Applies to every device with this VID:PID and is saved.")
                Spacer(Modifier.padding(4.dp))
                val options: List<DriverType?> = listOf(null) + DriverType.entries
                options.forEach { opt ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = choice == opt, onClick = { choice = opt }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == opt, onClick = { choice = opt })
                        Spacer(Modifier.width(8.dp))
                        Text(opt?.label ?: "Automatic (no override)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(choice) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** Where a profile would connect right now, mirroring resolveDevice() (without logging). */
private fun profileStatus(p: Profile, devices: List<UsbDeviceEntry>): String {
    val usable = devices.filter { it.vidPid == p.vidPid && it.driver != null }
    return when {
        usable.any { it.path == p.devicePath } -> "attached at ${p.devicePath}"
        usable.isNotEmpty() -> "attached at ${usable.first().path} (new path, will match by VID:PID)"
        else -> "not attached"
    }
}

@Composable
private fun ProfileCard(profile: Profile, status: String, onConnect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val attached = !status.startsWith("not")
    Card(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${profile.vidPid} · ${profile.config.summary} · flow ${profile.config.flowControl.label}",
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
