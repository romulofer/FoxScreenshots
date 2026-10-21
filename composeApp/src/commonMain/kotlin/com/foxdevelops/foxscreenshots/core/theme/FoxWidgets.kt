package com.foxdevelops.foxscreenshots.core.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Switch look of the mobile app: brand thumb on a muted brand track when on, quiet grey when off. */
@Composable
fun foxSwitchColors(): SwitchColors {
    val colors = LocalFoxColors.current
    return SwitchDefaults.colors(
        checkedThumbColor = colors.brandOnSurface,
        checkedTrackColor = colors.brandMuted,
        checkedBorderColor = colors.brandMuted,
        uncheckedThumbColor = colors.textSecondary,
        uncheckedTrackColor = colors.surface,
        uncheckedBorderColor = colors.textSecondary,
    )
}

/** Outlined fields as in the mobile app: grey outline at rest, brand outline and label while focused. */
@Composable
fun foxTextFieldColors(): TextFieldColors {
    val colors = LocalFoxColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.brandOnSurface,
        unfocusedBorderColor = colors.textSecondary,
        focusedLabelColor = colors.brandOnSurface,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.brandOnSurface,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )
}
