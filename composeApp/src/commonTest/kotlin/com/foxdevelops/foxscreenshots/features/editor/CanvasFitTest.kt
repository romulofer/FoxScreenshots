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

    @Test
    fun `zooming keeps the image centred until it is panned`() {
        val fit = CanvasFit.contain(Size(2000f, 1000f), Size(1000f, 1000f)).zoomed(2f, Offset.Zero, Size(1000f, 1000f))
        assertEquals(1f, fit.scale)
        assertEquals(Offset(-500f, 0f), fit.offset)
    }

    @Test
    fun `a zoomed and panned fit still maps widget points to image pixels`() {
        val viewport = Size(1000f, 1000f)
        val fit = CanvasFit.contain(Size(2000f, 1000f), viewport).zoomed(2f, Offset(100f, 0f), viewport)
        val local = Offset(400f, 300f)
        val image = fit.toImage(local)
        assertEquals((400f - fit.offset.x) / fit.scale, image.x)
        assertEquals((300f - fit.offset.y) / fit.scale, image.y)
    }
}
