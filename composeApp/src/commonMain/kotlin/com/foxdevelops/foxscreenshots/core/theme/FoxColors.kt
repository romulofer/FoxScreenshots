package com.foxdevelops.foxscreenshots.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand color tokens ported from `~/development/mobile/foxdevelops`
 * (`values/colors.xml` + `values-night`). Never reference raw hex outside this
 * file.
 *
 * On light backgrounds [brand] is the darker `#A63F10`: the orange `#D9531E`
 * fails 4.5:1 on white.
 *
 * [brandMuted] is the solid, low-key brand fill (`fox_red_muted` in the app: the
 * checked switch track, selected chips). The app only defines it for the dark
 * theme; the light value is the brand tinted into the cream background.
 */
@Immutable
data class FoxColors(
    val appBackground: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val brand: Color,
    val accent: Color,
    val brandMuted: Color,
    /** Brand shade for text, outlines and filled buttons on [surface]/[appBackground]: at least 4.5:1 there. */
    val brandOnSurface: Color,
    /** Text/icon color on a [brandOnSurface] fill. */
    val onBrand: Color,
    /** Dimming laid over a frozen screen or a crop. */
    val scrim: Color,
    /** Darker pill behind overlay text, so it reads on any wallpaper. */
    val scrimStrong: Color,
    val onScrim: Color,
) {
    companion object {
        val Light = FoxColors(
            appBackground = Color(0xFFFFFCF9),
            surface = Color(0xFFF2EBE5),
            textPrimary = Color(0xFF1B1411),
            textSecondary = Color(0xFF6A5D56),
            brand = Color(0xFFA63F10),
            accent = Color(0xFFA65A00),
            brandMuted = Color(0xFFF1DED4),
            brandOnSurface = Color(0xFFA63F10),
            onBrand = Color(0xFFFFFFFF),
            scrim = Color(0x8C000000),
            scrimStrong = Color(0xCC000000),
            onScrim = Color(0xFFFFFFFF),
        )
        val Dark = FoxColors(
            appBackground = Color(0xFF000000),
            surface = Color(0xFF241C18),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFB8ADA6),
            brand = Color(0xFFD9531E),
            accent = Color(0xFFFFB74D),
            brandMuted = Color(0xFF6B2A16),
            // `#D9531E` is 4.15:1 on the dark surface and takes 4.03:1 with white text; this lighter step clears 4.5:1 both ways.
            brandOnSurface = Color(0xFFDC602F),
            onBrand = Color(0xFF000000),
            scrim = Color(0x8C000000),
            scrimStrong = Color(0xCC000000),
            onScrim = Color(0xFFFFFFFF),
        )
    }
}

val LocalFoxColors = staticCompositionLocalOf { FoxColors.Dark }
