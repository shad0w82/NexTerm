package dev.nexterm.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keys the extra-keys bar can show, in the order the Settings chips list them. */
object ExtraKeys {
    val ALL = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "|", "~", "/", "-", "HOME", "END", "PGUP", "PGDN")

    /** Default selection: 12 keys on wide windows (tablets), the 7 basic ones on phones. */
    fun default(wide: Boolean): List<String> = if (wide) ALL.take(12) else listOf("ESC", "TAB", "CTRL", "↑", "↓", "←", "→")
}

/** Everything the Settings screen edits. Defaults are the ones in the design. */
data class AppSettings(
    /** Terminal text size in sp (7 to 24); pinching the terminal changes it too. */
    val textSize: Int = 13,
    val localEcho: Boolean = false,
    /** Console, Trace and Hex jump to the newest output as it arrives; off = they stay where they are. */
    val autoScroll: Boolean = true,
    /** Lines kept in memory for Console and Trace. */
    val scrollback: Int = 10_000,
    val keepScreenOn: Boolean = true,
    /** Keys in the extra-keys bar; null means the default for the window size. */
    val extraKeys: List<String>? = null,
    val autoReconnect: Boolean = false,
    /** Go straight to Terminal when a session starts (otherwise stay on Home). */
    val openTerminal: Boolean = false,
    /** Control lines set when the port opens. */
    val dtr: Boolean = true,
    val rts: Boolean = false,
    /** The row with DTR, RTS, CTS, DSR, CD, RI and BREAK in the terminal. */
    val showSignals: Boolean = true,
    /** The names under the icons of the bottom bar (and the rail on wide windows). */
    val navLabels: Boolean = true,
)

/** SharedPreferences-backed [AppSettings] that the UI observes. */
class SettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            textSize = prefs.getInt("textSize", d.textSize).coerceIn(7, 24),
            localEcho = prefs.getBoolean("localEcho", d.localEcho),
            autoScroll = prefs.getBoolean("autoScroll", d.autoScroll),
            scrollback = prefs.getInt("scrollback", d.scrollback),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            extraKeys = prefs.getString("extraKeys", null)?.split(',')?.filter { it in ExtraKeys.ALL },
            autoReconnect = prefs.getBoolean("autoReconnect", d.autoReconnect),
            openTerminal = prefs.getBoolean("openTerminal", d.openTerminal),
            dtr = prefs.getBoolean("dtr", d.dtr),
            rts = prefs.getBoolean("rts", d.rts),
            showSignals = prefs.getBoolean("showSignals", d.showSignals),
            navLabels = prefs.getBoolean("navLabels", d.navLabels),
        )
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val s = change(_settings.value)
        if (s == _settings.value) return
        prefs.edit {
            putInt("textSize", s.textSize)
            putBoolean("localEcho", s.localEcho)
            putBoolean("autoScroll", s.autoScroll)
            putInt("scrollback", s.scrollback)
            putBoolean("keepScreenOn", s.keepScreenOn)
            if (s.extraKeys == null) remove("extraKeys") else putString("extraKeys", s.extraKeys.joinToString(","))
            putBoolean("autoReconnect", s.autoReconnect)
            putBoolean("openTerminal", s.openTerminal)
            putBoolean("dtr", s.dtr)
            putBoolean("rts", s.rts)
            putBoolean("showSignals", s.showSignals)
            putBoolean("navLabels", s.navLabels)
        }
        _settings.value = s
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context.applicationContext).also { instance = it } }
    }
}
