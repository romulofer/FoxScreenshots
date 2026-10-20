package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import kotlin.math.roundToInt

/**
 * Ink color and stroke width for the next annotation, plus the fill toggle for
 * the rectangle tool (only shown while that tool is selected).
 */
@Composable
fun StyleBar(
    tool: EditorTool,
    color: Color,
    strokeWidth: Float,
    filled: Boolean,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onFilledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(strings.editorColor, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        for (swatch in AnnotationPalette.colors) {
            Swatch(swatch, selected = swatch == color, onSelected = { onColorSelected(swatch) })
        }
        Spacer(Modifier.width(20.dp))
        Text(strings.editorThickness, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = strokeWidth.coerceIn(AnnotationPalette.MIN_STROKE_WIDTH, AnnotationPalette.MAX_STROKE_WIDTH),
            onValueChange = { onStrokeWidthChanged(it.roundToInt().toFloat()) },
            valueRange = AnnotationPalette.MIN_STROKE_WIDTH..AnnotationPalette.MAX_STROKE_WIDTH,
            modifier = Modifier.weight(1f),
        )
        Text(
            strokeWidth.roundToInt().toString(),
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tool == EditorTool.Rectangle) {
            Spacer(Modifier.width(20.dp))
            Text(strings.editorFill, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Switch(checked = filled, onCheckedChange = onFilledChanged)
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onSelected: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Spacer(
        Modifier
            .padding(horizontal = 3.dp)
            .size(24.dp)
            .background(color, CircleShape)
            // A white swatch would vanish on a light surface without the outline.
            .border(if (selected) 3.dp else 1.dp, border, CircleShape)
            .semantics {
                this.selected = selected
                // The color has no name to announce; the value is the swatch itself.
                contentDescription = "#" + (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0')
            }
            .clickable(onClick = onSelected),
    )
}
