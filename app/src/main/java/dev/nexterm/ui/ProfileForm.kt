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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import dev.nexterm.profiles.Profile
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexOutlinedButton
import dev.nexterm.ui.theme.NexSwitch
import dev.nexterm.transport.FlowControl
import dev.nexterm.transport.Parity
import dev.nexterm.transport.SerialConfig
import dev.nexterm.transport.TxNewline
import dev.nexterm.usb.DriverType
import dev.nexterm.usb.UsbDeviceEntry
import dev.nexterm.usb.VidPid

/** The one form behind "Set up…", "New profile" and "Edit profile". */
enum class FormMode { SETUP, NEW, EDIT }

/** Everything the form produces: which device, which driver, which parameters. */
data class ConnectForm(
    val devicePath: String?,
    val vidPid: VidPid,
    val driverOverride: DriverType?,
    val config: SerialConfig,
)

/** One entry in the adapter select: an attached device, or a profile's saved device that's not attached. */
private data class DeviceOption(val path: String?, val vidPid: VidPid, val entry: UsbDeviceEntry?) {
    val title: String get() = entry?.let { "${it.productName ?: "USB device"} · $vidPid" } ?: "$vidPid · not connected"
}

private const val CUSTOM_BAUD = -1

/**
 * Centered dialog on wide windows (600 dp and up), bottom sheet on narrow ones. The fields scroll,
 * the buttons stay in view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormHost(
    formKey: String,
    mode: FormMode,
    devices: List<UsbDeviceEntry>,
    profiles: List<Profile>,
    /** The profile being edited (excluded from the duplicate-name check). */
    editingId: String?,
    initialDevicePath: String?,
    /** For profiles: the saved device, shown even when it isn't attached. */
    savedDevice: Pair<VidPid, String?>?,
    initialDriver: DriverType?,
    initial: SerialConfig,
    initialProfileName: String,
    working: Boolean,
    /** "Set up…": connect now; [save] says whether to keep it as a profile named [name]. */
    onConnect: (save: Boolean, name: String, form: ConnectForm) -> Unit,
    /** New or Edit profile: just save. */
    onSave: (name: String, form: ConnectForm) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(devices, savedDevice) {
        val attached = devices.map { DeviceOption(it.path, it.vidPid, it) }
        val saved = savedDevice?.let { (vp, path) -> if (attached.none { it.vidPid == vp }) DeviceOption(path, vp, null) else null }
        listOfNotNull(saved) + attached
    }
    var selectedPath by remember(formKey) {
        mutableStateOf(
            options.firstOrNull { it.entry != null && it.path == initialDevicePath && (savedDevice == null || it.vidPid == savedDevice.first) }?.path
                ?: savedDevice?.let { (vp, _) -> options.firstOrNull { it.entry != null && it.vidPid == vp }?.path }
                ?: initialDevicePath
                ?: options.firstOrNull()?.path,
        )
    }
    val device = options.firstOrNull { it.path == selectedPath } ?: options.firstOrNull()

    var cfg by remember(formKey) { mutableStateOf(initial) }
    var name by remember(formKey) { mutableStateOf(initialProfileName) }
    var saveAsProfile by remember(formKey) { mutableStateOf(true) }
    var baudChoice by remember(formKey) { mutableStateOf(if (initial.baudRate in SerialConfig.BAUD_PRESETS) initial.baudRate else CUSTOM_BAUD) }
    var customBaud by remember(formKey) { mutableStateOf(if (initial.baudRate in SerialConfig.BAUD_PRESETS) "" else initial.baudRate.toString()) }
    var nameError by remember(formKey) { mutableStateOf<String?>(null) }
    var baudError by remember(formKey) { mutableStateOf(false) }

    val baud = if (baudChoice == CUSTOM_BAUD) customBaud.toIntOrNull() else baudChoice
    val baudValid = baud != null && baud in 50..12_000_000

    val entry = device?.entry
    val effectiveDriver = entry?.let { e -> e.usbDevice?.let { dev -> initialDriver?.let { runCatching { it.instantiate(dev) }.getOrNull() } } ?: e.driver }
    val ports = effectiveDriver?.ports?.size ?: 1
    val cannotConnect = when {
        entry == null -> "The adapter isn't connected."
        effectiveDriver == null && !entry.isDemo -> "No driver detected for this adapter. Choose one with Change driver on Home."
        else -> null
    }
    val needName = mode != FormMode.SETUP || saveAsProfile

    fun form() = ConnectForm(
        devicePath = device?.path,
        vidPid = device!!.vidPid,
        driverOverride = initialDriver,
        config = cfg.copy(baudRate = baud!!, portIndex = cfg.portIndex.coerceIn(0, ports - 1)),
    )

    fun validate(): Boolean {
        baudError = baudChoice == CUSTOM_BAUD && !baudValid
        nameError = null
        if (needName) {
            val nm = name.trim()
            nameError = when {
                nm.isEmpty() -> "Enter a name for the profile."
                profiles.any { it.id != editingId && it.name.equals(nm, ignoreCase = true) } -> "A profile with this name already exists."
                else -> null
            }
        }
        return !baudError && nameError == null && device != null
    }

    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(formKey) { if (mode == FormMode.NEW) runCatching { nameFocus.requestFocus() } }

    val title = when (mode) { FormMode.SETUP -> "Set up connection"; FormMode.NEW -> "New profile"; FormMode.EDIT -> "Edit profile" }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                // The title keeps the width it needs and stays on one line; the device name gets what is left and is cut with "…".
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    if (mode == FormMode.SETUP && entry != null) {
                        Text(
                            "${entry.productName ?: "USB device"} · ${entry.vidPid}",
                            fontFamily = NexMono, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val nameField: @Composable () -> Unit = {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40); nameError = null },
                        placeholder = { Text("Profile name") },
                        singleLine = true,
                        enabled = needName,
                        isError = nameError != null,
                        // A blank line is always reserved, so an error never shifts the fields below.
                        supportingText = { Text(nameError ?: " ", maxLines = 1) },
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    )
                }
                if (mode != FormMode.SETUP) {
                    nameField()
                    FormRow("Adapter") {
                        if (options.isEmpty()) {
                            Text("None connected", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        } else {
                            SelectField(
                                options = options, selected = device, display = { it?.title ?: "" },
                                onSelect = { selectedPath = it?.path; cfg = cfg.copy(portIndex = 0) },
                                modifier = Modifier.width(240.dp), label = "Adapter",
                            )
                        }
                    }
                }
                if (ports > 1) {
                    FormRow("Port") {
                        SelectField(
                            options = (0 until ports).toList(), selected = cfg.portIndex.coerceIn(0, ports - 1),
                            display = { "Port ${it + 1}" }, onSelect = { cfg = cfg.copy(portIndex = it) },
                            modifier = Modifier.width(FIELD_WIDTH), label = "Port",
                        )
                    }
                }

                FormRow("Baud rate") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectField(
                            options = SerialConfig.BAUD_PRESETS + CUSTOM_BAUD, selected = baudChoice,
                            display = { if (it == CUSTOM_BAUD) "Custom…" else "$it" },
                            onSelect = {
                                // Start the custom field from the current rate instead of an empty one.
                                if (it == CUSTOM_BAUD && customBaud.isEmpty()) baud?.let { b -> customBaud = b.toString() }
                                baudChoice = it; baudError = false
                            },
                            modifier = Modifier.width(100.dp), label = "Baud rate",
                        )
                        CompactNumberField(
                            value = customBaud, onChange = { t -> customBaud = t.filter(Char::isDigit).take(8); baudError = false },
                            placeholder = "Baud", enabled = baudChoice == CUSTOM_BAUD, isError = baudError, modifier = Modifier.width(82.dp),
                        )
                    }
                }
                FormRow("Data bits") {
                    SelectField(listOf(5, 6, 7, 8), cfg.dataBits, { "$it" }, { cfg = cfg.copy(dataBits = it) }, Modifier.width(FIELD_WIDTH), label = "Data bits")
                }
                FormRow("Parity") {
                    SelectField(Parity.entries, cfg.parity, { it.label }, { cfg = cfg.copy(parity = it) }, Modifier.width(FIELD_WIDTH), label = "Parity")
                }
                FormRow("Stop bits") {
                    SelectField(listOf(1, 2), cfg.stopBits, { "$it" }, { cfg = cfg.copy(stopBits = it) }, Modifier.width(FIELD_WIDTH), label = "Stop bits")
                }
                FormRow("Flow control") {
                    SelectField(FlowControl.entries, cfg.flowControl, { it.label }, { cfg = cfg.copy(flowControl = it) }, Modifier.width(FIELD_WIDTH), label = "Flow control")
                }
                FormRow("Enter sends") {
                    SelectField(TxNewline.entries, cfg.txNewline, { it.label }, { cfg = cfg.copy(txNewline = it) }, Modifier.width(FIELD_WIDTH), label = "Enter sends")
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Received LF starts a new line", fontSize = 14.sp)
                        Text(
                            "Turn on if the device's output steps diagonally across the screen",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    NexSwitch(checked = cfg.rxLfAsCrLf, onCheckedChange = { cfg = cfg.copy(rxLfAsCrLf = it) }, label = "Received LF starts a new line")
                }

                if (mode == FormMode.SETUP) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Save as profile", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        NexSwitch(checked = saveAsProfile, onCheckedChange = { saveAsProfile = it; nameError = null }, label = "Save as profile")
                    }
                    nameField()
                }
                if (mode == FormMode.SETUP && cannotConnect != null) {
                    Text(cannotConnect, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NexOutlinedButton(onClick = onDismiss) { Text("Cancel") }
                if (mode == FormMode.SETUP) {
                    NexButton(
                        onClick = { if (validate()) onConnect(saveAsProfile, name.trim(), form()) },
                        enabled = cannotConnect == null && !working,
                    ) { if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Connect") }
                } else {
                    NexButton(onClick = { if (validate()) onSave(name.trim(), form()) }, enabled = device != null) { Text("Save") }
                }
            }
        }
    }

    NexDialogHost(onDismiss) { content() }
}

/** Every control on the right of a form row is this wide (190 dp in the mockup); only the Adapter select is wider. */
private val FIELD_WIDTH = 190.dp

/** Label on the left, the control on the right, like the rows in the design. */
@Composable
internal fun FormRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        control()
    }
}

/** Small numeric field; always shown, greyed out unless it applies (like the Custom baud field). */
@Composable
private fun CompactNumberField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = color, fontFamily = NexMono),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = source,
        modifier = modifier.height(36.dp),
    ) { inner ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value, innerTextField = inner, enabled = enabled, singleLine = true,
            visualTransformation = VisualTransformation.None, interactionSource = source, isError = isError,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            container = { OutlinedTextFieldDefaults.Container(enabled = enabled, isError = isError, interactionSource = source, shape = RoundedCornerShape(8.dp)) },
        )
    }
}
