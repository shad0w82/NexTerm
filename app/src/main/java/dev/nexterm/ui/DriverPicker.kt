package dev.nexterm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexOutlinedButton
import dev.nexterm.usb.DriverSource
import dev.nexterm.usb.DriverType
import dev.nexterm.usb.UsbDeviceEntry
import dev.nexterm.usb.typeLabel

/** Chips each driver is typically used for, shown under its name so the choice doesn't need driver knowledge. */
private fun DriverType.typicalChips(): String = when (this) {
    DriverType.CH34X -> "CH340, CH341"
    DriverType.FTDI -> "FT232R, FT2232, FT231X"
    DriverType.CP210X -> "CP2102, CP2104"
    DriverType.PL2303 -> "Prolific PL2303"
    DriverType.CDC -> "Arduino, ESP32-S2 and S3 native USB, STM32"
}

/**
 * "Change driver…": Automatic (what the probe table detects) or one of the five drivers the app can force.
 * The choice is saved per VID:PID, so it applies to every profile of that adapter model.
 */
@Composable
fun DriverPickerDialog(
    entry: UsbDeviceEntry,
    current: DriverType?,
    onDismiss: () -> Unit,
    onSave: (DriverType?) -> Unit,
) {
    var pick by remember(entry.vidPid) { mutableStateOf(current) }
    val detected = when {
        entry.source == DriverSource.USER_OVERRIDE -> "Use the driver NexTerm detects"
        entry.driver != null -> "Detected: " + entry.driver.typeLabel() + if (entry.source == DriverSource.CUSTOM_TABLE) " · added by NexTerm" else ""
        else -> "Nothing detected for this adapter"
    }
    NexDialogHost(onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("Driver", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    "${entry.productName ?: "USB device"} · ${entry.vidPid}", fontFamily = NexMono, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Used whenever this adapter model is connected, in every profile.", fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val shape = RoundedCornerShape(10.dp)
            Column(Modifier.fillMaxWidth().clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)) {
                DriverRow("Automatic", detected, pick == null) { pick = null }
                DriverType.entries.forEach { d ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DriverRow(d.label, d.typicalChips(), pick == d) { pick = d }
                }
            }
            Text("If the driver doesn't match the chip, the port may fail to open.", fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                NexOutlinedButton(onClick = onDismiss) { Text("Cancel") }
                NexButton(onClick = { onSave(pick) }, enabled = pick != current) { Text("Save") }
            }
        }
    }
}

@Composable
private fun DriverRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val green = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) green else Color(0xFF4B5563), CircleShape)) {
            if (selected) Box(Modifier.align(Alignment.Center).size(10.dp).background(green, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
