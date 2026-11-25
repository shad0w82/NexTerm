package dev.nexterm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.R
import dev.nexterm.commands.CommandEnding
import dev.nexterm.commands.SavedCommand
import dev.nexterm.session.ActiveSession
import dev.nexterm.session.parseRaw
import dev.nexterm.transport.TxNewline
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexOutlinedButton
import java.util.UUID

private val DockBg = Color(0xFF0F1419)
private val BoltOnBg = Color(0xFF0F2E25)

// ---- sending ----

/** Sends a saved command: raw bytes (hex pairs and "quoted text") as they are, text with its line ending ("Session default" is the one of the Trace send field). */
internal fun sendSavedCommand(session: ActiveSession, c: SavedCommand) {
    if (c.hex) {
        parseRaw(c.text).bytes?.let { session.send(it) }
        return
    }
    val tail = when (c.ending) {
        CommandEnding.SESSION -> session.lineEnding.value.bytes
        CommandEnding.CR -> TxNewline.CR.bytes
        CommandEnding.LF -> TxNewline.LF.bytes
        CommandEnding.CRLF -> TxNewline.CRLF.bytes
        CommandEnding.NONE -> ByteArray(0)
    }
    session.send(c.text.toByteArray(Charsets.UTF_8) + tail, echo = true)
}

// ---- the button in the header ----

/** The lightning button: opens the commands popup, or shows and hides the dock on a wide screen. */
@Composable
internal fun CommandsButton(on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(36.dp).clip(shape).background(if (on) BoltOnBg else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else KeyLine, shape)
            .clickable(role = Role.Button, onClickLabel = "Commands", onClick = onClick)
            .semantics { contentDescription = "Commands" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_bolt), contentDescription = null, tint = Green, modifier = Modifier.size(20.dp))
    }
}

// ---- the list: dock and popup share it ----

