package dev.nexterm.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.R
import dev.nexterm.session.ByteLog
import dev.nexterm.session.Dir
import dev.nexterm.session.LogEntry
import dev.nexterm.transport.TxNewline
import dev.nexterm.ui.theme.NexMono
import kotlinx.coroutines.delay
import java.util.Locale

/** The three ways to look at the same traffic. */
enum class TermView(val label: String, @DrawableRes val icon: Int) {
    Console("Console", R.drawable.ic_view_console),
    Trace("Trace", R.drawable.ic_view_trace),
    Hex("Hex", R.drawable.ic_view_hex),
}

private val ViewBtnBg = Color(0xFF1A1D21)
private val Faint = Color(0xFF6B7280)
private val TxBlue = Color(0xFF60A5FA)
private val NoteAmber = Color(0xFFF59E0B)
private val ErrRed = Color(0xFFF87171)

// ---- the view button in the header ----

/** The header button that names the current view and opens the menu to change it. */
@Composable
internal fun ViewButton(view: TermView, onOpen: () -> Unit = {}, onSelect: (TermView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        Row(
            Modifier.height(36.dp).clip(shape).background(ViewBtnBg).border(1.dp, KeyLine, shape)
                .clickable(role = Role.DropdownList, onClickLabel = "Change view") { open = true; onOpen() }
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(painterResource(view.icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(view.label, fontSize = 13.sp, maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = muted, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(190.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, KeyLine),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(horizontal = 6.dp)) {
                TermView.entries.forEach { v ->
                    val on = v == view
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(role = Role.RadioButton) { open = false; onSelect(v) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val tint = if (on) Green else MaterialTheme.colorScheme.onSurface
                        Icon(painterResource(v.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        Text(v.label, fontSize = 14.sp, color = tint, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Default.Check, contentDescription = "Current view", tint = Green, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---- Trace: one row per message ----

private fun printable(b: Byte) = b in 32..126

/** The text of a message: printable ASCII, CR and LF left out, every other byte shown as a dot. */
private fun asciiText(bytes: ByteArray) = buildString {
    for (b in bytes) if (b != 13.toByte() && b != 10.toByte()) append(if (printable(b)) b.toInt().toChar() else '·')
}

/** For every entry: -1 for a note, else the milliseconds since the message before it (0 for the first). Cheap even for a very long log. */
private fun traceDeltas(entries: List<LogEntry>): LongArray {
    val out = LongArray(entries.size)
    var previous = -1L
    entries.forEachIndexed { i, e ->
        if (e.dir == null) { out[i] = -1; return@forEachIndexed }
        out[i] = if (previous < 0) 0L else e.t - previous
        previous = e.t
    }
    return out
}

private fun deltaLabel(ms: Long) =
    "+" + if (ms < 10_000) String.format(Locale.US, "%.2f", ms / 1000.0) else String.format(Locale.US, "%.1f", ms / 1000.0)

/**
 * The log as messages with the direction, the time since the one before, and a rule across long pauses. Only the
 * rows on screen are built, so a log of tens of thousands of messages (the Scrollback setting) costs nothing extra.
 */
@Composable
internal fun TraceView(log: ByteLog, textSize: Float, autoScroll: Boolean, modifier: Modifier = Modifier) {
    val entries by rememberLogEntries(log)
    val deltas = remember(entries) { traceDeltas(entries) }
    val style = monoStyle(textSize)
    val timeWidth = (textSize * 3.4f).dp
    FollowList(entries.size, { entries[it].id }, autoScroll, modifier) { i ->
        val e = entries[i]
        val delta = deltas[i]
        Column {
            if (delta > 3000) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashedRule(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1f s", delta / 1000.0), fontFamily = NexMono, fontSize = 12.sp, color = Faint)
                    DashedRule(Modifier.weight(1f))
                }
            }
            if (e.dir == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(timeWidth))
                    Text(e.note, style = style, color = NoteAmber, modifier = Modifier.weight(1f))
                }
            } else {
                val tx = e.dir == Dir.TX
                val text = remember(e) { asciiText(e.bytes) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(deltaLabel(delta), style = style, color = Faint, textAlign = TextAlign.End, modifier = Modifier.width(timeWidth))
                    val color = when {
                        tx -> TxBlue
                        text.startsWith("ERR") -> ErrRed
                        else -> Color.Unspecified
                    }
                    DirTriangle(tx, textSize)
                    Text(text, style = style, color = color, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- Hex: rows of bytes with the characters beside them ----

/**
 * Where each message's rows start when [perRow] bytes go in a row, so a row index can be turned into the message and
 * the row inside it without building any row: the Hex list only makes the rows on screen.
 */
private class HexIndex(val entries: List<LogEntry>, val perRow: Int) {
    /** starts[i] = number of rows before entry i; notes and empty messages have none. */
    val starts = IntArray(entries.size + 1).also { s ->
        var rows = 0
        entries.forEachIndexed { i, e ->
            s[i] = rows
            if (e.dir != null && e.bytes.isNotEmpty()) rows += (e.bytes.size + perRow - 1) / perRow
        }
        s[entries.size] = rows
    }
    val rowCount: Int get() = starts[entries.size]

    /** The entry that row [row] belongs to: the last one that starts at or before it. */
    fun entryOf(row: Int): Int {
        var lo = 0
        var hi = entries.size // first index whose start is above the row
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= row) lo = mid + 1 else hi = mid
        }
        return lo - 1
    }

    fun keyOf(row: Int): Long {
        val i = entryOf(row)
        return entries[i].id * 8192 + (row - starts[i])
    }
}

private const val HEX = "0123456789ABCDEF"

/**
 * The log as bytes: as many to a row as fit the width (the hex, then the characters beside it), whatever the screen
 * and the text size, so the rows always fill the line.
 */
@Composable
internal fun HexView(log: ByteLog, textSize: Float, autoScroll: Boolean, modifier: Modifier = Modifier) {
    val entries by rememberLogEntries(log)
    val density = LocalDensity.current
    val style = monoStyle(textSize)
    val measurer = rememberTextMeasurer()
    // The real width of one monospaced character, from a long run so rounding does not add up.
    val charPx = remember(style) { measurer.measure("0".repeat(40), style).size.width / 40f }
    BoxWithConstraints(modifier) {
        val trianglePx = with(density) { (textSize * 0.75f).sp.toPx() }
        val triangleWidth = with(density) { trianglePx.toDp() }
        val gapPx = with(density) { 10.dp.toPx() }
        // Triangle column and two 10 dp gaps, then per bytes: 3 characters each less the last space, plus 1 character each.
        val available = constraints.maxWidth - trianglePx - 2 * gapPx - 1f
        val perRow = (((available / charPx) + 1f) / 4f).toInt().coerceAtLeast(1)
        val index = remember(entries, perRow) { HexIndex(entries, perRow) }
        FollowList(index.rowCount, index::keyOf, autoScroll, Modifier.fillMaxSize()) { row ->
            val ei = index.entryOf(row)
            val e = index.entries[ei]
            val from = (row - index.starts[ei]) * perRow
            val to = minOf(from + perRow, e.bytes.size)
            val hex = remember(e, from, perRow) {
                buildString {
                    for (k in from until to) {
                        if (k > from) append(' ')
                        append(HEX[(e.bytes[k].toInt() shr 4) and 15]).append(HEX[e.bytes[k].toInt() and 15])
                    }
                }.padEnd(perRow * 3 - 1)
            }
            val chars = remember(e, from, perRow) {
                buildString { for (k in from until to) append(if (printable(e.bytes[k])) e.bytes[k].toInt().toChar() else '.') }.padEnd(perRow)
            }
            Row(Modifier.fillMaxWidth()) {
                // The triangle marks where a message starts; the rows that continue it leave the column empty.
                if (from == 0) DirTriangle(e.dir == Dir.TX, textSize) else Spacer(Modifier.width(triangleWidth))
                Spacer(Modifier.width(10.dp))
                Text(hex, style = style, maxLines = 1, softWrap = false)
                // What is left over (less than one byte) goes between the hex and the characters, which sit against the edge.
                Spacer(Modifier.weight(1f))
                Text(chars, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---- shared bits ----

@Composable
private fun monoStyle(textSize: Float) = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = NexMono, fontSize = textSize.sp, lineHeight = (textSize * 1.45f).sp,
)

/**
 * A filled triangle in the colour of the direction: pointing up and blue for what was sent, pointing down and green for
 * what was received. Sized from the text, and as tall as one line so it sits beside the first line of a message.
 */
@Composable
private fun DirTriangle(tx: Boolean, textSize: Float) {
    val density = LocalDensity.current
    val color = if (tx) TxBlue else Green
    Canvas(
        Modifier.width(with(density) { (textSize * 0.75f).sp.toDp() })
            .height(with(density) { (textSize * 1.45f).sp.toDp() }),
    ) {
        val h = textSize * 0.62f * density.fontScale * density.density
        val top = (size.height - h) / 2
        val path = Path().apply {
            if (tx) {
                moveTo(size.width / 2, top); lineTo(size.width, top + h); lineTo(0f, top + h)
            } else {
                moveTo(0f, top); lineTo(size.width, top); lineTo(size.width / 2, top + h)
            }
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun DashedRule(modifier: Modifier) {
    Box(
        modifier.height(1.dp).drawBehind {
            drawLine(
                KeyLine, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        },
    )
}

/**
 * Two-finger pinch on the Trace and Hex lists, like the one on the Console: one step each time the fingers have moved
 * 10% apart or together. One finger is left alone, so the list still scrolls. Calls [onStep] with true to enlarge.
 */
internal suspend fun PointerInputScope.detectPinchSteps(onStep: (zoomIn: Boolean) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var base = 0f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.filter { it.pressed }
            if (down.size >= 2) {
                val distance = (down[0].position - down[1].position).getDistance()
                if (base == 0f) {
                    base = distance
                } else if (distance > base * 1.1f) {
                    onStep(true); base = distance
                } else if (distance < base * 0.9f) {
                    onStep(false); base = distance
                }
                event.changes.forEach { it.consume() }
            } else {
                base = 0f
            }
        } while (event.changes.any { it.pressed })
    }
}

/** A fresh copy of the log whenever it has changed, checked a few times a second so a fast stream does not flood the screen. */
@Composable
private fun rememberLogEntries(log: ByteLog): State<List<LogEntry>> {
    val state = remember(log) { mutableStateOf(log.snapshot()) }
    LaunchedEffect(log) {
        var seen = -1L
        while (true) {
            val v = log.version
            if (v != seen) { seen = v; state.value = log.snapshot() }
            delay(150)
        }
    }
    return state
}

/** The rows the list showed before the data last changed (their count and keys), to find the first visible one again. */
private class PreviousRows(var count: Int = 0, var keyAt: (Int) -> Long = { 0L })

/** The row whose key is [key], given that [keyAt] grows with the row number; -1 when it has been trimmed away. */
private fun indexOfKey(count: Int, keyAt: (Int) -> Long, key: Long): Int {
    var lo = 0
    var hi = count - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val k = keyAt(mid)
        if (k == key) return mid
        if (k < key) lo = mid + 1 else hi = mid - 1
    }
    return -1
}

/**
 * A list that sticks to its end: the newest row is at the bottom and the list is anchored there, so it stays
 * in view while rows arrive and when the keyboard shrinks the list. Scrolling up stops it (the rows you are
 * reading stay put); scrolling back to the bottom sticks it again. A short list starts at the top. With [autoScroll]
 * off it does not stick at all.
 */
@Composable
private fun FollowList(count: Int, keyAt: (Int) -> Long, autoScroll: Boolean, modifier: Modifier, row: @Composable (Int) -> Unit) {
    val state = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    // Dragging towards older rows stops following at once: new rows arriving mid-gesture must not pull the list back.
    val dragWatcher = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y > 0f) follow = false
                return Offset.Zero
            }
        }
    }
    // When a scroll settles, following is on exactly when the newest row is at the bottom.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (!moving) follow = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        }
    }
    // With autoscroll off the rows in view stay put: a lazy list resting on its first item would show every new first item,
    // so the first visible row is looked up by its key in the new data and put back where it was before the next layout.
    val previous = remember { PreviousRows() }
    SideEffect {
        if (!autoScroll && previous.count > 0 && count > 0) {
            val index = state.firstVisibleItemIndex
            if (index < previous.count) {
                val row = indexOfKey(count, keyAt, previous.keyAt(previous.count - 1 - index))
                if (row >= 0 && count - 1 - row != index) state.requestScrollToItem(count - 1 - row, state.firstVisibleItemScrollOffset)
            }
        }
        previous.count = count
        previous.keyAt = keyAt
    }
    val newest = if (count > 0) keyAt(count - 1) else null
    LaunchedEffect(newest) { if (follow && autoScroll) state.scrollToItem(0) }
    // Turning autoscroll on brings the list to the newest row at once, like the console does.
    LaunchedEffect(autoScroll) { if (autoScroll) { follow = true; state.scrollToItem(0) } }
    // The list is reversed (index 0 is the newest row, at the bottom); Arrangement.Top keeps a short list at the top.
    // Rows are counted from the oldest (0) to the newest (count - 1).
    LazyColumn(modifier.fillMaxSize().nestedScroll(dragWatcher), state = state, reverseLayout = true, verticalArrangement = Arrangement.Top) {
        items(count, key = { keyAt(count - 1 - it) }) { row(count - 1 - it) }
    }
}

// ---- the send field under Trace and Hex ----

/** Bytes as "01 03 00 6B": upper case pairs, one space between. */
internal fun hexText(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

/**
 * The bar that replaces the extra keys in Trace and Hex: a field, the line ending (text only; tap to change it
 * for this session) and the send button. Under Hex there is also a switch: "raw" takes hex pairs mixed with text in
 * quotes and sends exactly those bytes, "text" takes plain text like Trace does, with the line ending.
 */
@Composable
internal fun SendBar(
    hex: Boolean,
    raw: Boolean,
    onRaw: (Boolean) -> Unit,
    text: String,
    onText: (String) -> Unit,
    error: Boolean,
    lineEnding: TxNewline,
    onLineEnding: () -> Unit,
    onSend: () -> Unit,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        error -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> KeyLine
    }
    Row(
        Modifier.fillMaxWidth().background(BarBg)
            .drawBehind { drawLine(line, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text, onValueChange = onText, singleLine = true, interactionSource = source,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = NexMono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f).height(36.dp),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.background)
                        .border(if (focused || error) 2.dp else 1.dp, borderColor, shape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(
                            if (hex && raw) "01 03 \"OK\" 0D" else "Send text",
                            fontFamily = NexMono, fontSize = 13.sp, color = Color(0xFF5B6573), maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        if (hex) RawTextSwitch(raw, onRaw)
        if (!hex || !raw) {
            Box(
                Modifier.height(36.dp).clip(shape).border(1.dp, KeyLine, shape)
                    .clickable(role = Role.Button, onClickLabel = "Change line ending (this session only)", onClick = onLineEnding)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(lineEnding.label, fontFamily = NexMono, fontSize = 12.sp, color = KeyText, maxLines = 1) }
        }
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClickLabel = "Send", onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_send), contentDescription = "Send", tint = Color(0xFF06130E), modifier = Modifier.size(22.dp))
        }
    }
}

/** Two segments, "raw" and "text", the chosen one lit like a chip that is on; tap the other to switch. */
@Composable
private fun RawTextSwitch(raw: Boolean, onRaw: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.height(36.dp).clip(shape).border(1.dp, KeyLine, shape).selectableGroup()) {
        listOf(true to "raw", false to "text").forEach { (isRaw, label) ->
            val on = raw == isRaw
            Box(
                Modifier.fillMaxHeight().background(if (on) Color(0xFF0F2E25) else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onRaw(isRaw) })
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontFamily = NexMono, fontSize = 12.sp, color = if (on) Green else KeyText, maxLines = 1) }
        }
    }
}
