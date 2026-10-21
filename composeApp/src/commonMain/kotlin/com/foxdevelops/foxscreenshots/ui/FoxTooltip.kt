package com.foxdevelops.foxscreenshots.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shows [text] in a small bubble while the pointer rests on [content] (or the keyboard focuses it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * An icon-only button that always explains itself: the [label] is both the
 * accessibility description and the hover tooltip, followed by the keyboard
 * [shortcut] when there is one.
 */
@Composable
fun FoxIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shortcut: String? = null,
    tint: Color = Color.Unspecified,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    iconSize: Dp = 24.dp,
    buttonSize: Dp? = null,
    /** Accessibility name, when the tooltip says more than the plain label. */
    description: String = label,
) {
    FoxTooltip(if (shortcut == null) label else "$label ($shortcut)") {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            modifier = if (buttonSize != null) modifier.size(buttonSize) else modifier,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
