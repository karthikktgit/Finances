package com.ledgerline.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ledgerline.app.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerline.app.domain.model.ThemeChoice

// Quiet graphite neutrals with ink-black (light) or soft-white (dark) actions, like good stationery: the money
// colours (green in, rose out, amber caution) and each account's own colour are the only colour on screen.
private val Paper = Color(0xFFF6F6F4)
private val Ink = Color(0xFF141416)
private val Muted = Color(0xFF6B6B73)
private val Line = Color(0xFFDCDCE0)
private val Night = Color(0xFF0C0C0E)
private val NightCard = Color(0xFF151518)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBEBEE),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF55555E),
    onSecondary = Color(0xFFFFFFFF),
    // Selected chips and toggles: a clear neutral tint, so the choice is obvious without shouting.
    secondaryContainer = Color(0xFFE3E3E8),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFA86A0B),
    tertiaryContainer = Color(0xFFF8EBD6),
    onTertiaryContainer = Color(0xFF3D2500),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFA),
    surfaceContainer = Color(0xFFF2F2F3),
    surfaceContainerHigh = Color(0xFFEBEBED),
    surfaceContainerHighest = Color(0xFFE3E3E6),
    outline = Line,
    outlineVariant = Color(0xFFE9E9EC),
    error = Color(0xFFC8414C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE5E7),
    onErrorContainer = Color(0xFF4A0A14),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1F1F3),
    onPrimary = Color(0xFF0C0C0E),
    primaryContainer = Color(0xFF26262B),
    onPrimaryContainer = Color(0xFFF1F1F3),
    secondary = Color(0xFFB4B4BC),
    onSecondary = Color(0xFF1C1C20),
    secondaryContainer = Color(0xFF2C2C32),
    onSecondaryContainer = Color(0xFFF1F1F3),
    tertiary = Color(0xFFE9B162),
    tertiaryContainer = Color(0xFF3A2A12),
    onTertiaryContainer = Color(0xFFF8E6C8),
    background = Night,
    onBackground = Color(0xFFEDEDEF),
    surface = NightCard,
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF1E1E22),
    onSurfaceVariant = Color(0xFF9C9CA5),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF19191C),
    surfaceContainerHigh = Color(0xFF212125),
    surfaceContainerHighest = Color(0xFF2A2A2F),
    outline = Color(0xFF34343A),
    outlineVariant = Color(0xFF242428),
    error = Color(0xFFF07C88),
    onError = Color(0xFF4A0A14),
    errorContainer = Color(0xFF4E1C24),
    onErrorContainer = Color(0xFFFFD9DC),
)

data class LedgerColors(
    val positive: Color,
    val negative: Color,
    val caution: Color,
    val transfer: Color,
    val overdue: Color,
    val safe: Color,
    val scheduled: Color,
    val manual: Color,
    val debt: Color,
)

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerColors(
        Color(0xFF1B6B45),
        Color(0xFF8E2432),
        Color(0xFF8A5A12),
        Color(0xFF3E5C76),
        Color(0xFF6E1020),
        Color(0xFFE07A1F),
        Color(0xFF2F6FED),
        Color(0xFFC43B3B),
        Color(0xFF6E4B9E),
    )
}

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun TextStyle.bundled(size: Int? = null, line: Int? = null, weight: FontWeight? = null, figures: Boolean = false, tracking: Double? = null) = copy(
    fontFamily = Inter,
    fontSize = size?.sp ?: fontSize,
    lineHeight = line?.sp ?: lineHeight,
    fontWeight = weight ?: fontWeight,
    fontFeatureSettings = if (figures) "tnum, cv11" else "cv11",
    letterSpacing = tracking?.sp ?: 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
    ),
)

private val stock = Typography()

// Inter, set a little tight at display sizes and with tabular figures wherever amounts appear, so columns of money
// line up. Big numbers are semibold rather than heavy; titles are bold enough to scan.
private val LedgerType = Typography(
    displayLarge = stock.displayLarge.bundled(tracking = -1.0),
    displayMedium = stock.displayMedium.bundled(tracking = -0.8),
    displaySmall = stock.displaySmall.bundled(34, 40, FontWeight.SemiBold, figures = true, tracking = -0.8),
    headlineLarge = stock.headlineLarge.bundled(tracking = -0.6),
    headlineMedium = stock.headlineMedium.bundled(27, 33, FontWeight.SemiBold, figures = true, tracking = -0.6),
    headlineSmall = stock.headlineSmall.bundled(24, 30, FontWeight.Bold, tracking = -0.5),
    titleLarge = stock.titleLarge.bundled(19, 25, FontWeight.SemiBold, figures = true, tracking = -0.3),
    titleMedium = stock.titleMedium.bundled(16, 22, FontWeight.SemiBold, tracking = -0.15),
    titleSmall = stock.titleSmall.bundled(15, 20, FontWeight.Medium, tracking = -0.1),
    bodyLarge = stock.bodyLarge.bundled(16, 22, FontWeight.Normal, figures = true, tracking = -0.1),
    bodyMedium = stock.bodyMedium.bundled(14, 20, FontWeight.Normal, figures = true),
    bodySmall = stock.bodySmall.bundled(13, 18, FontWeight.Normal, figures = true),
    labelLarge = stock.labelLarge.bundled(14, 18, FontWeight.SemiBold),
    labelMedium = stock.labelMedium.bundled(12, 16, FontWeight.Medium, tracking = 0.1),
    labelSmall = stock.labelSmall.bundled(11, 14, FontWeight.Medium, tracking = 0.2),
)

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LedgerlineTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
    }
    val extra = if (dark) {
        LedgerColors(
            Color(0xFF5CC994), Color(0xFFF07C88), Color(0xFFE9B162), Color(0xFFA3AEC2), Color(0xFFF06A78), Color(0xFFF0A35E),
            Color(0xFF86A8F0), Color(0xFFF07C88), Color(0xFFB4A0EE),
        )
    } else {
        LedgerColors(
            Color(0xFF1E8A57), Color(0xFFC8414C), Color(0xFFB0700A), Color(0xFF5E6878), Color(0xFFA62B3A), Color(0xFFD9822B),
            Color(0xFF3A6FD8), Color(0xFFC8414C), Color(0xFF7A5BD0),
        )
    }
    CompositionLocalProvider(LocalLedgerColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = LedgerType,
            shapes = LedgerShapes,
            content = content,
        )
    }
}
