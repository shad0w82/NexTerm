package dev.nextterm.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.nextterm.session.ActiveSession
import dev.nextterm.session.SessionManager

private const val TAG = "NextTerm.TermUi"
private const val MIN_TEXT_SP = 6f
private const val MAX_TEXT_SP = 32f

@Composable
fun TerminalScreen(
    session: ActiveSession,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val connected by session.connected.collectAsStateWithLifecycle()
    val host = remember(session) { TerminalHost(context, session.terminal) }

    DisposableEffect(host) {
        SessionManager.clientProxy.delegate = host.sessionClient
        onDispose { if (SessionManager.clientProxy.delegate === host.sessionClient) SessionManager.clientProxy.delegate = null }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(session.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(
                    if (connected) "connected" else "disconnected",
                    color = if (connected) Color(0xFF7CD992) else Color(0xFFFF8A80), fontSize = 11.sp,
                    maxLines = 1, softWrap = false,
                )
            }
            if (connected) TextButton(onClick = host::toggleKeyboard) { Text("Keyboard", color = Color.White) }
            if (connected) TextButton(onClick = onDisconnect) { Text("Disconnect", color = Color(0xFFFF8A80)) }
            else {
                TextButton(onClick = onReconnect) { Text("Reconnect", color = Color(0xFF7CD992)) }
                TextButton(onClick = onClose) { Text("Close", color = Color.White) }
            }
        }

        // AndroidView only runs `factory` once, so key it on the host: Reconnect creates a new
        // session (and host/view) while this screen stays composed, and the old view would
        // otherwise stay on screen bound to the closed session, silently dropping input.
        key(host) {
            AndroidView(
                factory = { host.view },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        ExtraKeysBar(host)
    }
}

@Composable
private fun ExtraKeysBar(host: TerminalHost) {
    val keys = listOf(
        "ESC" to KeyEvent.KEYCODE_ESCAPE,
        "TAB" to KeyEvent.KEYCODE_TAB,
        "CTRL" to -1,
        "←" to KeyEvent.KEYCODE_DPAD_LEFT,
        "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
        "↑" to KeyEvent.KEYCODE_DPAD_UP,
        "→" to KeyEvent.KEYCODE_DPAD_RIGHT,
    )
    Row(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF1E1E1E))) {
        keys.forEach { (label, code) ->
            val active = code == -1 && host.ctrlLatched
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (active) Color(0xFF3D6FB6) else Color.Transparent)
                    .clickable { if (code == -1) host.ctrlLatched = !host.ctrlLatched else host.sendKey(code) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Owns the Android [TerminalView] and implements both Termux client interfaces for it.
 * Created per session and kept across recompositions (and rotations: the activity handles
 * config changes itself, so the view survives and simply gets a new size -> emulator resize).
 */
private class TerminalHost(private val context: Context, private val session: TerminalSession) {
    /** One-shot Ctrl from the extra-keys bar; cleared when the view consumes it. */
    var ctrlLatched by mutableStateOf(false)
    private var textSizeSp = 13f

    val view: TerminalView = TerminalView(context, null).apply {
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
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
                return 1.0f
            }
            return scale
        }
        override fun onSingleTapUp(e: MotionEvent) = showKeyboard()
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
        override fun readAltKey() = false
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

    private fun applyTextSize() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
        view.setTextSize(px.toInt())
    }

    /** Extra-keys press: route through the view so cursor-key application mode (vim, htop) is honoured. */
    fun sendKey(keyCode: Int) {
        var meta = 0
        if (ctrlLatched) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        ctrlLatched = false
        view.onKeyDown(keyCode, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
    }

    fun showKeyboard() {
        view.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(view, 0)
    }

    fun toggleKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        val visible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        if (visible) imm.hideSoftInputFromWindow(view.windowToken, 0) else showKeyboard()
    }
}
