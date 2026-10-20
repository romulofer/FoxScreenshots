package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap

/** A rendered image together with its PNG bytes, so callers can hand the bytes to the clipboard or a file. */
class ProcessedImage(val image: ImageBitmap, val pngBytes: ByteArray) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}

/**
 * The pixel operations the editor and the capture flow need. Behind an
 * interface so the logic is testable without a rasterizer.
 */
interface ImageProcessor {
    /** Decodes PNG bytes. */
    fun decode(png: ByteArray): ImageBitmap

    /** Trims [base] to [rect] (image pixels), returning the new base image. */
    suspend fun crop(base: ImageBitmap, rect: Rect): ProcessedImage

    /** Draws [base] plus [annotations] at 1:1 and encodes the result as PNG. */
    suspend fun flatten(base: ImageBitmap, annotations: List<Annotation>): ProcessedImage

    /** Frees native memory held by [image] as soon as nothing needs it. */
    fun release(image: ImageBitmap)
}
