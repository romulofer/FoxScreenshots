package com.foxdevelops.foxscreenshots.model

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureRegionTest {
    @Test
    fun `fromPoints normalizes any drag direction`() {
        val region = CaptureRegion.fromPoints(Offset(50f, 80f), Offset(10f, 20f))
        assertEquals(CaptureRegion(10, 20, 40, 60), region)
    }

    @Test
    fun `clampedTo trims a selection dragged past the edge`() {
        val region = CaptureRegion(-10, 90, 50, 50).clampedTo(100, 100)
        assertEquals(CaptureRegion(0, 90, 40, 10), region)
    }

    @Test
    fun `clampedTo is empty without overlap`() {
        assertTrue(CaptureRegion(200, 200, 10, 10).clampedTo(100, 100).isEmpty)
    }

    @Test
    fun `scaled converts between overlay and screenshot pixels`() {
        assertEquals(CaptureRegion(20, 40, 60, 80), CaptureRegion(10, 20, 30, 40).scaled(2f))
    }

    @Test
    fun `toRect spans the whole region`() {
        val rect = CaptureRegion(5, 6, 7, 8).toRect()
        assertEquals(5f, rect.left)
        assertEquals(12f, rect.right)
        assertEquals(14f, rect.bottom)
    }
}
