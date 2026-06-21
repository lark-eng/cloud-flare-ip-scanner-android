package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = AccentBlue,
            secondary = AccentCyan,
            tertiary = AccentTeal,
            background = DarkBg,
            surface = CardBg,
            surfaceVariant = SurfaceVariantBg,
            onPrimary = if (AccentBlue.isLight()) Color.Black else Color.White,
            onSecondary = if (AccentCyan.isLight()) Color.Black else Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary
        )
    } else {
        lightColorScheme(
            primary = AccentBlue,
            secondary = AccentCyan,
            tertiary = AccentTeal,
            background = DarkBg,
            surface = CardBg,
            surfaceVariant = SurfaceVariantBg,
            onPrimary = if (AccentBlue.isLight()) Color.Black else Color.White,
            onSecondary = if (AccentCyan.isLight()) Color.Black else Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
