package com.foxdevelops.foxscreenshots.features.capture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionGeometryTest {
    private val rect = Rect(100f, 100f, 300f, 200f)
    private val canvas = Rect(0f, 0f, 1000f, 800f)

    @Test
    fun `corners and edges are grabbed within reach, corners first`() {
        assertEquals(SelectionHandle.TopLeft, SelectionGeometry.handleAt(rect, Offset(104f, 96f)))
        assertEquals(SelectionHandle.BottomRight, SelectionGeometry.handleAt(rect, Offset(305f, 207f)))
        assertEquals(SelectionHandle.Top, SelectionGeometry.handleAt(rect, Offset(200f, 95f)))
        assertEquals(SelectionHandle.Bottom, SelectionGeometry.handleAt(rect, Offset(200f, 205f)))
        assertEquals(SelectionHandle.Left, SelectionGeometry.handleAt(rect, Offset(92f, 150f)))
        assertEquals(SelectionHandle.Right, SelectionGeometry.handleAt(rect, Offset(308f, 150f)))
    }

    @Test
    fun `the inside and far outside grab nothing`() {
        assertNull(SelectionGeometry.handleAt(rect, Offset(200f, 150f)))
        assertNull(SelectionGeometry.handleAt(rect, Offset(500f, 500f)))
        assertNull(SelectionGeometry.handleAt(rect, Offset(200f, 60f)))
    }

    @Test
    fun `dragging an edge moves only that edge`() {
        val resized = SelectionGeometry.resized(rect, SelectionHandle.Right, Offset(400f, 999f), canvas)
        assertEquals(Rect(100f, 100f, 400f, 200f), resized)
        val top = SelectionGeometry.resized(rect, SelectionHandle.Top, Offset(0f, 50f), canvas)
        assertEquals(Rect(100f, 50f, 300f, 200f), top)
    }

    @Test
    fun `dragging a corner moves both its edges`() {
        assertEquals(
            Rect(50f, 60f, 300f, 200f),
            SelectionGeometry.resized(rect, SelectionHandle.TopLeft, Offset(50f, 60f), canvas),
        )
    }

    @Test
    fun `dragging past the opposite edge flips instead of inverting`() {
        val flipped = SelectionGeometry.resized(rect, SelectionHandle.Left, Offset(400f, 150f), canvas)
        assertEquals(Rect(300f, 100f, 400f, 200f), flipped)
        assertTrue(flipped.width > 0f)
    }

    @Test
    fun `a resize stays inside the canvas`() {
        val resized = SelectionGeometry.resized(rect, SelectionHandle.BottomRight, Offset(5000f, 5000f), canvas)
        assertEquals(Rect(100f, 100f, 1000f, 800f), resized)
    }

    @Test
    fun `moving keeps the size and stops at the canvas edge`() {
        assertEquals(Rect(110f, 90f, 310f, 190f), SelectionGeometry.moved(rect, Offset(10f, -10f), canvas))
        assertEquals(Rect(0f, 0f, 200f, 100f), SelectionGeometry.moved(rect, Offset(-500f, -500f), canvas))
        assertEquals(Rect(800f, 700f, 1000f, 800f), SelectionGeometry.moved(rect, Offset(9000f, 9000f), canvas))
    }

    @Test
    fun `the capture button sits under the corner and flips when there is no room`() {
        val below = SelectionGeometry.confirmButton(rect, canvas)
        assertEquals(rect.right, below.right)
        assertTrue(below.top > rect.bottom)

        val nearBottom = Rect(100f, 700f, 300f, 790f)
        val above = SelectionGeometry.confirmButton(nearBottom, canvas)
        assertTrue(above.bottom < nearBottom.top)
    }

    @Test
    fun `the capture button never leaves the canvas`() {
        val edge = Rect(0f, 100f, 40f, 200f)
        val button = SelectionGeometry.confirmButton(edge, canvas)
        assertTrue(button.left >= canvas.left && button.right <= canvas.right)
    }
}
