package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.Strings

/** Vertical rail of editor tools. Scrollable: the full set is taller than a short window, and a clipped rail would hide the last tool. */
@Composable
fun ToolRail(selected: EditorTool, onSelected: (EditorTool) -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        for (tool in EditorTool.entries) {
            val isSelected = tool == selected
            IconButton(
                onClick = { onSelected(tool) },
                modifier = Modifier.padding(vertical = 2.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(toolIcon(tool), contentDescription = toolLabel(strings, tool), modifier = Modifier.size(24.dp))
            }
        }
    }
}

fun toolIcon(tool: EditorTool): ImageVector = when (tool) {
    EditorTool.Crop -> Icons.Outlined.Crop
    EditorTool.Arrow -> Icons.Outlined.NorthEast
    EditorTool.Rectangle -> Icons.Outlined.Rectangle
    EditorTool.Ellipse -> Icons.Outlined.Circle
    EditorTool.Highlight -> Icons.Outlined.BorderColor
    EditorTool.Blur -> Icons.Outlined.BlurOn
    EditorTool.Pixelate -> Icons.Outlined.GridOn
    EditorTool.Pen -> Icons.Outlined.Gesture
    EditorTool.Text -> Icons.Outlined.TextFields
    EditorTool.Step -> Icons.Outlined.LooksOne
}

fun toolLabel(strings: Strings, tool: EditorTool): String = when (tool) {
    EditorTool.Crop -> strings.toolCrop
    EditorTool.Arrow -> strings.toolArrow
    EditorTool.Rectangle -> strings.toolRectangle
    EditorTool.Ellipse -> strings.toolEllipse
    EditorTool.Highlight -> strings.toolHighlight
    EditorTool.Blur -> strings.toolBlur
    EditorTool.Pixelate -> strings.toolPixelate
    EditorTool.Pen -> strings.toolPen
    EditorTool.Text -> strings.toolText
    EditorTool.Step -> strings.toolNumber
}
