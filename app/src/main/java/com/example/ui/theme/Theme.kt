package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentCyan,
    tertiary = AccentTeal,
    background = DarkBg,
    surface = CardBg,
    surfaceVariant = SurfaceVariantBg,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = AccentCyan,
    tertiary = AccentTeal,
    background = Color(0xFFF7FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF2F7),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF1A202C),
    onSurface = Color(0xFF1A202C),
    onSurfaceVariant = Color(0xFF4A5568)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
