package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * One non-destructive mark on top of a capture.
 *
 * Coordinates are **image pixels**, never screen pixels: the same annotation
 * list drives the on-screen preview (scaled to fit the window) and the
 * full-resolution export, so what the user sees is what gets written.
 *
 * Immutable — editing produces a new instance, which keeps the undo stack cheap.
 */
sealed class Annotation {
    abstract val id: String
    abstract val color: Color

    /** Stroke width in image pixels. */
    abstract val strokeWidth: Float

    /** The annotation as it should look with the pointer now at [point]. */
    abstract fun dragTo(point: Offset): Annotation

    /** Whether the finished gesture is worth keeping. */
    abstract val isMeaningful: Boolean

    /** Painted extent, stroke included. */
    abstract val bounds: Rect

    /** The same annotation moved by [shift]; used when a crop re-origins the image and when the user drags it. */
    abstract fun translated(shift: Offset): Annotation

    /**
     * Whether a click at [point] (image pixels) grabs this annotation, with
     * [tolerance] of slack so a thin line is still easy to hit. The default is the
     * painted box; shapes that are mostly empty inside override it.
     */
    open fun hitTest(point: Offset, tolerance: Float): Boolean = bounds.inflate(tolerance).contains(point)

    companion object {
        /** Drags shorter than this are a stray click, not a shape. */
        const val MIN_DRAG_EXTENT = 4f
    }
}

/** Distance from [p] to the segment [a]–[b]. */
fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lengthSquared = ab.x * ab.x + ab.y * ab.y
    if (lengthSquared == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lengthSquared).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/** Normalized rectangle spanned by two corners, in any drag direction. */
fun rectFromPoints(a: Offset, b: Offset): Rect =
    Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

/** Shared base for the annotations defined by two drag corners. */
sealed class ShapeAnnotation : Annotation() {
    abstract val start: Offset
    abstract val end: Offset

    val rect: Rect get() = rectFromPoints(start, end)

    override val bounds: Rect get() = rect.inflate(strokeWidth)

    override val isMeaningful: Boolean
        get() = rect.width >= MIN_DRAG_EXTENT || rect.height >= MIN_DRAG_EXTENT
}

/** Straight line with a solid head at [end]. */
data class ArrowAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    override val start: Offset,
    override val end: Offset,
) : ShapeAnnotation() {
    /** Head length as a multiple of the stroke width, so a thick arrow keeps its proportions. */
    val headLength: Float get() = strokeWidth * HEAD_SCALE

    override fun dragTo(point: Offset) = copy(end = point)
    override fun translated(shift: Offset) = copy(start = start + shift, end = end + shift)

    /** An arrow is about its length, not its box: a perfectly horizontal one has zero height and still counts. */
    override val isMeaningful: Boolean get() = (end - start).getDistance() >= MIN_DRAG_EXTENT

    override val bounds: Rect get() = rect.inflate(headLength)

    override fun hitTest(point: Offset, tolerance: Float): Boolean =
        distanceToSegment(point, start, end) <= max(strokeWidth * 1.5f, headLength / 2f) + tolerance

    companion object {
        const val HEAD_SCALE = 4f
    }
}

/** Rectangle outline, or a solid block when [filled]. */
data class RectangleAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    override val start: Offset,
    override val end: Offset,
    val filled: Boolean = false,
) : ShapeAnnotation() {
    override fun dragTo(point: Offset) = copy(end = point)
    override fun translated(shift: Offset) = copy(start = start + shift, end = end + shift)

    /** An outline is grabbed by its edge, so the shapes underneath stay reachable; a filled block by its whole area. */
    override fun hitTest(point: Offset, tolerance: Float): Boolean {
        if (filled) return super.hitTest(point, tolerance)
        val reach = strokeWidth / 2f + tolerance
        val inner = rect.deflate(reach)
        return rect.inflate(reach).contains(point) && !(inner.width > 0f && inner.height > 0f && inner.contains(point))
    }
}

/** Hollow ellipse inscribed in the dragged rectangle. */
data class EllipseAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    override val start: Offset,
    override val end: Offset,
) : ShapeAnnotation() {
    override fun dragTo(point: Offset) = copy(end = point)
    override fun translated(shift: Offset) = copy(start = start + shift, end = end + shift)

    /** Grabbed by its outline: how far [point] is from the ellipse, measured along the radius, within the stroke plus slack. */
    override fun hitTest(point: Offset, tolerance: Float): Boolean {
        val a = rect.width / 2f
        val b = rect.height / 2f
        if (a <= 0f || b <= 0f) return super.hitTest(point, tolerance)
        val dx = (point.x - rect.center.x) / a
        val dy = (point.y - rect.center.y) / b
        val radial = kotlin.math.abs(kotlin.math.sqrt(dx * dx + dy * dy) - 1f) * min(a, b)
        return radial <= strokeWidth / 2f + tolerance
    }
}

