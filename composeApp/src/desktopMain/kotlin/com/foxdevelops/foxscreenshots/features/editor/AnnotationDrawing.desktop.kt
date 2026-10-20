package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.foxdevelops.foxscreenshots.platform.image.SkiaAnnotationPainter

actual fun DrawScope.drawAnnotations(base: ImageBitmap?, annotations: List<Annotation>) {
    if (annotations.isEmpty()) return
    val painter = SkiaAnnotationPainter(base)
    drawIntoCanvas { canvas -> painter.paintAll(canvas.nativeCanvas, annotations) }
}
