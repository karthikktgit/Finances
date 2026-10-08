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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ledgerline.app.domain.model.ThemeChoice

private val Ink = Color(0xFF1B3A2F)
private val Paper = Color(0xFFF6F3EC)
private val Card = Color(0xFFFFFCF7)
private val InkDark = Color(0xFF101411)
private val CardDark = Color(0xFF1A211D)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFF4F8F5),
    primaryContainer = Color(0xFFD7E8DE),
    onPrimaryContainer = Color(0xFF10241C),
    secondary = Color(0xFF5C5346),
    onSecondary = Color(0xFFFFFBF5),
    secondaryContainer = Color(0xFFE7E0D4),
    onSecondaryContainer = Color(0xFF241E16),
    background = Paper,
    onBackground = Color(0xFF1C1B17),
    surface = Card,
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE7E1D8),
    onSurfaceVariant = Color(0xFF514C44),
    outline = Color(0xFF8A847A),
    error = Color(0xFF8E2432),
    onError = Color(0xFFFFF7F7),
    errorContainer = Color(0xFFF8D9DD),
    onErrorContainer = Color(0xFF3F0A12),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7D8C8),
    onPrimary = Color(0xFF0E241C),
    primaryContainer = Color(0xFF23483A),
    onPrimaryContainer = Color(0xFFD7E8DE),
    secondary = Color(0xFFD9CBB8),
    onSecondary = Color(0xFF2C261C),
    secondaryContainer = Color(0xFF433C32),
    onSecondaryContainer = Color(0xFFE7E0D4),
    background = InkDark,
    onBackground = Color(0xFFE7E4DC),
    surface = CardDark,
    onSurface = Color(0xFFE7E4DC),
    surfaceVariant = Color(0xFF2A332E),
    onSurfaceVariant = Color(0xFFC9C2B6),
    outline = Color(0xFF948D82),
    error = Color(0xFFFFB3B8),
    onError = Color(0xFF680014),
    errorContainer = Color(0xFF6E2230),
    onErrorContainer = Color(0xFFFFD9DC),
)

data class LedgerColors(
    val positive: Color,
    val negative: Color,
    val caution: Color,
)

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerColors(Color(0xFF1B6B45), Color(0xFF8E2432), Color(0xFF8A5A12))
}

private val LedgerType = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun LedgerlineTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
    }
    val extra = if (dark) {
        LedgerColors(Color(0xFF8ED0AE), Color(0xFFFFB3B8), Color(0xFFE6C27A))
    } else {
        LedgerColors(Color(0xFF1B6B45), Color(0xFF8E2432), Color(0xFF8A5A12))
    }
    CompositionLocalProvider(LocalLedgerColors provides extra) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = LedgerType, content = content)
    }
}
