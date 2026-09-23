package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = IrumaCyanPrimary,
    onPrimary = Color(0xFF041824),
    primaryContainer = DemonSurfaceElevated,
    onPrimaryContainer = IrumaCyanLight,
    secondary = DemonPurpleSecondary,
    onSecondary = Color(0xFF1E0A38),
    secondaryContainer = Color(0xFF3B1861),
    onSecondaryContainer = DemonPurpleLight,
    tertiary = DemonGoldAccent,
    background = DemonNightDark,
    onBackground = TextPrimary,
    surface = DemonSurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = DemonSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = DividerColor
)

private val LightColorScheme = lightColorScheme(
    primary = IrumaCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF00363A),
    secondary = DemonPurpleDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = DemonGoldAccent,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun IrumaMangaTheme(
    darkTheme: Boolean = true, // default dark mode for authentic manga reading immersion
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
