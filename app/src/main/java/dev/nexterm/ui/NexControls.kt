package dev.nexterm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexTermTheme

/** Background of an expanded row's details in the mockup. */
internal val DetailsBg = Color(0xFF12161D)

/** Label left, value right in mono, a thin line above every row but the first. */
@Composable
internal fun KeyValue(key: String, value: String, first: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(key, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            // A long value (the list of flow control modes) wraps to a second line, right-aligned, before it is cut.
            Text(
                value, fontFamily = NexMono, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Small round status dot: filled green when [on], hollow grey otherwise. */
@Composable
internal fun LedDot(on: Boolean, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        Modifier.height(size).width(size).let {
            if (on) it.background(NexTermTheme.colors.success, CircleShape)
            else it.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
        },
    )
}

/** "Profile: Name" in a green pill, or a dashed grey "no profile" pill. */
@Composable
internal fun ProfileChip(name: String?, modifier: Modifier = Modifier) {
    if (name != null) {
        Text(
            "Profile: $name", color = Color(0xFF34D399), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = modifier.border(BorderStroke(1.dp, NexTermTheme.colors.success), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
        )
    } else {
        val line = Color(0xFF4B5563)
        Text(
            "no profile", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
            modifier = modifier.drawBehind {
                drawRoundRect(line, cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))))
            }.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

/** A 36 dp select: outlined box with the value and an arrow, menu below. */
@Composable
internal fun <T> SelectField(
    options: List<T>,
    selected: T,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    /** A disabled select shows its value dimmed and does not open. */
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).clip(shape).background(MaterialTheme.colorScheme.background)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape)
                .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = label) { open = true }
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(display(selected), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The menu is a lighter card with a clear border and a shadow, so it stands out from the sheet behind it.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 150.dp),
            shape = RoundedCornerShape(10.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            options.forEach { o ->
                val current = o == selected
                // Own rows instead of DropdownMenuItem, which is at least 48 dp high: 38 dp keeps long lists compact.
                Box(
                    Modifier.fillMaxWidth().height(38.dp)
                        .background(if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable(role = Role.DropdownList) { onSelect(o); open = false }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        display(o), fontSize = 14.sp, fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The dashed "nothing here" box of the empty screens (the mockup's `.empty`): icon, title, text and an
 * optional button, centred, with a 1 dp dashed line and 12 dp corners.
 */
@Composable
internal fun NexEmptyBox(
    icon: Painter,
    title: String,
    text: String,
    wide: Boolean,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val line = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.fillMaxWidth()
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    line, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .padding(horizontal = if (wide) 24.dp else 18.dp, vertical = if (wide) 28.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(40.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(text, fontSize = 13.sp, lineHeight = 19.5.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        if (action != null) Box(Modifier.padding(top = 6.dp)) { action() }
    }
}

/**
 * Body of a screen with nothing to show (Profiles without profiles, Terminal without a session): a blank
 * line as high as Home's "Connected adapters" label, then the dashed box, so the box sits at the same
 * height on every screen.
 */
@Composable
internal fun EmptyScreen(
    icon: Painter,
    title: String,
    text: String,
    wide: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    val side = if (wide) 32.dp else 16.dp
    Column(Modifier.fillMaxWidth().padding(start = side, end = side, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(" ", style = MaterialTheme.typography.labelLarge)
        NexEmptyBox(icon, title, text, wide, action = action)
    }
}
