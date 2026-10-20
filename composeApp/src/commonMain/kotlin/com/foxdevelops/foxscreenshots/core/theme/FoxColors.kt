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
 */
@Immutable
data class FoxColors(
    val appBackground: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val brand: Color,
    val accent: Color,
) {
    companion object {
        val Light = FoxColors(
            appBackground = Color(0xFFFFFCF9),
            surface = Color(0xFFF2EBE5),
            textPrimary = Color(0xFF1B1411),
            textSecondary = Color(0xFF6A5D56),
            brand = Color(0xFFA63F10),
            accent = Color(0xFFA65A00),
        )
        val Dark = FoxColors(
            appBackground = Color(0xFF000000),
            surface = Color(0xFF241C18),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFB8ADA6),
            brand = Color(0xFFD9531E),
            accent = Color(0xFFFFB74D),
        )
    }
}

val LocalFoxColors = staticCompositionLocalOf { FoxColors.Dark }
