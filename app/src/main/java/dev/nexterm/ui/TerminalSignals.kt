package dev.nexterm.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nexterm.session.ActiveSession
import dev.nexterm.session.BREAK_MS
import dev.nexterm.transport.FlowControl
import dev.nexterm.transport.Line
import dev.nexterm.ui.theme.NexMono

private val SigBarBg = Color(0xFF0F1419)
private val BreakLine = Color(0xFF7F1D1D)
private val BreakText = Color(0xFFF87171)
private val ChipOnBg = Color(0xFF0F2E25)

/**
 * The signals of the session, shared by the bar under the header and the compact header: DTR and RTS (tap to
 * change), a separator, the four inputs the device drives (CTS, DSR, CD, RI), whatever [beforeBreak] puts in
 * front of BREAK, and BREAK. A line the adapter cannot handle is dimmed; with RTS/CTS flow control the chip
 * drives RTS itself, so that chip is dimmed too.
 */
@Composable
private fun SignalItems(session: ActiveSession, beforeBreak: @Composable () -> Unit) {
    val context = LocalContext.current
    val lines by session.lines.collectAsStateWithLifecycle()
    val rtsByChip = session.target.config.flowControl == FlowControl.RTS_CTS
    val state = lines.on
    SignalChip("DTR", Line.DTR in state, enabled = Line.DTR in lines.supported) { session.setLine(Line.DTR, Line.DTR !in state) }
    SignalChip("RTS", Line.RTS in state, enabled = Line.RTS in lines.supported && !rtsByChip) { session.setLine(Line.RTS, Line.RTS !in state) }
    Separator()
    listOf(Line.CTS to "CTS", Line.DSR to "DSR", Line.CD to "CD", Line.RI to "RI").forEach { (line, label) ->
        SignalInput(label, line in state, supported = line in lines.supported)
    }
    beforeBreak()
    BreakChip {
        session.sendBreak()
        Toast.makeText(context, "BREAK sent ($BREAK_MS ms)", Toast.LENGTH_SHORT).show()
    }
}

/** The bar under the header: signals on the left, BREAK at the far right (on a narrow screen it wraps to a second line). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignalBar(session: ActiveSession) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val lines by session.lines.collectAsStateWithLifecycle()
    if (lines.supported.isEmpty()) return // a link without control lines (SSH later)
    FlowRow(
        Modifier.fillMaxWidth().background(SigBarBg)
            .drawBehind { drawLine(line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .heightIn(min = 34.dp).padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SignalItems(session) { Spacer(Modifier.weight(1f)) }
    }
}

/** The signals on one line inside the header, for a short screen (phone sideways). Nothing if the link has no lines. */
@Composable
internal fun InlineSignals(session: ActiveSession) {
    val lines by session.lines.collectAsStateWithLifecycle()
    if (lines.supported.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SignalItems(session) { Separator() }
    }
}

@Composable
private fun Separator() {
    Spacer(Modifier.width(1.dp).height(14.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** DTR or RTS: a pill with a lamp that is lit while the line is asserted. */
@Composable
private fun SignalChip(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.height(24.dp).alpha(if (enabled) 1f else 0.45f).clip(shape)
            .background(if (on) ChipOnBg else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else KeyLine, shape)
            .then(if (enabled) Modifier.clickable(role = Role.Switch, onClick = onClick) else Modifier)
            .semantics { contentDescription = label; stateDescription = if (on) "On" else "Off" }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LedDot(on, 7.dp)
        Text(label, fontFamily = NexMono, fontSize = 11.sp, color = if (on) Green else KeyText, maxLines = 1)
    }
}

/** An input driven by the device (CTS, DSR, CD, RI): a lamp and the name, nothing to tap. */
@Composable
private fun SignalInput(label: String, on: Boolean, supported: Boolean) {
    Row(
        Modifier.alpha(if (supported) 1f else 0.45f).semantics { contentDescription = label; stateDescription = if (on) "On" else "Off" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LedDot(on, 7.dp)
        Text(label, fontFamily = NexMono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun BreakChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.height(24.dp).clip(shape).border(1.dp, BreakLine, shape)
            .clickable(role = Role.Button, onClickLabel = "Send BREAK", onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("BREAK", fontFamily = NexMono, fontSize = 11.sp, color = BreakText, maxLines = 1)
    }
}