/** Small caps title of a panel, with the green line under it. */
@Composable
private fun PanelHeader() {
    val green = MaterialTheme.colorScheme.primary
    Text(
        "COMMANDS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.99.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
            .drawBehind { drawLine(green, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun CommandList(commands: List<SavedCommand>, onSend: (SavedCommand) -> Unit, onEdit: (SavedCommand) -> Unit, onNew: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    if (commands.isEmpty()) {
        Text("No saved commands yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
    }
    commands.forEachIndexed { i, c -> CommandRow(c, first = i == 0, onSend = { onSend(c) }, onEdit = { onEdit(c) }) }
    Text(
        "+ New command", fontSize = 13.sp, color = Green,
        modifier = Modifier.fillMaxWidth()
            .drawBehind { drawLine(line, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClick = onNew)
            .padding(top = 8.dp, bottom = 6.dp),
    )
}

/** Name and the command under it: a tap on the row sends it, the pencil on the right opens the editor. */
@Composable
private fun CommandRow(c: SavedCommand, first: Boolean, onSend: () -> Unit, onEdit: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth()
            .then(if (first) Modifier else Modifier.drawBehind { drawLine(line, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClickLabel = "Send ${c.name}", onClick = onSend),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(c.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.text, fontFamily = NexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(36.dp).clip(shape).border(1.dp, KeyLine, shape)
                .clickable(role = Role.Button, onClickLabel = "Edit ${c.name}", onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit ${c.name}", tint = Green, modifier = Modifier.size(20.dp))
        }
    }
}

/** The column on the right of the terminal on a wide screen (1000 dp and up): the terminal gets narrower. */
@Composable
internal fun CommandsDock(commands: List<SavedCommand>, onSend: (SavedCommand) -> Unit, onEdit: (SavedCommand) -> Unit, onNew: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(DockBg)
            .drawBehind { drawLine(line, Offset(0.5.dp.toPx(), 0f), Offset(0.5.dp.toPx(), size.height), 1.dp.toPx()) }
            .verticalScroll(rememberScrollState()).padding(10.dp),
    ) {
        Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).border(1.dp, line, shape)) {
            PanelHeader()
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { CommandList(commands, onSend, onEdit, onNew) }
        }
    }
}

/**
 * The same list as a card over the top right of the terminal (full width on a phone), for screens under 1000 dp.
 * Tapping outside it closes it; sending a command closes it too.
 */
@Composable
internal fun BoxScope.CommandsPopup(
    commands: List<SavedCommand>,
    phone: Boolean,
    onSend: (SavedCommand) -> Unit,
    onEdit: (SavedCommand) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp)
                .then(if (phone) Modifier.fillMaxWidth() else Modifier.width(320.dp))
                .heightIn(max = maxHeight - 16.dp)
                .shadow(14.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, KeyLine, shape)
                // Taps inside the card must not reach the dismiss area behind it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            PanelHeader()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp)) {
                CommandList(commands, onSend, onEdit, onNew)
            }
        }
    }
}

// ---- the editor ----

/** The fields of the editor while it is open (kept outside the dialog so they survive the delete confirmation). */
internal class CommandDraft(initial: SavedCommand?) {
    val id: String = initial?.id ?: UUID.randomUUID().toString()
    var name by mutableStateOf(initial?.name ?: "")
    var text by mutableStateOf(initial?.text ?: "")
    var hex by mutableStateOf(initial?.hex ?: false)
    var ending by mutableStateOf(initial?.ending ?: CommandEnding.SESSION)
    var nameError by mutableStateOf<String?>(null)
    var textError by mutableStateOf<String?>(null)

    /** The command to store, or null after showing what is wrong. Plain hex is kept as "01 03 00 6B"; with quoted text it stays as typed. */
    fun validated(): SavedCommand? {
        val nm = name.trim()
        val cm = text.trim()
        val parsed = if (hex && cm.isNotEmpty()) parseRaw(cm) else null
        nameError = if (nm.isEmpty()) "Enter a name for the command." else null
        textError = when {
            cm.isEmpty() -> "Enter the command to send."
            parsed != null && parsed.bytes == null -> parsed.error
            else -> null
        }
        if (nameError != null || textError != null) return null
        val quoted = cm.contains('"') || cm.contains('\'')
        return SavedCommand(id, nm, if (parsed?.bytes != null && !quoted) hexText(parsed.bytes) else cm, hex, ending)
    }
}

/** New or Edit command: name, the text or hex to send, how to send it and the line ending. */
@Composable
internal fun CommandEditor(draft: CommandDraft, editing: Boolean, onSave: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(draft) { if (!editing) runCatching { nameFocus.requestFocus() } }
    NexDialogHost(onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(if (editing) "Edit command" else "New command", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = draft.name, onValueChange = { draft.name = it.take(40); draft.nameError = null },
                    placeholder = { Text("Name") }, singleLine = true, isError = draft.nameError != null,
                    // A blank line is always reserved, so an error never shifts the fields below.
                    supportingText = { Text(draft.nameError ?: " ", maxLines = 1) },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = draft.text, onValueChange = { draft.text = it.take(500); draft.textError = null },
                    placeholder = { Text(if (draft.hex) "Hex bytes, e.g. 01 03 \"OK\" 0D" else "Command, e.g. AT+STATUS?", fontFamily = NexMono, fontSize = 13.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = NexMono, fontSize = 13.sp),
                    singleLine = true, isError = draft.textError != null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    supportingText = { Text(draft.textError ?: " ", maxLines = 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FormRow("Send as") {
                    SelectField(
                        options = listOf(false, true), selected = draft.hex, display = { if (it) "Hex bytes" else "Text" },
                        onSelect = { draft.hex = it; draft.textError = null }, modifier = Modifier.width(190.dp), label = "Send as",
                    )
                }
                FormRow("Line ending") {
                    // Hex bytes go out as they are, so the ending does not apply.
                    SelectField(
                        options = CommandEnding.entries, selected = if (draft.hex) CommandEnding.NONE else draft.ending, display = { it.label },
                        onSelect = { draft.ending = it }, modifier = Modifier.width(190.dp), label = "Line ending", enabled = !draft.hex,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) NexOutlinedButton(onClick = onDelete, danger = true) { Text("Delete") }
                Spacer(Modifier.weight(1f))
                NexOutlinedButton(onClick = onDismiss) { Text("Cancel") }
                NexButton(onClick = onSave) { Text("Save") }
            }
        }
    }
}
