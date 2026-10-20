package com.foxdevelops.foxscreenshots.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An immutable rectangular region of the virtual screen, in physical pixels.
 *
 * Used by the selection overlay and the capture backend. Origin is the
 * top-left of the combined multi-monitor virtual desktop.
 */
data class CaptureRegion(val x: Int, val y: Int, val width: Int, val height: Int) {

    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /**
     * Clips this region to a `boundsWidth` x `boundsHeight` image anchored at
     * the origin. A selection dragged past the edge of the frozen screenshot
     * would make the capture backend read out of bounds, so every crop goes
     * through this first. Returns an empty region when there is no overlap.
     */
    fun clampedTo(boundsWidth: Int, boundsHeight: Int): CaptureRegion {
        val left = max(x, 0)
        val top = max(y, 0)
        val right = min(x + width, boundsWidth)
        val bottom = min(y + height, boundsHeight)
        return CaptureRegion(left, top, max(right - left, 0), max(bottom - top, 0))
    }

    /** Scales this region by [factor], e.g. between overlay and screenshot pixels. */
    fun scaled(factor: Float): CaptureRegion = CaptureRegion(
        x = (x * factor).roundToInt(),
        y = (y * factor).roundToInt(),
        width = (width * factor).roundToInt(),
        height = (height * factor).roundToInt(),
    )

    fun toRect(): Rect = Rect(
        left = x.toFloat(),
        top = y.toFloat(),
        right = (x + width).toFloat(),
        bottom = (y + height).toFloat(),
    )

    override fun toString(): String = "CaptureRegion($x, $y, ${width}x$height)"

    companion object {
        /** Builds a normalized region from two drag corners (any order). */
        fun fromPoints(a: Offset, b: Offset): CaptureRegion = CaptureRegion(
            x = min(a.x, b.x).roundToInt(),
            y = min(a.y, b.y).roundToInt(),
            width = abs(a.x - b.x).roundToInt(),
            height = abs(a.y - b.y).roundToInt(),
        )
    }
}
