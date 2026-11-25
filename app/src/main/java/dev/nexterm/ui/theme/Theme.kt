package dev.nexterm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nexterm.R
import com.termux.terminal.TerminalColors
import java.util.Properties

/**
 * NexTerm palette: near-black neutrals with an emerald accent (chosen by the user), plus
 * red/amber/blue for status. Neutral and accent values follow the Tailwind scales.
 */
object Palette {
    // Dark neutrals
    val Ink950 = Color(0xFF0D1117) // deepest surface
    val TerminalBg = Color(0xFF0A0D12) // the terminal area, a step darker than the page
    val Ink900 = Color(0xFF0F1419) // app background
    val Ink850 = Color(0xFF151A1F)
    val Ink800 = Color(0xFF1A1D21) // cards
    val Ink750 = Color(0xFF1F2328)
    val Ink700 = Color(0xFF23272E) // raised surfaces, chips
    val Ink600 = Color(0xFF374151) // outlines
    val Gray500 = Color(0xFF6B7280) // muted text
    val Gray400 = Color(0xFF9CA3AF) // secondary text on dark
    val Gray300 = Color(0xFFD1D5DB)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray50 = Color(0xFFF9FAFB)
    val Gray900 = Color(0xFF111827)
    val Gray600 = Color(0xFF4B5563)
    val Snow = Color(0xFFFAFAFA)

    // Accents
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF059669)
    val EmeraldDeep = Color(0xFF0F3D33)
    val Red = Color(0xFFEF4444)
    val RedDark = Color(0xFFDC2626)
    val Amber = Color(0xFFF59E0B)
    val AmberDark = Color(0xFFD97706)
    val Blue = Color(0xFF3B82F6)
    val BlueDark = Color(0xFF2563EB)
}

/** Dark scheme taken from the design mockup: page #0D1117, cards #161B22, rail #0A0D12, lines #23272E / #30363D. */
private val DarkColors: ColorScheme = darkColorScheme(
    primary = Palette.Emerald, onPrimary = Color(0xFF06130E),
    primaryContainer = Color(0xFF12201C), onPrimaryContainer = Color(0xFF34D399),
    inversePrimary = Palette.EmeraldDark,
    secondary = Palette.Blue, onSecondary = Color.White,
    secondaryContainer = Color(0xFF142139), onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = Palette.Amber, onTertiary = Color(0xFF1F1300),
    tertiaryContainer = Color(0xFF302616), onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171), onError = Color(0xFF2B0A0A),
    errorContainer = Color(0xFF3B1517), onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF0D1117), onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117), onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF23272E), onSurfaceVariant = Color(0xFF8B949E),
    surfaceTint = Palette.Emerald,
    inverseSurface = Color(0xFFE6EDF3), inverseOnSurface = Color(0xFF0D1117),
    outline = Color(0xFF30363D), outlineVariant = Color(0xFF23272E),
    scrim = Color.Black,
    surfaceDim = Color(0xFF0A0D12), surfaceBright = Color(0xFF23272E),
    surfaceContainerLowest = Color(0xFF0A0D12), surfaceContainerLow = Color(0xFF0F1419),
    surfaceContainer = Color(0xFF161B22), surfaceContainerHigh = Color(0xFF1A1F26),
    surfaceContainerHighest = Color(0xFF23272E),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.EmeraldDark, onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5), onPrimaryContainer = Color(0xFF064E3B),
    inversePrimary = Palette.Emerald,
    secondary = Palette.BlueDark, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE), onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Palette.AmberDark, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7), onTertiaryContainer = Color(0xFF78350F),
    error = Palette.RedDark, onError = Color.White,
    errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF7F1D1D),
    background = Palette.Gray50, onBackground = Palette.Gray900,
    surface = Palette.Gray50, onSurface = Palette.Gray900,
    surfaceVariant = Palette.Gray100, onSurfaceVariant = Palette.Gray600,
    surfaceTint = Palette.EmeraldDark,
    inverseSurface = Palette.Ink800, inverseOnSurface = Palette.Snow,
    outline = Palette.Gray300, outlineVariant = Palette.Gray200,
    scrim = Color.Black,
    surfaceDim = Palette.Gray200, surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Palette.Gray100, surfaceContainerHigh = Palette.Gray100,
    // Filled cards use this slot: keep them light grey, not mid grey, on the near-white background.
    surfaceContainerHighest = Palette.Gray100,
)

/** Colours Material's scheme has no slot for. */
@Immutable
data class NexTermColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    /** Terminal and its chrome stay dark in both themes. */
    val terminalBackground: Color,
    val terminalForeground: Color,
    val terminalChrome: Color,
    val terminalChromeContent: Color,
    val terminalChromeMuted: Color,
    val rx: Color,
    val tx: Color,
    val sys: Color,
)

private val TerminalSide = NexTermColors(
    success = Palette.Emerald, warning = Palette.Amber, info = Palette.Blue,
    terminalBackground = Palette.TerminalBg, terminalForeground = Color(0xFFE6EDF3),
    terminalChrome = Palette.Ink800, terminalChromeContent = Palette.Snow, terminalChromeMuted = Palette.Gray400,
    rx = Palette.Emerald, tx = Palette.Blue, sys = Palette.Amber,
)

val LocalNexTermColors = staticCompositionLocalOf { TerminalSide }

/** Roboto Mono, as in the mockup (Apache 2.0). Used for baud rates, VID:PID, paths and the like. */
val NexMono = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** Sizes from the mockup: 20 for screen titles, 14 for body, 13 for labels, 12 for captions, 11 for bar labels. */
private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/**
 * App theme. Dark for now, like the design (light and system modes come with the Settings screen); the terminal itself is always dark.
 * Dynamic (wallpaper) colour is deliberately off so the brand palette is used everywhere.
 */
@Composable
fun NexTermTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val extra = if (darkTheme) TerminalSide else TerminalSide.copy(
        success = Palette.EmeraldDark, warning = Palette.AmberDark, info = Palette.BlueDark,
    )
    CompositionLocalProvider(LocalNexTermColors provides extra) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, shapes = AppShapes, typography = AppTypography, content = content)
    }
}

/** Shortcut: `NexTermTheme.colors.success` etc. */
object NexTermTheme {
    val colors: NexTermColors @Composable get() = LocalNexTermColors.current
}

/**
 * Terminal palette for the Termux engine: background/foreground/cursor plus the 16 ANSI colours,
 * tuned to the app palette. Applies to every emulator created afterwards (and to reset()).
 */
object TerminalPalette {
    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    fun install() {
        val ansi = listOf(
            "#4B5563", "#EF4444", "#10B981", "#F59E0B", "#3B82F6", "#A855F7", "#06B6D4", "#D1D5DB",
            "#9CA3AF", "#F87171", "#34D399", "#FBBF24", "#60A5FA", "#C084FC", "#22D3EE", "#FAFAFA",
        )
        val p = Properties().apply {
            setProperty("background", hex(Palette.TerminalBg))
            setProperty("foreground", hex(TerminalSide.terminalForeground))
            setProperty("cursor", hex(Palette.Emerald))
            ansi.forEachIndexed { i, c -> setProperty("color$i", c) }
        }
        TerminalColors.COLOR_SCHEME.updateWith(p)
    }
}
