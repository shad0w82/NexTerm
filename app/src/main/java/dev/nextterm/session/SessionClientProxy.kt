package dev.nextterm.session

import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * The session outlives any UI (it lives as long as the foreground service), so it talks to a
 * proxy. The terminal screen plugs its real client in while visible; otherwise callbacks are
 * dropped (the emulator still processes data, so the screen is up to date when reopened).
 */
class SessionClientProxy : TerminalSessionClient {
    @Volatile var delegate: TerminalSessionClient? = null

    override fun onTextChanged(s: TerminalSession) { delegate?.onTextChanged(s) }
    override fun onTitleChanged(s: TerminalSession) { delegate?.onTitleChanged(s) }
    override fun onSessionFinished(s: TerminalSession) { delegate?.onSessionFinished(s) }
    override fun onCopyTextToClipboard(s: TerminalSession, text: String?) { delegate?.onCopyTextToClipboard(s, text) }
    override fun onPasteTextFromClipboard(s: TerminalSession?) { delegate?.onPasteTextFromClipboard(s) }
    override fun onBell(s: TerminalSession) { delegate?.onBell(s) }
    override fun onColorsChanged(s: TerminalSession) { delegate?.onColorsChanged(s) }
    override fun onTerminalCursorStateChange(state: Boolean) { delegate?.onTerminalCursorStateChange(state) }
    override fun getTerminalCursorStyle(): Int? = delegate?.terminalCursorStyle

    override fun logError(tag: String?, message: String?) { Log.e(tag ?: TAG, message ?: "") }
    override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: TAG, message ?: "") }
    override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: TAG, message ?: "") }
    override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: TAG, message ?: "") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: TAG, message ?: "") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(tag ?: TAG, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: TAG, "", e) }

    private companion object { const val TAG = "NextTerm.Term" }
}
