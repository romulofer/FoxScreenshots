package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorViewportTest {
    @Test
    fun `starts showing the whole image`() {
        val viewport = EditorViewport()
        assertEquals(1f, viewport.zoom)
        assertEquals(Offset.Zero, viewport.pan)
    }

    @Test
    fun `zoom is clamped to its range`() {
        val viewport = EditorViewport()
        viewport.zoomBy(1000f)
        assertEquals(EditorViewport.MAX_ZOOM, viewport.zoom)
        viewport.zoomBy(0.0001f)
        assertEquals(EditorViewport.MIN_ZOOM, viewport.zoom)
    }

    @Test
    fun `zooming around a point keeps that point under the pointer`() {
        val viewport = EditorViewport()
        val around = Offset(120f, -40f)
        viewport.zoomBy(2f, around)
        // The point at `around` before zooming is at `around` after: pan = around - 2 * (around - 0).
        assertEquals(Offset(-120f, 40f), viewport.pan)
        assertEquals(2f, viewport.zoom)
    }

    @Test
    fun `zooming all the way out recentres`() {
        val viewport = EditorViewport()
        viewport.zoomBy(4f, Offset(50f, 50f))
        viewport.panBy(Offset(30f, 30f))
        viewport.zoomBy(0.01f)
        assertEquals(Offset.Zero, viewport.pan)
    }

    @Test
    fun `panning needs a zoomed-in view`() {
        val viewport = EditorViewport()
        viewport.panBy(Offset(10f, 10f))
        assertEquals(Offset.Zero, viewport.pan)
        viewport.zoomBy(2f)
        viewport.panBy(Offset(10f, 10f))
        assertEquals(Offset(10f, 10f), viewport.pan)
    }

    @Test
    fun `reset goes back to fit`() {
        val viewport = EditorViewport()
        viewport.zoomBy(3f, Offset(5f, 5f))
        viewport.reset()
        assertEquals(1f, viewport.zoom)
        assertEquals(Offset.Zero, viewport.pan)
    }

    @Test
    fun `the fit scales with the zoom`() {
        val viewport = EditorViewport()
        val image = Size(2000f, 1000f)
        val area = Size(1000f, 1000f)
        val whole = viewport.fit(image, area)
        viewport.zoomBy(2f)
        assertTrue(viewport.fit(image, area).scale == whole.scale * 2f)
    }
}
