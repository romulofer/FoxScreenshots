package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * Maps between the widget's pixels and the image's own pixels.
 *
 * The editor stores every annotation in image coordinates, so the preview needs
 * exactly one place that knows how the image was letterboxed into the available
 * space — the painter and the gesture handler must agree, or marks land where
 * the user did not click.
 */
data class CanvasFit(val scale: Float, val offset: Offset, val imageSize: Size) {

    /** Where the image is painted inside the widget. */
    val destination: Rect
        get() = Rect(offset.x, offset.y, offset.x + imageSize.width * scale, offset.y + imageSize.height * scale)

    /**
     * Widget-local point to image pixels, clamped to the image: a drag that
     * runs off the edge should stop at the border, not annotate thin air.
     */
    fun toImage(local: Offset): Offset {
        if (scale == 0f) return Offset.Zero
        return Offset(
            ((local.x - offset.x) / scale).coerceIn(0f, imageSize.width),
            ((local.y - offset.y) / scale).coerceIn(0f, imageSize.height),
        )
    }

    companion object {
        /**
         * Largest scale that fits [imageSize] inside [viewport] without cropping,
         * never magnifying past 1:1 (a small capture blown up is just blurry).
         */
        fun contain(imageSize: Size, viewport: Size): CanvasFit {
            if (imageSize.width <= 0f || imageSize.height <= 0f || viewport.width <= 0f || viewport.height <= 0f) {
                return CanvasFit(1f, Offset.Zero, imageSize)
            }
            val scale = minOf(viewport.width / imageSize.width, viewport.height / imageSize.height, 1f)
            return CanvasFit(
                scale = scale,
                offset = Offset((viewport.width - imageSize.width * scale) / 2f, (viewport.height - imageSize.height * scale) / 2f),
                imageSize = imageSize,
            )
        }
    }
}

internal fun minOf(a: Float, b: Float, c: Float): Float = min(min(a, b), c)
