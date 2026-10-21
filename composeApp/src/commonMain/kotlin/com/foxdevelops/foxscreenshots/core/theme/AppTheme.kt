package com.foxdevelops.foxscreenshots.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference

/** Material color scheme derived from the [FoxColors] tokens, so the UI never references raw colors. */
fun foxColorScheme(colors: FoxColors, dark: Boolean): ColorScheme {
    val onBrand = colors.onBrand
    val base = if (dark) {
        darkColorScheme(
            primary = colors.brandOnSurface,
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
            primary = colors.brandOnSurface,
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
        // Solid fills, as in the app: a translucent brand over the surface reads as a washed-out pink.
        primaryContainer = colors.brandMuted,
        onPrimaryContainer = colors.textPrimary,
        secondaryContainer = colors.brandMuted,
        onSecondaryContainer = colors.textPrimary,
        outline = colors.textSecondary,
        surfaceContainerHighest = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerLow = colors.surface,
        outlineVariant = colors.textSecondary.copy(alpha = 0.35f),
    )
}

/** The app's corner radii: 16 dp cards, 24 dp dialogs, 12 dp thumbnails. */
val FoxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun FoxTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val colors = if (dark) FoxColors.Dark else FoxColors.Light
    CompositionLocalProvider(LocalFoxColors provides colors) {
        MaterialTheme(colorScheme = foxColorScheme(colors, dark), shapes = FoxShapes, content = content)
    }
}
