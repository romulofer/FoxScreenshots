package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.ui.FoxIconButton

/**
 * Vertical rail of editor tools, each with its keyboard shortcut in the tooltip.
 * Scrollable: the full set is taller than a short window, and a clipped rail would hide the last tool.
 */
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
            FoxIconButton(
                icon = toolIcon(tool),
                label = toolLabel(strings, tool),
                shortcut = toolShortcut(tool).letter,
                onClick = { onSelected(tool) },
                modifier = Modifier.padding(vertical = 1.dp),
                buttonSize = 40.dp,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** A tool's keyboard shortcut: the key to match and the letter to show in the tooltip. */
class ToolShortcut(val key: Key, val letter: String)

/** One letter per tool. Pen is P and Pixelate X because B (blur) and the tools' initials collide. */
fun toolShortcut(tool: EditorTool): ToolShortcut = when (tool) {
    EditorTool.Select -> ToolShortcut(Key.V, "V")
    EditorTool.Crop -> ToolShortcut(Key.C, "C")
    EditorTool.Arrow -> ToolShortcut(Key.A, "A")
    EditorTool.Rectangle -> ToolShortcut(Key.R, "R")
    EditorTool.Ellipse -> ToolShortcut(Key.E, "E")
    EditorTool.Highlight -> ToolShortcut(Key.H, "H")
    EditorTool.Blur -> ToolShortcut(Key.B, "B")
    EditorTool.Pixelate -> ToolShortcut(Key.X, "X")
    EditorTool.Pen -> ToolShortcut(Key.P, "P")
    EditorTool.Text -> ToolShortcut(Key.T, "T")
    EditorTool.Step -> ToolShortcut(Key.N, "N")
}

/** The tool whose shortcut is [key], if any. */
fun toolForKey(key: Key): EditorTool? = EditorTool.entries.firstOrNull { toolShortcut(it).key == key }

fun toolIcon(tool: EditorTool): ImageVector = when (tool) {
    EditorTool.Select -> Icons.Outlined.NearMe
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
    EditorTool.Select -> strings.toolSelect
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
