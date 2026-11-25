package dev.nexterm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nexterm.R
import dev.nexterm.settings.AppSettings
import dev.nexterm.settings.ExtraKeys
import dev.nexterm.settings.SettingsStore
import dev.nexterm.ui.theme.NexCard
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexSwitch
import dev.nexterm.ui.theme.ScreenHeader
import kotlin.math.roundToInt

/** The four sections of Settings, with the icon each one has in the section list. */
private enum class Cat(val title: String, val icon: Int) {
    Terminal("Terminal", R.drawable.ic_nav_terminal),
    Connection("Connection", R.drawable.ic_usb),
    App("App", R.drawable.ic_tune),
    About("About", R.drawable.ic_info),
}

private val ChipOn = Color(0xFF0F2E25)
private val ChipOff = Color(0xFF161B22)
private val DashColor = Color(0xFF374151)
private val SelectedRow = Color(0xFF12201C)

/**
 * Settings, laid out by window size like the design:
 * - under 600 dp wide (phone upright): a list of sections, each opening its own page with a back arrow;
 * - 600 to 840 dp (tablet upright): the sections as a 2 x 2 grid on top, the settings below at full width;
 * - 840 dp and more: the section list on the left and the settings on the right, split by a dashed line;
 *   on short windows (phone sideways) the list stays put and only the settings scroll.
 */
@Composable
fun SettingsScreen(store: SettingsStore) {
    val settings by store.settings.collectAsStateWithLifecycle()
    val config = LocalConfiguration.current
    val w = config.screenWidthDp
    val short = config.screenHeightDp < 500
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = saved?.let { Cat.valueOf(it) }
    val select: (Cat) -> Unit = { saved = it.name }

    BackHandler(enabled = w < 600 && picked != null) { saved = null }

    when {
        w < 600 -> {
            if (picked == null) PhoneList(select)
            else PhonePage(picked, settings, store) { saved = null }
        }
        w < 840 -> TabletPortrait(picked ?: Cat.Terminal, settings, store, select)
        else -> TwoColumns(picked ?: Cat.Terminal, settings, store, select, short)
    }
}

// ---- layouts ----

@Composable
private fun PhoneList(onSelect: (Cat) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", wide = false)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            NexCard(Modifier.fillMaxWidth()) {
                Cat.entries.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavRow(c, selected = false, chevron = true, onClick = { onSelect(c) })
                }
            }
        }
    }
}

@Composable
private fun PhonePage(cat: Cat, settings: AppSettings, store: SettingsStore, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Text(cat.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { CategoryBody(cat, settings, store, narrow = true) }
    }
}

@Composable
private fun TabletPortrait(cat: Cat, settings: AppSettings, store: SettingsStore, onSelect: (Cat) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings", wide = true)
        Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Cat.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { c -> Tile(c, selected = c == cat, modifier = Modifier.weight(1f), onClick = { onSelect(c) }) }
                }
            }
            DashedLine(vertical = false, Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { CategoryBody(cat, settings, store, narrow = false) }
        }
    }
}

@Composable
private fun TwoColumns(cat: Cat, settings: AppSettings, store: SettingsStore, onSelect: (Cat) -> Unit, short: Boolean) {
    val navWidth = if (short) 164.dp else 260.dp
    if (short) {
        // The section list stays put; only the settings scroll.
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Settings", wide = true, compact = true)
            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NavCard(cat, onSelect, Modifier.width(navWidth).align(Alignment.Top))
                DashedLine(vertical = true, Modifier.fillMaxHeight())
                androidx.compose.runtime.key(cat) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().widthIn(max = 720.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { CategoryBody(cat, settings, store, narrow = false) }
                }
            }
        }
    } else {
        // The whole page scrolls; the dashed line is drawn along the settings column, which is at least as tall as the list.
        var navHeight by remember { mutableIntStateOf(0) }
        val navDp = with(LocalDensity.current) { navHeight.toDp() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader("Settings", wide = true)
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                NavCard(cat, onSelect, Modifier.width(navWidth).align(Alignment.Top).onSizeChanged { navHeight = it.height })
                Column(
                    Modifier.weight(1f).widthIn(max = 720.dp).heightIn(min = navDp)
                        .drawBehind {
                            val x = -16.dp.toPx()
                            drawLine(
                                DashColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { CategoryBody(cat, settings, store, narrow = false) }
            }
        }
    }
}

// ---- navigation pieces ----

@Composable
private fun NavCard(cat: Cat, onSelect: (Cat) -> Unit, modifier: Modifier = Modifier) {
    NexCard(modifier) {
        Cat.entries.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavRow(c, selected = c == cat, chevron = false, onClick = { onSelect(c) })
        }
    }
}

