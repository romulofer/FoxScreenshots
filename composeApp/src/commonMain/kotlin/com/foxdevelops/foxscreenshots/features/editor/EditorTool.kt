package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.graphics.Color

/** The tools on the editor rail. */
enum class EditorTool {
    /** Trims the base image itself — the one destructive tool. */
    Crop,
    Arrow,
    Rectangle,
    Ellipse,
    Highlight,
    Blur,
    Pixelate,
    Pen,
    Text,
    Step;

    /** Whether the tool is driven by dragging out a shape, as opposed to a single tap that places something. */
    val isDragTool: Boolean get() = this != Text && this != Step

    /** Whether the tool adds an annotation layer. [Crop] does not: it rewrites the base image. */
    val createsAnnotation: Boolean get() = this != Crop
}

/**
 * Ink colors offered by the editor. These are *content*, not chrome: they end
 * up baked into the exported PNG, so they must not follow the light/dark theme.
 */
object AnnotationPalette {
    val Red = Color(0xFFE53935)
    val Orange = Color(0xFFD9531E)
    val Yellow = Color(0xFFFFC107)
    val Green = Color(0xFF43A047)
    val Blue = Color(0xFF1E88E5)
    val Black = Color(0xFF111111)
    val White = Color(0xFFFFFFFF)

    val colors = listOf(Red, Orange, Yellow, Green, Blue, Black, White)

    /** Stroke widths in image pixels, from hairline to marker. */
    const val MIN_STROKE_WIDTH = 1f
    const val MAX_STROKE_WIDTH = 24f
}
