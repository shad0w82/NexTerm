package dev.nexterm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.session.HISTORY_SECONDS
import dev.nexterm.session.SessionStats
import dev.nexterm.session.StatsSnapshot
import dev.nexterm.ui.theme.NexCard
import dev.nexterm.ui.theme.NexMono
import dev.nexterm.ui.theme.NexTermTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Sizes of the tiles and the traffic card: phone upright, wide windows, and short windows (phone sideways). */
internal enum class StatLayout(
    val gap: Dp,
    val tileH: Dp, val tileV: Dp, val keySp: Float, val valueSp: Float, val valueTop: Dp,
    val cardH: Dp, val cardTop: Dp, val cardBottom: Dp, val chartHeight: Dp, val chartTop: Dp, val axisSp: Float, val axisTop: Dp,
) {
    PHONE(8.dp, 12.dp, 8.dp, 12f, 18f, 0.dp, 14.dp, 10.dp, 10.dp, 60.dp, 6.dp, 11f, 6.dp),
    WIDE(12.dp, 16.dp, 14.dp, 12f, 20f, 2.dp, 16.dp, 14.dp, 12.dp, 120.dp, 10.dp, 11f, 6.dp),
    COMPACT(8.dp, 12.dp, 8.dp, 11f, 16f, 0.dp, 12.dp, 8.dp, 8.dp, 84.dp, 6.dp, 10f, 4.dp),
}

/** The statistics as the UI should show them: refreshed every second while [live], frozen once the session is over. */
@Composable
internal fun rememberStatsSnapshot(stats: SessionStats, live: Boolean, version: Int = 0): State<StatsSnapshot> =
    produceState(initialValue = stats.snapshot(), stats, live, version) {
        value = stats.snapshot()
        while (live) {
            delay(1000)
            value = stats.snapshot()
        }
    }

// ---- formatting ----

internal fun fmtBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(Locale.US, n / 1024.0)
    else -> "%.1f MB".format(Locale.US, n / 1048576.0)
}

internal fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

private fun fmtAgo(ms: Long?): String = when {
    ms == null -> "—"
    ms < 2000 -> "now"
    ms < 60_000 -> "${ms / 1000} s ago"
    ms < 3_600_000 -> "${ms / 60_000} min ago"
    else -> "${ms / 3_600_000} h ago"
}

private fun fmtClock(time: Long?): String = if (time == null) "—" else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))

// ---- tiles ----

private class Tile(val key: String, val value: String, val bad: Boolean = false)

/** The six statistic tiles in [columns] columns. An ended session shows "Ended" (a clock time) instead of "Last activity". */
@Composable
internal fun StatTiles(s: StatsSnapshot, ended: Boolean, columns: Int, layout: StatLayout) {
    val tiles = listOf(
        Tile("Duration", fmtDuration(s.durationMs)),
        Tile("Received", fmtBytes(s.rx)),
        Tile("Sent", fmtBytes(s.tx)),
        Tile("Receive speed", if (ended) "—" else fmtBytes(s.rxSpeed.toLong()) + "/s"),
        Tile("Errors", s.errors.toString(), bad = s.errors > 0),
        if (ended) Tile("Ended", fmtClock(s.endedAt)) else Tile("Last activity", fmtAgo(s.sinceActivityMs)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(layout.gap)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(layout.gap)) {
                row.forEach { StatTile(it, layout, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatTile(t: Tile, layout: StatLayout, modifier: Modifier) {
    NexCard(modifier) {
        Column(Modifier.padding(horizontal = layout.tileH, vertical = layout.tileV)) {
            Text(t.key, fontSize = layout.keySp.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                t.value, fontFamily = NexMono, fontWeight = FontWeight.Medium, fontSize = layout.valueSp.sp,
                color = if (t.bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, modifier = Modifier.padding(top = layout.valueTop),
            )
        }
    }
}

// ---- traffic chart ----

/** Smallest full scale of each half of the chart in bytes per second, so a trickle does not fill it. */
private const val MIN_RX_SCALE = 64
private const val MIN_TX_SCALE = 16

/**
 * "Traffic, last 60 s": one column per second, received bytes going up from the middle line in green and
 * sent bytes going down in blue, each with its own scale. An ended session shows its last minute dimmed.
 */
@Composable
internal fun TrafficCard(s: StatsSnapshot, legend: Boolean, dim: Boolean, layout: StatLayout) {
    val rxColor = NexTermTheme.colors.rx
    val txColor = NexTermTheme.colors.tx
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    NexCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = layout.cardH, end = layout.cardH, top = layout.cardTop, bottom = layout.cardBottom)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Traffic, last 60 s", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (legend) {
                    LegendItem(rxColor, "Received")
                    Spacer(Modifier.width(14.dp))
                    LegendItem(txColor, "Sent")
                }
            }
            Spacer(Modifier.height(layout.chartTop))
            // Each direction has its own scale, so the few bytes you type stay visible next to a busy receive line.
            val rxScale = max(MIN_RX_SCALE, s.rxHistory.maxOrNull() ?: 0).toFloat()
            val txScale = max(MIN_TX_SCALE, s.txHistory.maxOrNull() ?: 0).toFloat()
            Canvas(Modifier.fillMaxWidth().height(layout.chartHeight).alpha(if (dim) 0.5f else 1f)) {
                val gap = 2.dp.toPx()
                val barW = (size.width - gap * (HISTORY_SECONDS - 1)) / HISTORY_SECONDS
                val mid = size.height / 2
                val corner = CornerRadius(1.dp.toPx())
                for (i in 0 until HISTORY_SECONDS) {
                    val x = i * (barW + gap)
                    val up = s.rxHistory[i]
                    if (up > 0) {
                        val h = (up / rxScale * mid).coerceIn(1.dp.toPx(), mid)
                        drawRoundRect(rxColor, Offset(x, mid - h), Size(barW, h), corner)
                    }
                    val down = s.txHistory[i]
                    if (down > 0) {
                        val h = (down / txScale * mid).coerceIn(1.dp.toPx(), mid)
                        drawRoundRect(txColor, Offset(x, mid), Size(barW, h), corner)
                    }
                }
            }
            if (legend) {
                Row(Modifier.fillMaxWidth().padding(top = layout.axisTop), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("-60 s", "-30 s", "now").forEach { Text(it, fontFamily = NexMono, fontSize = layout.axisSp.sp, color = muted) }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- notice and chips ----

/** The amber banner above an ended session: why it ended, with a Dismiss link. */
@Composable
internal fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    val amber = Color(0xFFF5C969)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A210A))
            .border(BorderStroke(1.dp, Color(0xFF6B4E0B)), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, fontSize = 13.sp, color = amber, modifier = Modifier.weight(1f))
        Text(
            "Dismiss", fontSize = 13.sp, color = amber, textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(role = Role.Button, onClick = onDismiss),
        )
    }
}
