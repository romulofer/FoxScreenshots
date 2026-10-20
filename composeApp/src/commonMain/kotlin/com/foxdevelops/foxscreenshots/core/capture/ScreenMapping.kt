package com.foxdevelops.foxscreenshots.core.capture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlin.math.max

/**
 * Maps the overlay's canvas pixels onto the frozen screenshot.
 *
 * The overlay shows the screenshot fitted inside its window, centred. When the
 * window covers the virtual screen exactly the fit is 1:1; when a window manager
 * clamps it (or HiDPI scaling differs) the frozen frame is shown shrunk, and the
 * drag still maps back to exact screenshot pixels.
 */
data class ScreenMapping(val imageOrigin: Offset, val imagePixelsPerCanvasPixel: Float) {

    /** Screenshot pixel under a point in overlay coordinates. */
    fun toImage(local: Offset): Offset = imageOrigin + local * imagePixelsPerCanvasPixel

    /** Screenshot region for a rectangle dragged in overlay coordinates, clipped to the screenshot. */
    fun toRegion(a: Offset, b: Offset, imageWidth: Int, imageHeight: Int): CaptureRegion =
        CaptureRegion.fromPoints(toImage(a), toImage(b)).clampedTo(imageWidth, imageHeight)

    companion object {
        val Identity = ScreenMapping(Offset.Zero, 1f)

        /** Fits the whole screenshot inside [window], centred, never cropping. */
        fun fitted(image: Size, window: Size): ScreenMapping {
            if (image.width <= 0f || image.height <= 0f || window.width <= 0f || window.height <= 0f) {
                return Identity
            }
            val scale = max(image.width / window.width, image.height / window.height)
            val drawnWidth = image.width / scale
            val drawnHeight = image.height / scale
            val margin = Offset((window.width - drawnWidth) / 2f, (window.height - drawnHeight) / 2f)
            return ScreenMapping(imageOrigin = -margin * scale, imagePixelsPerCanvasPixel = scale)
        }
    }
}
