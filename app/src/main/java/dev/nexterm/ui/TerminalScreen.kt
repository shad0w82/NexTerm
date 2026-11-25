package dev.nexterm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.widget.Toast
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.nexterm.R
import dev.nexterm.commands.CommandStore
import dev.nexterm.commands.SavedCommand
import dev.nexterm.session.ActiveSession
import dev.nexterm.session.TermSize
import dev.nexterm.session.parseRaw
import dev.nexterm.session.SessionManager
import dev.nexterm.settings.ExtraKeys
import dev.nexterm.settings.SettingsStore
import dev.nexterm.transport.FlowControl
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexTermTheme
import dev.nexterm.ui.theme.Palette
import kotlin.math.roundToInt

private const val TAG = "NexTerm.TermUi"
private const val MIN_TEXT_SP = 7f
private const val MAX_TEXT_SP = 24f

/** Fill of the extra-keys bar and of the "session ended" bar. */
internal val BarBg = Color(0xFF12171E)
private val KeyBg = Color(0xFF161B22)
internal val KeyLine = Color(0xFF30363D)
internal val KeyText = Color(0xFFC9D1D9)
internal val Green = Color(0xFF34D399)
private val KeyOnBg = Color(0xFF0F2E25)

/**
 * The terminal: a header (name, parameters, time connected), the terminal itself on a darker background, and
 * under it the extra keys while connected or a "session ended" bar. A round button toggles full screen
 * (header and bars away) and can be dragged anywhere over the terminal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    session: ActiveSession,
    full: Boolean,
    onFullChange: (Boolean) -> Unit,
    view: TermView,
    onViewChange: (TermView) -> Unit,
    onReconnect: () -> Unit,
) {
    val context = LocalContext.current
    val connected by session.connected.collectAsStateWithLifecycle()
    val awaiting by session.awaitingAdapter.collectAsStateWithLifecycle()
    val endReason by session.endReason.collectAsStateWithLifecycle()
    val host = remember(session) { TerminalHost(context, session.terminal) }
    host.interactive = connected

    DisposableEffect(host) {
        SessionManager.clientProxy.delegate = host.sessionClient
        onDispose { if (SessionManager.clientProxy.delegate === host.sessionClient) SessionManager.clientProxy.delegate = null }
    }

    val settings by SettingsStore.get(context).settings.collectAsStateWithLifecycle()
    // The text size may also be changed by pinching Trace or Hex while the Console view is hidden.
    LaunchedEffect(host, settings.textSize) { host.syncTextSize(settings.textSize) }
    LaunchedEffect(host, settings.autoScroll) { host.view.setAutoScroll(settings.autoScroll) }
    val config = LocalConfiguration.current
    val wide = config.screenWidthDp >= 600
    val compact = config.screenHeightDp < 500
    val phone = config.smallestScreenWidthDp < 600
    // On a phone the header makes room for the terminal while the keyboard is up.
    val hideHeader = full || (phone && connected && WindowInsets.isImeVisible)

    // The send field under Trace and Hex; its text is shared by the two views and survives a rotation.
    var sendText by rememberSaveable { mutableStateOf("") }
    var sendError by remember { mutableStateOf(false) }
    // Under Hex: "raw" (hex pairs and "quoted text", sent exactly) or "text" (plain text with the line ending, like Trace).
    var hexRaw by rememberSaveable { mutableStateOf(true) }
    val lineEnding by session.lineEnding.collectAsStateWithLifecycle()
    val send = {
        val typed = sendText.trim()
        if (view == TermView.Hex && hexRaw) {
            val parsed = parseRaw(typed)
            val bytes = parsed.bytes
            if (bytes == null) {
                sendError = true
                Toast.makeText(context, parsed.error, Toast.LENGTH_LONG).show()
            } else {
                session.send(bytes)
                sendText = ""; sendError = false
            }
        } else if (typed.isNotEmpty()) {
            session.send(typed.toByteArray(Charsets.UTF_8) + lineEnding.bytes, echo = true)
            sendText = ""; sendError = false
        }
    }

    // Saved commands: a popup on screens under 1000 dp, a dock beside the terminal from 1000 dp.
    val commandStore = remember { CommandStore.get(context) }
    val commands by commandStore.commands.collectAsStateWithLifecycle()
    val dockCapable = config.screenWidthDp >= 1000
    var dockOpen by rememberSaveable { mutableStateOf(true) }
    var popupOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<CommandDraft?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val showDock = connected && !full && dockCapable && dockOpen
    LaunchedEffect(full, connected, dockCapable) { if (full || !connected || dockCapable) popupOpen = false }
    val sendCommand: (SavedCommand) -> Unit = { c -> sendSavedCommand(session, c); popupOpen = false }
    val newCommand = { draft = CommandDraft(null); editingId = null; confirmDelete = false; popupOpen = false }
    val editCommand: (SavedCommand) -> Unit = { c -> draft = CommandDraft(c); editingId = c.id; confirmDelete = false; popupOpen = false }

    // The shell owns the system bars and the navigation; only the keyboard is handled here.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        if (!hideHeader) {
            TerminalHeader(
                session, connected, wide, compact, phone, view, onViewChange, onViewOpen = { popupOpen = false },
                commandsOn = if (dockCapable) showDock else popupOpen,
                onCommands = { if (dockCapable) dockOpen = !dockOpen else popupOpen = !popupOpen },
                showSignals = settings.showSignals,
            )
        }
        // Tall screens get the signals in a bar of their own; a short one has them in the header (above).
        if (!hideHeader && connected && !compact && settings.showSignals) SignalBar(session)

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier.fillMaxSize().background(Palette.TerminalBg)
                        .padding(horizontal = if (full) 20.dp else 14.dp, vertical = if (full) 18.dp else 10.dp),
                ) {
                    // AndroidView only runs `factory` once, so key it on the host: Reconnect creates a new
                    // session (and host/view) while this screen stays composed, and the old view would
                    // otherwise stay on screen bound to the closed session, silently dropping input.
                    // In Trace and Hex the view stays laid out (so it keeps its size and the text on it) but is not drawn.
                    key(host) {
                        AndroidView(
                            factory = { host.view }, modifier = Modifier.fillMaxSize(),
                            update = { it.visibility = if (view == TermView.Console) View.VISIBLE else View.INVISIBLE },
                        )
                    }
                    if (view != TermView.Console) {
                        // Pinching here changes the same Text size setting as pinching the Console.
                        val textSize by rememberUpdatedState(settings.textSize)
                        Box(
                            Modifier.fillMaxSize().background(Palette.TerminalBg).pointerInput(Unit) {
                                detectPinchSteps { zoomIn ->
                                    val next = if (zoomIn) maxOf(textSize + 1, (textSize * 1.1f).roundToInt()) else minOf(textSize - 1, (textSize * 0.9f).roundToInt())
                                    SettingsStore.get(context).update { it.copy(textSize = next.coerceIn(MIN_TEXT_SP.toInt(), MAX_TEXT_SP.toInt())) }
                                }
                            },
                        ) {
                            if (view == TermView.Trace) TraceView(session.log, settings.textSize.toFloat(), settings.autoScroll, Modifier.fillMaxSize())
                            else HexView(session.log, settings.textSize.toFloat(), settings.autoScroll, Modifier.fillMaxSize())
                        }
                    }
                }
                    if (connected) FullScreenButton(full, onToggle = { onFullChange(!full) })
                    if (popupOpen && !dockCapable) CommandsPopup(commands, phone, sendCommand, editCommand, newCommand, onDismiss = { popupOpen = false })
            }

            if (!full) {
                when {
                    !connected -> EndedBar(waiting = awaiting && endReason != null, onReconnect)
                    view == TermView.Console -> ExtraKeysBar(host, settings.extraKeys ?: ExtraKeys.default(wide))
                    else -> SendBar(
                        hex = view == TermView.Hex, raw = hexRaw, onRaw = { hexRaw = it; sendError = false },
                        text = sendText, onText = { sendText = it; sendError = false }, error = sendError,
                        lineEnding = lineEnding, onLineEnding = session::cycleLineEnding, onSend = send,
                    )
                }
            }
            }
            if (showDock) CommandsDock(commands, sendCommand, editCommand, newCommand)
        }
    }

    draft?.let { d ->
        if (confirmDelete) {
            val stored = commands.firstOrNull { it.id == editingId }
            NexAlertDialog(
                title = "Delete command?",
                text = "“${stored?.name ?: d.name}” will be removed. This can't be undone.",
                confirm = "Delete", danger = true,
                onConfirm = {
                    editingId?.let(commandStore::delete)
                    draft = null; confirmDelete = false
                    Toast.makeText(context, "Command deleted", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { confirmDelete = false },
            )
        } else {
            CommandEditor(
                draft = d, editing = editingId != null,
                onSave = {
                    d.validated()?.let { c ->
                        commandStore.upsert(c)
                        Toast.makeText(context, if (editingId != null) "Command saved" else "Command created", Toast.LENGTH_SHORT).show()
                        draft = null
                    }
                },
                onDelete = { confirmDelete = true },
                onDismiss = { draft = null },
            )
        }
    }
}

// ---- header ----

/** "58×43": the terminal's columns × rows, muted and monospaced; nothing until the size is known. */
@Composable
private fun TermSizeLabel(size: TermSize, fontSize: TextUnit, lineHeight: TextUnit) {
    if (size.cols <= 0) return
    Text(
        "${size.cols}×${size.rows}", fontFamily = NexMono, fontSize = fontSize, lineHeight = lineHeight,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        modifier = Modifier.semantics { contentDescription = "Terminal size: ${size.cols} columns, ${size.rows} rows" },
    )
}

