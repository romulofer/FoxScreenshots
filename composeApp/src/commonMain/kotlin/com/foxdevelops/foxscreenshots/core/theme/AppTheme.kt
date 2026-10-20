package com.foxdevelops.foxscreenshots.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference

/** Material color scheme derived from the [FoxColors] tokens, so the UI never references raw colors. */
fun foxColorScheme(colors: FoxColors, dark: Boolean): ColorScheme {
    val onBrand = Color.White
    val base = if (dark) {
        darkColorScheme(
            primary = colors.brand,
            onPrimary = onBrand,
            secondary = colors.accent,
            onSecondary = Color.Black,
            background = colors.appBackground,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
        )
    } else {
        lightColorScheme(
            primary = colors.brand,
            onPrimary = onBrand,
            secondary = colors.accent,
            onSecondary = Color.White,
            background = colors.appBackground,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
        )
    }
    return base.copy(
        primaryContainer = colors.brand.copy(alpha = if (dark) 0.35f else 0.18f),
        onPrimaryContainer = colors.textPrimary,
        secondaryContainer = colors.accent.copy(alpha = if (dark) 0.28f else 0.20f),
        onSecondaryContainer = colors.textPrimary,
        surfaceContainerHighest = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerLow = colors.surface,
        outlineVariant = colors.textSecondary.copy(alpha = 0.35f),
    )
}

@Composable
fun FoxTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val colors = if (dark) FoxColors.Dark else FoxColors.Light
    CompositionLocalProvider(LocalFoxColors provides colors) {
        MaterialTheme(colorScheme = foxColorScheme(colors, dark), content = content)
    }
}
