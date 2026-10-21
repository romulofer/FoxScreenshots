package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * How far the editor is zoomed in and where it is panned to. Purely a view
 * matter: annotations stay in image pixels, so none of this reaches the export
 * or the undo history.
 *
 * [zoom] is relative to the "fit to window" scale: 1 shows the whole image.
 */
class EditorViewport {
    var zoom by mutableFloatStateOf(1f)
        private set

    /** Shift of the image from its centred position, in widget pixels. */
    var pan by mutableStateOf(Offset.Zero)
        private set

    /** Zooms by [factor] keeping the point under [around] (widget pixels, relative to the viewport centre) where it is. */
    fun zoomBy(factor: Float, around: Offset = Offset.Zero) {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return
        pan = if (newZoom == MIN_ZOOM) Offset.Zero else (pan - around) * (newZoom / zoom) + around
        zoom = newZoom
    }

    fun panBy(delta: Offset) {
        if (zoom > MIN_ZOOM) pan += delta
    }

    /** Back to the whole image, centred. */
    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }

    /** The fit for [imageSize] in [viewport] under the current zoom and pan. */
    fun fit(imageSize: Size, viewport: Size): CanvasFit =
        CanvasFit.contain(imageSize, viewport).zoomed(zoom, pan, viewport)

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 16f

        /** Step of the zoom buttons and shortcuts. */
        const val STEP = 1.25f
    }
}
