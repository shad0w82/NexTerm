package dev.nexterm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nexterm.ui.theme.NexButton
import dev.nexterm.ui.theme.NexOutlinedButton

/**
 * Where dialogs and forms live, as in the mockup: a centered 460 dp card on wide windows (600 dp and up),
 * a card floating at the bottom with side margins, 22 dp corners and a visible border on narrow ones.
 */
@Composable
fun NexDialogHost(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val fill = MaterialTheme.colorScheme.surfaceContainer
    if (cfg.screenWidthDp >= 600) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = fill,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(0.92f).heightIn(max = (cfg.screenHeightDp * 0.92f).dp),
            ) {
                Box(Modifier.padding(20.dp).imePadding()) { content() }
            }
        }
    } else {
        // Narrow windows: a card that floats at the bottom with a small margin on every side and a visible border.
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = fill,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding().imePadding()
                        .heightIn(max = (cfg.screenHeightDp * 0.92f).dp)
                        // Taps inside the card must not reach the dismiss area behind it.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                ) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)) { content() }
                }
            }
        }
    }
}

/** Title, message and one or two buttons; [danger] draws the confirm button as a red outline. */
@Composable
fun NexAlertDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String? = "Cancel",
    danger: Boolean = false,
) {
    NexDialogHost(onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(text, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                if (dismiss != null) NexOutlinedButton(onClick = onDismiss) { Text(dismiss) }
                if (danger) NexOutlinedButton(onClick = onConfirm, danger = true) { Text(confirm) }
                else NexButton(onClick = onConfirm) { Text(confirm) }
            }
        }
    }
}
