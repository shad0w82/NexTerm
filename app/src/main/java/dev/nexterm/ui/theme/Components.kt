package dev.nexterm.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Border of a destructive button in the mockup (#7F1D1D). */
private val DangerBorder = Color(0xFF7F1D1D)

/** Filled green button: 10 dp corners (not a pill), 40 dp high, 36 dp when [small]. */
@Composable
fun NexButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(if (small) 36.dp else 40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        contentPadding = PaddingValues(horizontal = if (small) 14.dp else 18.dp),
    ) { ProvideTextStyle(TextStyle(fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.Medium)) { content() } }
}

/** Outlined button: transparent, 1 dp line, 10 dp corners; [danger] for Delete and the like. */
@Composable
fun NexOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    small: Boolean = false,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(if (small) 36.dp else 40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (danger) DangerBorder else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) MaterialTheme.colorScheme.error else Color(0xFFC9D1D9)),
        contentPadding = PaddingValues(horizontal = if (small) 14.dp else 18.dp),
    ) { ProvideTextStyle(TextStyle(fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.Medium)) { content() } }
}

/** Square outlined button with one icon, [size] wide and high; [label] is what TalkBack reads out. */
@Composable
fun NexIconButton(
    onClick: () -> Unit,
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        // Same look as the mockup's active button: dark green fill, green line and icon.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0F2E25), contentColor = Color(0xFF34D399)),
        contentPadding = PaddingValues(0.dp),
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size(if (size > 52.dp) 30.dp else 28.dp)) }
}

/** Card as drawn in the mockup: surface fill, 1 dp line, 12 dp corners. */
@Composable
fun NexCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val color = MaterialTheme.colorScheme.surfaceContainer
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) { Column(content = content) }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) { Column(content = content) }
    }
}

/** Screen title row: 20 sp medium on phones, 22 sp with wider side margins on wide windows. */
@Composable
fun ScreenHeader(
    title: String,
    wide: Boolean,
    modifier: Modifier = Modifier,
    /** Short windows (phone in landscape): a 48 dp title row with 24 dp side margins. */
    compact: Boolean = false,
    /** Overrides the title size (the session screens use 18 sp on short windows). */
    titleSp: Float? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val side: Dp = if (compact) 24.dp else if (wide) 32.dp else 16.dp
    Row(
        modifier.fillMaxWidth().height(if (compact) 48.dp else if (wide) 72.dp else 56.dp).padding(start = side, end = if (wide) side else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = (titleSp ?: if (wide) 22f else 20f).sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        actions()
    }
}

/**
 * The switch of the design: a 44 x 26 dp track (green when on, #374151 when off) with a 20 dp knob; the touch
 * area is 44 dp high. [label] is what TalkBack reads out.
 */
@Composable
fun NexSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 21.dp else 3.dp, label = "knob")
    Box(
        modifier.size(width = 44.dp, height = 44.dp).alpha(if (enabled) 1f else 0.45f)
            .toggleable(
                value = checked, enabled = enabled && onCheckedChange != null, role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(13.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary else Color(0xFF374151)),
        ) {
            Box(Modifier.offset(x = knobX, y = 3.dp).size(20.dp).background(Color(0xFFE6EDF3), CircleShape))
        }
    }
}
