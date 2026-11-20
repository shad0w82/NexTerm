package dev.nextterm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.nextterm.transport.FlowControl
import dev.nextterm.transport.Parity
import dev.nextterm.transport.SerialConfig
import dev.nextterm.transport.TxNewline

/** What the Connect screen shows about the target device. */
data class ConnectDeviceInfo(
    val name: String,
    val detail: String,
    val ports: Int,
    /** Null when connecting is possible; otherwise why not. */
    val cannotConnectReason: String?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(
    screenKey: String,
    title: String,
    device: ConnectDeviceInfo,
    initial: SerialConfig,
    initialProfileName: String,
    working: Boolean,
    onConnect: (SerialConfig) -> Unit,
    onSaveProfile: (name: String, SerialConfig) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var cfg by remember(screenKey) { mutableStateOf(initial) }
    var baudText by remember(screenKey) { mutableStateOf(initial.baudRate.toString()) }
    var profileName by remember(screenKey) { mutableStateOf(initialProfileName) }
    val baud = baudText.toIntOrNull()
    val baudValid = baud != null && baud in 50..12_000_000
    fun current() = cfg.copy(baudRate = baud!!)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.detail, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }

            Section("Baud rate") {
                OutlinedTextField(
                    value = baudText,
                    onValueChange = { t -> baudText = t.filter(Char::isDigit).take(8) },
                    singleLine = true,
                    isError = !baudValid,
                    supportingText = { if (!baudValid) Text("Enter a rate between 50 and 12000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SerialConfig.BAUD_PRESETS.forEach { b ->
                        FilterChip(selected = baud == b, onClick = { baudText = b.toString() }, label = { Text("$b") })
                    }
                }
            }
            Section("Data bits") { ChipRow(listOf(5, 6, 7, 8), cfg.dataBits, { "$it" }) { cfg = cfg.copy(dataBits = it) } }
            Section("Stop bits") { ChipRow(listOf(1, 2), cfg.stopBits, { "$it" }) { cfg = cfg.copy(stopBits = it) } }
            Section("Parity") { ChipRow(Parity.entries, cfg.parity, { it.label }) { cfg = cfg.copy(parity = it) } }
            Section("Flow control") { ChipRow(FlowControl.entries, cfg.flowControl, { it.label }) { cfg = cfg.copy(flowControl = it) } }
            if (device.ports > 1) {
                Section("Port") {
                    ChipRow((0 until device.ports).toList(), cfg.portIndex, { "Port $it" }) { cfg = cfg.copy(portIndex = it) }
                }
            }
            Section("Enter key sends") { ChipRow(TxNewline.entries, cfg.txNewline, { it.label }) { cfg = cfg.copy(txNewline = it) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Received LF starts a new line")
                    Text(
                        "Turn on if output from the device steps diagonally across the screen",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = cfg.rxLfAsCrLf, onCheckedChange = { cfg = cfg.copy(rxLfAsCrLf = it) })
            }

            Button(
                onClick = { onConnect(current()) },
                enabled = baudValid && device.cannotConnectReason == null && !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (working) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text("Connect · ${baud ?: "?"} ${cfg.dataBits}${cfg.parity.short}${cfg.stopBits}")
            }
            device.cannotConnectReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            Section("Profile") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(40) },
                        singleLine = true,
                        placeholder = { Text("e.g. Router console") },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onSaveProfile(profileName.trim(), current()) },
                        enabled = baudValid && profileName.isNotBlank(),
                    ) { Text("Save profile") }
                }
                Text(
                    "Saves this device (by path and VID:PID) with the settings above.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}
