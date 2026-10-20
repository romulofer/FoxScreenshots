package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasFitTest {
    @Test
    fun `a big image shrinks to fit and is centred`() {
        val fit = CanvasFit.contain(Size(2000f, 1000f), Size(1000f, 1000f))
        assertEquals(0.5f, fit.scale)
        assertEquals(Offset(0f, 250f), fit.offset)
    }

    @Test
    fun `a small image is never magnified`() {
        val fit = CanvasFit.contain(Size(100f, 50f), Size(1000f, 1000f))
        assertEquals(1f, fit.scale)
        assertEquals(Offset(450f, 475f), fit.offset)
    }

    @Test
    fun `widget points map back to image pixels`() {
        val fit = CanvasFit.contain(Size(2000f, 1000f), Size(1000f, 1000f))
        assertEquals(Offset(1000f, 500f), fit.toImage(Offset(500f, 500f)))
    }

    @Test
    fun `a drag past the edge stops at the border`() {
        val fit = CanvasFit.contain(Size(2000f, 1000f), Size(1000f, 1000f))
        assertEquals(Offset(0f, 0f), fit.toImage(Offset(-50f, 0f)))
        assertEquals(Offset(2000f, 1000f), fit.toImage(Offset(5000f, 5000f)))
    }

    @Test
    fun `destination is the painted rectangle`() {
        val fit = CanvasFit.contain(Size(2000f, 1000f), Size(1000f, 1000f))
        assertEquals(0f, fit.destination.left)
        assertEquals(250f, fit.destination.top)
        assertEquals(1000f, fit.destination.right)
        assertEquals(750f, fit.destination.bottom)
    }

    @Test
    fun `empty sizes fall back to an identity fit`() {
        assertEquals(1f, CanvasFit.contain(Size.Zero, Size(10f, 10f)).scale)
        assertEquals(1f, CanvasFit.contain(Size(10f, 10f), Size.Zero).scale)
    }
}
