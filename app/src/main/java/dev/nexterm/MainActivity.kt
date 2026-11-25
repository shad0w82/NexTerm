package dev.nexterm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.nexterm.ui.AppRoot
import dev.nexterm.ui.DeviceListViewModel
import dev.nexterm.ui.theme.NexTermTheme
import dev.nexterm.ui.theme.TerminalPalette

class MainActivity : ComponentActivity() {
    private val deviceVm: DeviceListViewModel by viewModels()
    private var openTerminalRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark for now (like the design), whatever the system theme is: light icons on the bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        TerminalPalette.install() // terminal colours for every session created from now on
        handleIntent(intent)
        setContent {
            NexTermTheme { AppRoot(deviceVm, openTerminalRequests) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_TERMINAL) openTerminalRequests++
    }

    override fun onResume() {
        super.onResume()
        // Backstop for attach/detach broadcasts missed while backgrounded.
        deviceVm.refresh()
    }

    companion object {
        const val ACTION_OPEN_TERMINAL = "dev.nexterm.action.OPEN_TERMINAL"
    }
}
