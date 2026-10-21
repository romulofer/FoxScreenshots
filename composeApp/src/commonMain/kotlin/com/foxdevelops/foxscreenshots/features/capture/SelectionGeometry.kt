package com.foxdevelops.foxscreenshots.features.capture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs

/** The eight grips of a pending selection: dragging one moves the edge(s) it sits on. */
enum class SelectionHandle(val movesLeft: Boolean, val movesRight: Boolean, val movesTop: Boolean, val movesBottom: Boolean) {
    TopLeft(true, false, true, false),
    Top(false, false, true, false),
    TopRight(false, true, true, false),
    Right(false, true, false, false),
    BottomRight(false, true, false, true),
    Bottom(false, false, false, true),
    BottomLeft(true, false, false, true),
    Left(true, false, false, false),
}

/**
 * The geometry of an adjustable selection, in canvas pixels. Kept free of any
 * UI so every grip, move and nudge is unit-testable.
 */
object SelectionGeometry {
    /** How far from an edge or corner a press still grabs it. */
    const val HANDLE_REACH = 10f

    /** Size of the floating "Capture" button. */
    val ConfirmButtonSize = Size(116f, 34f)

    private const val CONFIRM_GAP = 8f

    /** The grip under [point], corners before edges, or `null` when [point] is not on the selection's outline. */
    fun handleAt(rect: Rect, point: Offset): SelectionHandle? {
        val nearLeft = abs(point.x - rect.left) <= HANDLE_REACH
        val nearRight = abs(point.x - rect.right) <= HANDLE_REACH
        val nearTop = abs(point.y - rect.top) <= HANDLE_REACH
        val nearBottom = abs(point.y - rect.bottom) <= HANDLE_REACH
        val withinX = point.x >= rect.left - HANDLE_REACH && point.x <= rect.right + HANDLE_REACH
        val withinY = point.y >= rect.top - HANDLE_REACH && point.y <= rect.bottom + HANDLE_REACH
        return when {
            nearTop && nearLeft -> SelectionHandle.TopLeft
            nearTop && nearRight -> SelectionHandle.TopRight
            nearBottom && nearLeft -> SelectionHandle.BottomLeft
            nearBottom && nearRight -> SelectionHandle.BottomRight
            nearTop && withinX -> SelectionHandle.Top
            nearBottom && withinX -> SelectionHandle.Bottom
            nearLeft && withinY -> SelectionHandle.Left
            nearRight && withinY -> SelectionHandle.Right
            else -> null
        }
    }

    /** [rect] with the edge(s) of [handle] dragged to [point], re-normalised so crossing the opposite edge flips instead of inverting. */
    fun resized(rect: Rect, handle: SelectionHandle, point: Offset, bounds: Rect): Rect {
        val p = Offset(point.x.coerceIn(bounds.left, bounds.right), point.y.coerceIn(bounds.top, bounds.bottom))
        var left = rect.left
        var right = rect.right
        var top = rect.top
        var bottom = rect.bottom
        if (handle.movesLeft) left = p.x
        if (handle.movesRight) right = p.x
        if (handle.movesTop) top = p.y
        if (handle.movesBottom) bottom = p.y
        return Rect(minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom))
    }

    /** [rect] shifted by [delta], held inside [bounds] without changing its size. */
    fun moved(rect: Rect, delta: Offset, bounds: Rect): Rect {
        val dx = delta.x.coerceIn(bounds.left - rect.left, bounds.right - rect.right)
        val dy = delta.y.coerceIn(bounds.top - rect.top, bounds.bottom - rect.bottom)
        return rect.translate(dx, dy)
    }

    /**
     * Where the floating "Capture" button sits: under the selection's bottom-right
     * corner, flipping above it (or inside it) when there is no room.
     */
    fun confirmButton(rect: Rect, canvas: Rect): Rect {
        val size = ConfirmButtonSize
        var left = (rect.right - size.width).coerceIn(canvas.left, maxOf(canvas.left, canvas.right - size.width))
        var top = rect.bottom + CONFIRM_GAP
        if (top + size.height > canvas.bottom) top = rect.top - CONFIRM_GAP - size.height
        if (top < canvas.top) top = (rect.bottom - size.height - CONFIRM_GAP).coerceAtLeast(canvas.top)
        left = left.coerceAtLeast(canvas.left)
        return Rect(left, top, left + size.width, top + size.height)
    }
}