/** One row of the section list: icon, name and, on phones, a chevron. A selected row is tinted and gets a green bar. */
@Composable
private fun NavRow(cat: Cat, selected: Boolean, chevron: Boolean, onClick: () -> Unit) {
    val green = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(if (selected) SelectedRow else Color.Transparent)
            .drawBehind { if (selected) drawRect(green, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(cat.icon), contentDescription = null, tint = if (selected) Green else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(cat.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A section as a tile (tablet upright). The selected one has a green border. */
@Composable
private fun Tile(cat: Cat, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.heightIn(min = 56.dp).clip(shape)
            .background(if (selected) SelectedRow else MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(cat.icon), contentDescription = null, tint = if (selected) Green else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(cat.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DashedLine(vertical: Boolean, modifier: Modifier = Modifier) {
    Box(
        (if (vertical) modifier.width(1.dp) else modifier.heightIn(min = 1.dp, max = 1.dp)).drawBehind {
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            if (vertical) drawLine(DashColor, Offset(0.5f, 0f), Offset(0.5f, size.height), 1.dp.toPx(), pathEffect = dash)
            else drawLine(DashColor, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx(), pathEffect = dash)
        },
    )
}

// ---- the settings themselves ----

@Composable
private fun CategoryBody(cat: Cat, s: AppSettings, store: SettingsStore, narrow: Boolean) {
    when (cat) {
        Cat.Terminal -> TerminalSettings(s, store)
        Cat.Connection -> ConnectionSettings(s, store, narrow)
        Cat.App -> AppSettingsCard(s, store, narrow)
        Cat.About -> AboutSettings()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TerminalSettings(s: AppSettings, store: SettingsStore) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val keys = s.extraKeys ?: ExtraKeys.default(wide)
    SettingsCard {
        // Text size: title, slider and a live preview.
        StackedRow {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowText("Text size", "You can also pinch the terminal to zoom.", Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Text("${s.textSize} sp", fontFamily = NexMono, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text("A", fontSize = 12.sp, color = muted)
                val sliderColors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = DashColor, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                )
                val interaction = remember { MutableInteractionSource() }
                // A plain round thumb on a thin track, like the browser's range input in the mockup.
                Slider(
                    value = s.textSize.toFloat(),
                    onValueChange = { v -> store.update { it.copy(textSize = v.roundToInt().coerceIn(7, 24)) } },
                    valueRange = 7f..24f, steps = 16, modifier = Modifier.weight(1f), colors = sliderColors,
                    interactionSource = interaction,
                    thumb = { SliderDefaults.Thumb(interactionSource = interaction, colors = sliderColors, thumbSize = DpSize(18.dp, 18.dp)) },
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state, colors = sliderColors, modifier = Modifier.height(4.dp),
                            drawStopIndicator = null, thumbTrackGapSize = 0.dp, trackInsideCornerSize = 2.dp,
                        )
                    },
                )
                Text("A", fontSize = 20.sp, color = muted)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0D12))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                val style = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = s.textSize.sp, lineHeight = (s.textSize * 1.45f).sp)
                Text("esp32:~# AT+STATUS?", style = style)
                Text("+STATUS: OK up=252 heap=214880", style = style)
            }
        }
        SwitchRow("Local echo", "Show what you type even if the device does not send it back.", s.localEcho) { v -> store.update { it.copy(localEcho = v) } }
        SwitchRow("Autoscroll", "Follow new output at the bottom. Turn off to stay where you scrolled.", s.autoScroll) { v -> store.update { it.copy(autoScroll = v) } }
        InlineRow("Scrollback", "Lines kept in memory for Console and Trace.") {
            SelectField(
                options = listOf(1000, 5000, 10000, 50000), selected = s.scrollback,
                display = { "%,d lines".format(it) }, onSelect = { v -> store.update { it.copy(scrollback = v) } },
                modifier = Modifier.width(140.dp), label = "Scrollback",
            )
        }
        SwitchRow("Keep screen on", "Stop the screen from turning off during a session.", s.keepScreenOn) { v -> store.update { it.copy(keepScreenOn = v) } }
    }
    SettingsCard {
        StackedRow {
            RowText("Extra keys", "Tap a key to show or hide it in the keys bar.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtraKeys.ALL.forEach { k ->
                    Chip(k, on = k in keys, mono = true, minWidth = 52.dp) {
                        val next = if (k in keys) keys - k else keys + k
                        store.update { it.copy(extraKeys = ExtraKeys.ALL.filter { key -> key in next }) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionSettings(s: AppSettings, store: SettingsStore, narrow: Boolean) {
    SettingsCard {
        SwitchRow("Auto-reconnect", "Resume the session if the adapter is unplugged and plugged back in.", s.autoReconnect) { v -> store.update { it.copy(autoReconnect = v) } }
        SwitchRow("Open terminal after Connect", "Go straight to Terminal when a session starts.", s.openTerminal) { v -> store.update { it.copy(openTerminal = v) } }
        WrapRow(narrow, "Initial DTR and RTS", "Lines set when the port opens. Some boards, like ESP32 and Arduino, reset when these change.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("DTR", on = s.dtr, mono = true, minWidth = 52.dp) { store.update { it.copy(dtr = !it.dtr) } }
                Chip("RTS", on = s.rts, mono = true, minWidth = 52.dp) { store.update { it.copy(rts = !it.rts) } }
            }
        }
    }
}

@Composable
private fun AppSettingsCard(s: AppSettings, store: SettingsStore, narrow: Boolean) {
    SettingsCard {
        WrapRow(narrow, "Theme", "Light and System come in a later version. The terminal itself is always dark.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Dark", on = true, mono = false, minWidth = 76.dp, fixedWidth = true) {}
                Chip("Light", on = false, mono = false, minWidth = 76.dp, fixedWidth = true, enabled = false) {}
                Chip("System", on = false, mono = false, minWidth = 76.dp, fixedWidth = true, enabled = false) {}
            }
        }
        SwitchRow("Control lines", "Show DTR, RTS, CTS, DSR, CD, RI and BREAK in the terminal.", s.showSignals) { v -> store.update { it.copy(showSignals = v) } }
        SwitchRow("Button labels", "Show the names under the icons of the bottom bar.", s.navLabels) { v -> store.update { it.copy(navLabels = v) } }
        Row(
            Modifier.fillMaxWidth().topLine().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RowText("Session logging", "Save each session to a file. Coming in a later version.", Modifier.weight(1f).alpha(0.45f))
            NexSwitch(checked = false, onCheckedChange = null, label = "Session logging", enabled = false)
        }
    }
}

@Composable
private fun AboutSettings() {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    SettingsCard {
        InlineRow("NexTerm", "A USB serial terminal for Android.") { MonoValue(version) }
        InlineRow("License", "Free software under the GNU General Public License v3.") { MonoValue("GPL v3") }
    }
    Text(
        "Open-source libraries", fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp, top = 4.dp),
    )
    SettingsCard {
        InlineRow("usb-serial-for-android", "USB serial drivers, by mik3y.") { MonoValue("3.12.0 · MIT") }
        InlineRow("Termux terminal-emulator and terminal-view", "Terminal emulation and display.") { MonoValue("GPL v3") }
        InlineRow("Roboto Mono", "Monospaced font for values like VID:PID.") { MonoValue("Apache 2.0") }
    }
}

// ---- rows and controls ----

/** One card; every row draws a thin line along its top edge (on the first row it sits on the card's own border). */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    NexCard(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun Modifier.topLine(): Modifier {
    val line = MaterialTheme.colorScheme.outlineVariant
    return this.drawBehind { drawLine(line, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
}

/** A row whose content is stacked: 14 dp above and below, 16 dp at the sides. */
@Composable
private fun StackedRow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().topLine().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun RowText(title: String, help: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, fontSize = 15.sp)
        if (help != null) {
            Text(help, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Title and help on the left, the control on the right. */
@Composable
private fun InlineRow(title: String, help: String?, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topLine().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowText(title, help, Modifier.weight(1f))
        control()
    }
}

@Composable
private fun SwitchRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    InlineRow(title, help) { NexSwitch(checked, onChange, label = title) }
}

/** Like [InlineRow], but on phones the control moves under the text. */
@Composable
private fun WrapRow(narrow: Boolean, title: String, help: String?, control: @Composable () -> Unit) {
    if (!narrow) {
        InlineRow(title, help, control)
    } else {
        StackedRow {
            RowText(title, help)
            control()
        }
    }
}

@Composable
private fun MonoValue(text: String) {
    Text(text, fontFamily = NexMono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The chip of the design: 36 dp high, 8 dp corners, green when on. */
@Composable
private fun Chip(
    label: String,
    on: Boolean,
    mono: Boolean,
    minWidth: Dp,
    fixedWidth: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.then(if (fixedWidth) Modifier.width(minWidth) else Modifier.widthIn(min = minWidth)).heightIn(min = 36.dp, max = 36.dp)
            .clip(shape).background(if (on) ChipOn else ChipOff)
            .border(BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), shape)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = if (mono) 12.sp else 13.sp, fontFamily = if (mono) NexMono else null,
            color = (if (on) Green else Color(0xFFC9D1D9)).let { if (enabled) it else it.copy(alpha = 0.45f) },
            maxLines = 1,
        )
    }
}