@Composable
private fun TerminalHeader(
    session: ActiveSession, connected: Boolean, wide: Boolean, compact: Boolean, phone: Boolean,
    view: TermView, onViewChange: (TermView) -> Unit, onViewOpen: () -> Unit,
    commandsOn: Boolean, onCommands: () -> Unit, showSignals: Boolean,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val target = session.target
    val cfg = target.config
    val flow = if (cfg.flowControl == FlowControl.NONE) "no flow control" else cfg.flowControl.label
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 52.dp)
            .drawBehind { drawLine(line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(horizontal = if (phone) 12.dp else 16.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
    ) {
        // Name and details on the left (the name gives way when space is short), the view button at the far right.
        Row(
            Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 8.dp),
        ) {
            LedDot(connected, 8.dp)
            val termSize by session.size.collectAsStateWithLifecycle()
            // Columns × rows of the console: under the name where the header is tight (phone), after the link settings on wide windows.
            val sizeUnderName = !wide || compact
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    target.profileName ?: "Quick connect", fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (sizeUnderName) TermSizeLabel(termSize, 11.sp, 14.sp)
            }
            if (compact && connected && showSignals) InlineSignals(session)
            if (wide) {
                if (!compact) Text("${cfg.summary} · $flow", fontFamily = NexMono, fontSize = 12.sp, color = muted, maxLines = 1)
                if (!compact) TermSizeLabel(termSize, 12.sp, 16.sp)
                if (!connected) {
                    Text("· ended", fontFamily = NexMono, fontSize = 12.sp, color = muted)
                } else if (!compact) {
                    val snapshot by rememberStatsSnapshot(session.stats, live = true)
                    Text(fmtDuration(snapshot.durationMs), fontFamily = NexMono, fontSize = 12.sp, color = muted)
                }
            }
        }
        ViewButton(view, onOpen = onViewOpen, onSelect = onViewChange)
        if (connected) CommandsButton(commandsOn, onCommands)
    }
}

