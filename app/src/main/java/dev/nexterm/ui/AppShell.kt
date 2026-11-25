package dev.nexterm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.R
import dev.nexterm.ui.theme.NexTermTheme

/** The four top-level destinations, shown as a bottom bar or a left rail. */
enum class Tab(val label: String) {
    Home("Home"),
    Terminal("Terminal"),
    Profiles("Profiles"),
    Settings("Settings"),
}

/** The layout follows the window width, not the kind of device: from this width a rail replaces the bar. */
private val RAIL_MIN_WIDTH = 600.dp
/** Under this height (phone in landscape) the rail is narrower. */
private val SHORT_MAX_HEIGHT = 500.dp
private val RAIL_WIDTH = 80.dp
private val RAIL_WIDTH_SHORT = 64.dp
private val BAR_ITEM_HEIGHT = 64.dp
/** With only icons the bar and the rail items are lower. */
private val BAR_ITEM_HEIGHT_ICONS = 56.dp

/**
 * App shell: bottom bar under 600 dp wide, navigation rail from 600 dp. It owns the system-bar insets
 * so the screens inside don't. The keyboard may cover the bottom bar: the content area only gives up
 * the part of the keyboard that is above the bar.
 */
@Composable
fun NexShell(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    /** A session is open and connected: Home shows a green dot, like the LED in the design. */
    sessionLive: Boolean,
    /** Terminal in full screen: the rail or bar goes away and the content gets the whole window. */
    fullScreen: Boolean = false,
    /** The names under the icons (the Button labels setting); without them the bar is lower. */
    showLabels: Boolean = true,
    content: @Composable () -> Unit,
) {
    val itemHeight = if (showLabels) BAR_ITEM_HEIGHT else BAR_ITEM_HEIGHT_ICONS
    val barColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val chrome = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= RAIL_MIN_WIDTH) {
            val railWidth = if (maxHeight < SHORT_MAX_HEIGHT) RAIL_WIDTH_SHORT else RAIL_WIDTH
            Row(Modifier.fillMaxSize()) {
                if (!fullScreen) Column(
                    Modifier.fillMaxHeight().background(barColor)
                        .windowInsetsPadding(chrome.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                        .width(railWidth),
                ) {
                    Spacer(Modifier.height(8.dp))
                    NavItem(Tab.Home, selected == Tab.Home, sessionLive, rail = true, onClick = { onSelect(Tab.Home) }, modifier = Modifier.fillMaxWidth().height(itemHeight), showLabel = showLabels)
                    NavItem(Tab.Terminal, selected == Tab.Terminal, false, rail = true, onClick = { onSelect(Tab.Terminal) }, modifier = Modifier.fillMaxWidth().height(itemHeight), showLabel = showLabels)
                    NavItem(Tab.Profiles, selected == Tab.Profiles, false, rail = true, onClick = { onSelect(Tab.Profiles) }, modifier = Modifier.fillMaxWidth().height(itemHeight), showLabel = showLabels)
                    Spacer(Modifier.weight(1f))
                    NavItem(Tab.Settings, selected == Tab.Settings, false, rail = true, onClick = { onSelect(Tab.Settings) }, modifier = Modifier.fillMaxWidth().height(itemHeight), showLabel = showLabels)
                    Spacer(Modifier.height(8.dp))
                }
                if (!fullScreen) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .windowInsetsPadding(if (fullScreen) chrome else chrome.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)),
                ) { content() }
            }
        } else {
            val density = LocalDensity.current
            var barPx by remember { mutableIntStateOf(0) }
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .windowInsetsPadding(if (fullScreen) chrome else chrome.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        // The bar sits under the content; tell the screens inside that the bottom bar's
                        // height of the keyboard (and gesture bar) is already accounted for.
                        .consumeWindowInsets(PaddingValues(bottom = if (fullScreen) 0.dp else with(density) { barPx.toDp() })),
                ) { content() }
                if (!fullScreen) Column(Modifier.fillMaxWidth().background(barColor).onSizeChanged { barPx = it.height }) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().height(itemHeight)
                            .windowInsetsPadding(chrome.only(WindowInsetsSides.Horizontal)),
                    ) {
                        Tab.entries.forEach { t ->
                            NavItem(t, selected == t, t == Tab.Home && sessionLive, rail = false, onClick = { onSelect(t) }, modifier = Modifier.weight(1f).fillMaxHeight(), showLabel = showLabels)
                        }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Boolean,
    live: Boolean,
    rail: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (selected) cs.primary else cs.onSurfaceVariant
    Box(
        modifier
            .background(if (rail && selected) Color(0xFF10201C) else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .drawBehind {
                if (rail && selected) {
                    drawRect(cs.primary, topLeft = Offset(0f, 12.dp.toPx()), size = Size(3.dp.toPx(), size.height - 24.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box {
                Icon(tab.painter(), contentDescription = if (showLabel) null else tab.label, tint = tint, modifier = Modifier.size(24.dp))
                if (live) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-1).dp).size(8.dp)
                            .background(NexTermTheme.colors.success, CircleShape),
                    )
                }
            }
            if (showLabel) Text(tab.label, color = tint, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Tab.painter(): Painter = when (this) {
    Tab.Home -> painterResource(R.drawable.ic_nav_home)
    Tab.Terminal -> painterResource(R.drawable.ic_nav_terminal)
    Tab.Profiles -> painterResource(R.drawable.ic_nav_profiles)
    Tab.Settings -> painterResource(R.drawable.ic_nav_settings)
}
