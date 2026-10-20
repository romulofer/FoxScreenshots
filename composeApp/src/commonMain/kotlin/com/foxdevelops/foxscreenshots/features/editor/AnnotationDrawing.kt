package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws [annotations] in image coordinates onto the current draw scope — the
 * same painter the export uses, so the preview is exactly what gets written.
 * [base] is what redactions re-draw through a blur or a mosaic.
 */
expect fun DrawScope.drawAnnotations(base: ImageBitmap?, annotations: List<Annotation>)