// ---- bars under the terminal ----

/** The keys chosen in Settings, one row that scrolls sideways when there are too many for the width. */
@Composable
private fun ExtraKeysBar(host: TerminalHost, keys: List<String>) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.fillMaxWidth().background(BarBg)
            .drawBehind { drawLine(line, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) },
    ) {
        if (keys.isEmpty()) {
            Text(
                "No extra keys. Choose them in Settings, Terminal.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 17.dp),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                val gap = 4.dp
                // Half a dp less than the exact share: rounding every key up to whole pixels must not push the last one off the bar.
                val keyWidth = maxOf(44.dp, (maxWidth - gap * (keys.size - 1)) / keys.size - 0.5.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    keys.forEach { k ->
                        val on = (k == "CTRL" && host.ctrlLatched) || (k == "ALT" && host.altLatched)
                        ExtraKey(k, on, keyWidth) { host.pressKey(k) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraKey(label: String, on: Boolean, width: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.width(width).height(36.dp).clip(shape).background(if (on) KeyOnBg else KeyBg)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else KeyLine, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = NexMono, fontSize = 12.sp, color = if (on) Green else KeyText, maxLines = 1)
    }
}

@Composable
private fun EndedBar(waiting: Boolean, onReconnect: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().background(BarBg)
            .drawBehind { drawLine(line, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (waiting) "Waiting for the adapter. The session resumes when it is plugged back in." else "Session ended. This is the last transcript.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        NexButton(onClick = onReconnect, small = true) { Text("Reconnect") }
    }
}

// ---- the round full-screen button ----

/** Where the full-screen button sits, per state (normal or full, upright or sideways), as fractions of the free space. */
private object FabPositions {
    fun load(context: Context, key: String): Offset {
        val p = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        return Offset(p.getFloat("fab_${key}_x", 1f), p.getFloat("fab_${key}_y", 1f))
    }

    fun save(context: Context, key: String, pos: Offset) {
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit()
            .putFloat("fab_${key}_x", pos.x).putFloat("fab_${key}_y", pos.y).apply()
    }
}

/** 48 dp round button over the terminal: a tap toggles full screen, dragging moves it (12 dp from the edges at most). */
@Composable
private fun FullScreenButton(full: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val key = (if (full) "f" else "n") + (if (landscape) "L" else "P")
    val currentToggle by rememberUpdatedState(onToggle)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rangeW = (maxWidth - 72.dp).coerceAtLeast(0.dp)
        val rangeH = (maxHeight - 72.dp).coerceAtLeast(0.dp)
        var pos by remember(key) { mutableStateOf(FabPositions.load(context, key)) }
        var dragging by remember { mutableStateOf(false) }
        val bg = if (full) MaterialTheme.colorScheme.primary else Color(0xFF1C2128)
        Box(
            Modifier
                .offset(x = 12.dp + rangeW * pos.x, y = 12.dp + rangeH * pos.y)
                .graphicsLayer { scaleX = if (dragging) 1.08f else 1f; scaleY = if (dragging) 1.08f else 1f }
                .shadow(if (dragging) 10.dp else 6.dp, CircleShape)
                .size(48.dp).clip(CircleShape).background(bg)
                .border(1.dp, if (full) MaterialTheme.colorScheme.primary else KeyLine, CircleShape)
                .pointerInput(key, rangeW, rangeH) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false; FabPositions.save(context, key, pos) },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        val w = with(density) { rangeW.toPx() }
                        val h = with(density) { rangeH.toPx() }
                        pos = Offset(
                            if (w > 0) (pos.x + drag.x / w).coerceIn(0f, 1f) else 1f,
                            if (h > 0) (pos.y + drag.y / h).coerceIn(0f, 1f) else 1f,
                        )
                    }
                }
                // The detector is created once, so it must read the current callback, not the one from the first
                // composition (that one always says "enter full screen" and made the exit tap do nothing).
                .pointerInput(Unit) { detectTapGestures(onTap = { currentToggle() }) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (full) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                contentDescription = if (full) "Exit full screen" else "Full screen",
                tint = if (full) Color(0xFF06130E) else Green, modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Owns the Android [TerminalView] and implements both Termux client interfaces for it.
 * Created per session and kept across recompositions (and rotations: the activity handles
 * config changes itself, so the view survives and simply gets a new size -> emulator resize).
 */
private class TerminalHost(private val context: Context, private val session: TerminalSession) {
    /** One-shot Ctrl and Alt from the extra-keys bar; cleared when the next key consumes them. */
    var ctrlLatched by mutableStateOf(false)
    var altLatched by mutableStateOf(false)
    /** False once the session has ended: the transcript can be read but taps no longer bring up the keyboard. */
    @Volatile var interactive = true
    private val settingsStore = SettingsStore.get(context)
    private var textSizeSp = settingsStore.settings.value.textSize.toFloat()

    val view: TerminalView = TerminalView(context, null).apply {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = settingsStore.settings.value.keepScreenOn
    }

    val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(s: TerminalSession) = view.onScreenUpdated()
        override fun onTitleChanged(s: TerminalSession) {}
        override fun onSessionFinished(s: TerminalSession) = view.onScreenUpdated()
        override fun onCopyTextToClipboard(s: TerminalSession, text: String?) {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("terminal", text ?: ""))
        }
        override fun onPasteTextFromClipboard(s: TerminalSession?) {
            val cm = context.getSystemService(ClipboardManager::class.java)
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
            session.emulator.paste(text)
        }
        override fun onBell(s: TerminalSession) {}
        override fun onColorsChanged(s: TerminalSession) = view.invalidate()
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }
    }

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float {
            // Same approach as Termux: only change font size once the pinch passes a threshold.
            if (scale < 0.9f || scale > 1.1f) {
                textSizeSp = (textSizeSp * (if (scale > 1f) 1.1f else 0.9f)).coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)
                applyTextSize()
                // Pinching is another way to change the Text size setting.
                settingsStore.update { it.copy(textSize = textSizeSp.roundToInt()) }
                return 1.0f
            }
            return scale
        }
        override fun onSingleTapUp(e: MotionEvent) { if (interactive) showKeyboard() }
        override fun shouldBackButtonBeMappedToEscape() = false
        // Serial targets don't want IME composition/autocorrect; send characters as typed.
        override fun shouldEnforceCharBasedInput() = true
        override fun shouldUseCtrlSpaceWorkaround() = false
        override fun isTerminalViewSelected() = true
        override fun copyModeChanged(copyMode: Boolean) {}
        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
        override fun onKeyUp(keyCode: Int, e: KeyEvent) = false
        override fun onLongPress(event: MotionEvent) = false
        override fun readControlKey(): Boolean {
            val v = ctrlLatched
            if (v) ctrlLatched = false
            return v
        }
        override fun readAltKey(): Boolean {
            val v = altLatched
            if (v) altLatched = false
            return v
        }
        override fun readShiftKey() = false
        override fun readFnKey() = false
        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession) = false
        override fun onEmulatorSet() {}
        override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }
    }

    init {
        view.setTerminalViewClient(viewClient)
        applyTextSize()
        view.attachSession(session)
        view.post { view.requestFocus() }
    }

    /** Follows the Text size setting when it was changed elsewhere (a pinch in Trace or Hex). */
    fun syncTextSize(sp: Int) {
        if (textSizeSp.roundToInt() != sp) {
            textSizeSp = sp.toFloat()
            applyTextSize()
        }
    }

    private fun applyTextSize() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
        view.setTextSize(px.toInt())
    }

    /** A key of the extra-keys bar. */
    fun pressKey(label: String) {
        when (label) {
            "CTRL" -> ctrlLatched = !ctrlLatched
            "ALT" -> altLatched = !altLatched
            "ESC" -> sendKey(KeyEvent.KEYCODE_ESCAPE)
            "TAB" -> sendKey(KeyEvent.KEYCODE_TAB)
            "↑" -> sendKey(KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
            "←" -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            "HOME" -> sendKey(KeyEvent.KEYCODE_MOVE_HOME)
            "END" -> sendKey(KeyEvent.KEYCODE_MOVE_END)
            "PGUP" -> sendKey(KeyEvent.KEYCODE_PAGE_UP)
            "PGDN" -> sendKey(KeyEvent.KEYCODE_PAGE_DOWN)
            else -> if (label.length == 1) sendChar(label[0])
        }
    }

    /** Route a key through the view so cursor-key application mode (vim, htop) is honoured. */
    private fun sendKey(keyCode: Int) {
        var meta = 0
        if (ctrlLatched) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (altLatched) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        ctrlLatched = false
        altLatched = false
        view.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
    }

    /** A printable key such as | or ~: Ctrl turns letters and @[\]^_ into control codes, Alt prefixes ESC. */
    private fun sendChar(c: Char) {
        val ctrl = ctrlLatched
        val alt = altLatched
        ctrlLatched = false
        altLatched = false
        var code = c.code
        if (ctrl && code in 0x40..0x7F) code = code and 0x1F
        session.writeCodePoint(alt, code)
    }

    fun showKeyboard() {
        view.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(view, 0)
    }
}