/** Translucent marker over a region — the content stays readable underneath. */
data class HighlightAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    override val start: Offset,
    override val end: Offset,
) : ShapeAnnotation() {
    override fun dragTo(point: Offset) = copy(end = point)
    override fun translated(shift: Offset) = copy(start = start + shift, end = end + shift)
    override val bounds: Rect get() = rect

    companion object {
        /** Marker-pen opacity: enough to tint, not enough to hide the text. */
        const val OPACITY = 0.35f
    }
}

/** How a [RedactionAnnotation] destroys the pixels underneath. */
enum class RedactionStyle {
    /** Gaussian blur — softer, still hints at the shape of what was there. */
    Blur,

    /** Nearest-neighbour downscale — coarse blocks, nothing recoverable by eye. */
    Pixelate,
}

/**
 * Obscures a region so sensitive data can be shared. Applied when the image is
 * flattened for export, so the exported PNG has no layer to peel back.
 */
data class RedactionAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    override val start: Offset,
    override val end: Offset,
    val style: RedactionStyle,
) : ShapeAnnotation() {
    /** Gaussian sigma in image pixels. */
    val sigma: Float get() = strokeWidth * STRENGTH_SCALE

    /** Block edge in image pixels; at least 2, or "pixelated" is a no-op. */
    val blockSize: Float get() = max(strokeWidth * STRENGTH_SCALE, 2f)

    override fun dragTo(point: Offset) = copy(end = point)
    override fun translated(shift: Offset) = copy(start = start + shift, end = end + shift)
    override val bounds: Rect get() = rect

    companion object {
        /** Blur sigma / pixel block size as a multiple of the stroke width. */
        const val STRENGTH_SCALE = 2.5f
    }
}

/** Freehand stroke: the polyline the pointer traced. */
data class PenAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    val points: List<Offset>,
) : Annotation() {
    override fun dragTo(point: Offset): PenAnnotation {
        val last = points.lastOrNull()
        if (last != null && (point - last).getDistance() < MIN_SEGMENT) return this
        return copy(points = points + point)
    }

    override fun translated(shift: Offset) = copy(points = points.map { it + shift })

    override fun hitTest(point: Offset, tolerance: Float): Boolean {
        val reach = strokeWidth / 2f + tolerance
        if (points.size == 1) return (points.first() - point).getDistance() <= reach
        return points.zipWithNext().any { (a, b) -> distanceToSegment(point, a, b) <= reach }
    }

    /** A dot counts: lifting the pen immediately is a legitimate mark. */
    override val isMeaningful: Boolean get() = points.isNotEmpty()

    override val bounds: Rect
        get() {
            val first = points.firstOrNull() ?: return Rect.Zero
            var rect = Rect(first, first)
            for (p in points) {
                rect = Rect(min(rect.left, p.x), min(rect.top, p.y), max(rect.right, p.x), max(rect.bottom, p.y))
            }
            return rect.inflate(strokeWidth)
        }

    companion object {
        /**
         * Minimum gap between recorded points, in image pixels. A pointer that
         * barely moves reports dozens of events per second; below this distance
         * the move folds into the previous point.
         */
        const val MIN_SEGMENT = 1.5f
    }
}

/** A caption anchored at its top-left corner. */
data class TextAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    val position: Offset,
    val text: String,
    val fontSize: Float,
) : Annotation() {
    /** Text is placed by a single tap and typed in a dialog, so a drag does not move it. */
    override fun dragTo(point: Offset): Annotation = this
    override fun translated(shift: Offset) = copy(position = position + shift)
    override val isMeaningful: Boolean get() = text.isNotBlank()

    /** Rough box: exact metrics need a laid-out paragraph, which lives in the painter. One entry per typed line. */
    override val bounds: Rect
        get() {
            val lines = text.lines()
            val widest = lines.maxOfOrNull { it.length } ?: 0
            return Rect(
                position.x,
                position.y,
                position.x + widest * fontSize * 0.6f,
                position.y + lines.size * fontSize * LINE_HEIGHT_SCALE,
            )
        }

    companion object {
        /** Font size derived from the stroke slider, so text scales with the same control as every tool. */
        const val FONT_SCALE = 6f

        /** Distance between two lines of a multi-line caption, as a multiple of the font size. */
        const val LINE_HEIGHT_SCALE = 1.4f

        fun fontSizeFor(strokeWidth: Float): Float = strokeWidth * FONT_SCALE
    }
}

/** Numbered circular badge for step-by-step walkthroughs (1, 2, 3…). */
data class StepAnnotation(
    override val id: String,
    override val color: Color,
    override val strokeWidth: Float,
    val center: Offset,
    val number: Int,
) : Annotation() {
    val radius: Float get() = strokeWidth * RADIUS_SCALE

    override fun dragTo(point: Offset): Annotation = this
    override fun translated(shift: Offset) = copy(center = center + shift)
    override val isMeaningful: Boolean get() = true
    override val bounds: Rect
        get() = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

    companion object {
        /** Badge radius as a multiple of the stroke width. */
        const val RADIUS_SCALE = 4f
    }
}
