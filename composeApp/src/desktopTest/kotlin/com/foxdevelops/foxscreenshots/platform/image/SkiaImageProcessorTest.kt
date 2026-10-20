package com.foxdevelops.foxscreenshots.platform.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import com.foxdevelops.foxscreenshots.features.editor.ArrowAnnotation
import com.foxdevelops.foxscreenshots.features.editor.HighlightAnnotation
import com.foxdevelops.foxscreenshots.features.editor.PenAnnotation
import com.foxdevelops.foxscreenshots.features.editor.RectangleAnnotation
import com.foxdevelops.foxscreenshots.features.editor.RedactionAnnotation
import com.foxdevelops.foxscreenshots.features.editor.RedactionStyle
import com.foxdevelops.foxscreenshots.features.editor.StepAnnotation
import com.foxdevelops.foxscreenshots.features.editor.TextAnnotation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SkiaImageProcessorTest {
    private val processor = SkiaImageProcessor()

    /** Opaque PNG whose pixel at (x, y) is [color] from the function. */
    private fun png(width: Int, height: Int, color: (Int, Int) -> Int): ByteArray {
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val argb = color(x, y)
            val i = (y * width + x) * 4
            bytes[i] = argb.toByte() // B
            bytes[i + 1] = (argb shr 8).toByte() // G
            bytes[i + 2] = (argb shr 16).toByte() // R
            bytes[i + 3] = 0xFF.toByte()
        }
        return Png.encodeOpaque(bytes, width, height)
    }

    private fun ImageBitmap.pixel(x: Int, y: Int): Int = asSkiaBitmap().getColor(x, y)

    private val white = Color.White.toArgb()
    private val black = Color.Black.toArgb()
    private val red = Color(0xFFE53935).toArgb()

    @Test
    fun `decode keeps the size`() {
        val image = processor.decode(png(30, 20) { _, _ -> white })
        assertEquals(30, image.width)
        assertEquals(20, image.height)
    }

    @Test
    fun `encoding raw BGRA gives a PNG with the right size`() {
        val bytes = png(7, 5) { _, _ -> white }
        assertEquals(7 to 5, Png.size(bytes))
    }

    @Test
    fun `Png size rejects what is not a PNG`() {
        assertFailsWith<IllegalArgumentException> { Png.size(ByteArray(40)) }
        assertFailsWith<IllegalArgumentException> { Png.size(ByteArray(3)) }
    }

    @Test
    fun `the fourth byte of a raw frame is ignored`() {
        val raw = ByteArray(2 * 2 * 4) { if (it % 4 == 3) 0 else 0x7F }
        val image = processor.decode(Png.encodeOpaque(raw, 2, 2))
        assertEquals(0xFF, (image.pixel(0, 0) ushr 24))
    }

    @Test
    fun `crop returns exactly the requested pixels`() = runTest {
        val base = processor.decode(png(40, 30) { x, y -> if (x >= 10 && y >= 5) red else white })
        val cropped = processor.crop(base, Rect(10f, 5f, 30f, 25f))
        assertEquals(20, cropped.width)
        assertEquals(20, cropped.height)
        assertEquals(red, cropped.image.pixel(0, 0))
        assertEquals(red, cropped.image.pixel(19, 19))
        assertEquals(cropped.width to cropped.height, Png.size(cropped.pngBytes))
    }

    @Test
    fun `crop clips to the image and rounds fractional edges outward`() = runTest {
        val base = processor.decode(png(40, 30) { _, _ -> white })
        val cropped = processor.crop(base, Rect(-5f, 10.4f, 20.2f, 99f))
        assertEquals(21, cropped.width)
        assertEquals(20, cropped.height)
    }

    @Test
    fun `crop outside the image is an error`() = runTest {
        val base = processor.decode(png(10, 10) { _, _ -> white })
        assertFailsWith<IllegalArgumentException> { processor.crop(base, Rect(50f, 50f, 60f, 60f)) }
    }

    @Test
    fun `flatten with nothing to draw reproduces the base`() = runTest {
        val base = processor.decode(png(16, 16) { x, y -> if ((x + y) % 2 == 0) black else white })
        val flat = processor.flatten(base, emptyList())
        assertEquals(16, flat.width)
        for (y in 0 until 16) for (x in 0 until 16) assertEquals(base.pixel(x, y), flat.image.pixel(x, y))
    }

    @Test
    fun `a filled rectangle paints its color and leaves the rest alone`() = runTest {
        val base = processor.decode(png(40, 40) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(RectangleAnnotation("r", Color(0xFFE53935), 2f, Offset(10f, 10f), Offset(20f, 20f), filled = true)),
        )
        assertEquals(red, flat.image.pixel(15, 15))
        assertEquals(white, flat.image.pixel(30, 30))
        assertEquals(white, flat.image.pixel(5, 5))
    }

    @Test
    fun `an outlined rectangle leaves the inside untouched`() = runTest {
        val base = processor.decode(png(60, 60) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(RectangleAnnotation("r", Color(0xFFE53935), 4f, Offset(10f, 10f), Offset(50f, 50f))),
        )
        assertEquals(red, flat.image.pixel(10, 30))
        assertEquals(white, flat.image.pixel(30, 30))
    }

    @Test
    fun `a highlight tints without hiding what is underneath`() = runTest {
        val base = processor.decode(png(20, 20) { x, _ -> if (x < 10) black else white })
        val flat = processor.flatten(
            base,
            listOf(HighlightAnnotation("h", Color(0xFFFFC107), 4f, Offset(0f, 0f), Offset(20f, 20f))),
        )
        val dark = flat.image.pixel(5, 5)
        val light = flat.image.pixel(15, 5)
        assertNotEquals(black, dark, "tinted")
        assertNotEquals(white, light, "tinted")
        assertTrue((dark and 0xFF) < (light and 0xFF), "text under the marker stays darker than the page")
    }

    @Test
    fun `an arrow draws its head at the end point`() = runTest {
        val base = processor.decode(png(80, 40) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(ArrowAnnotation("a", Color(0xFFE53935), 3f, Offset(5f, 20f), Offset(70f, 20f))),
        )
        assertEquals(red, flat.image.pixel(40, 20), "shaft")
        assertEquals(red, flat.image.pixel(66, 20), "head")
        assertEquals(white, flat.image.pixel(75, 20), "nothing past the tip")
    }

    @Test
    fun `a pen dot and a pen stroke both draw`() = runTest {
        val base = processor.decode(png(40, 40) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(
                PenAnnotation("p1", Color(0xFFE53935), 6f, listOf(Offset(10f, 10f))),
                PenAnnotation("p2", Color(0xFFE53935), 4f, listOf(Offset(5f, 30f), Offset(35f, 30f))),
            ),
        )
        assertEquals(red, flat.image.pixel(10, 10))
        assertEquals(red, flat.image.pixel(20, 30))
    }

    @Test
    fun `a step badge is a filled circle with its number`() = runTest {
        val base = processor.decode(png(60, 60) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(StepAnnotation("s", Color(0xFFE53935), 4f, Offset(30f, 30f), 7)),
        )
        // radius 16: a point near the rim is the badge color (the number sits in the middle).
        assertEquals(red, flat.image.pixel(30, 17))
        assertEquals(white, flat.image.pixel(2, 2))
    }

    @Test
    fun `text puts ink on the page`() = runTest {
        val base = processor.decode(png(200, 60) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(TextAnnotation("t", Color(0xFF111111), 4f, Offset(10f, 10f), "Hello", 24f)),
        )
        var inked = 0
        for (y in 10 until 40) for (x in 10 until 120) if (flat.image.pixel(x, y) != white) inked++
        assertTrue(inked > 50, "expected glyph pixels, got $inked")
    }

    @Test
    fun `pixelate turns detail into uniform blocks and spares the outside`() = runTest {
        val checker = png(40, 40) { x, y -> if ((x + y) % 2 == 0) black else white }
        val base = processor.decode(checker)
        val flat = processor.flatten(
            base,
            listOf(
                RedactionAnnotation(
                    "r", Color.Red, 4f, Offset(8f, 8f), Offset(32f, 32f), RedactionStyle.Pixelate,
                ),
            ),
        )
        // blockSize = 10; the block starting at (8,8) must now be one flat color.
        val first = flat.image.pixel(8, 8)
        for (y in 8 until 18) for (x in 8 until 18) assertEquals(first, flat.image.pixel(x, y), "($x,$y)")
        // outside the region the checkerboard is intact
        assertEquals(base.pixel(2, 2), flat.image.pixel(2, 2))
        assertEquals(base.pixel(3, 2), flat.image.pixel(3, 2))
        assertEquals(base.pixel(35, 35), flat.image.pixel(35, 35))
    }

    @Test
    fun `blur softens an edge inside the region and spares the outside`() = runTest {
        val base = processor.decode(png(60, 30) { x, _ -> if (x < 30) black else white })
        val flat = processor.flatten(
            base,
            listOf(RedactionAnnotation("r", Color.Red, 4f, Offset(10f, 5f), Offset(50f, 25f), RedactionStyle.Blur)),
        )
        val atEdge = flat.image.pixel(30, 15) and 0xFF
        assertTrue(atEdge in 40..215, "edge should be a mid grey after blur, was $atEdge")
        assertEquals(black, flat.image.pixel(3, 15))
        assertEquals(white, flat.image.pixel(57, 15))
        assertEquals(black, flat.image.pixel(20, 2), "above the region")
    }

    @Test
    fun `redaction of an area outside the image does nothing`() = runTest {
        val base = processor.decode(png(20, 20) { _, _ -> white })
        val flat = processor.flatten(
            base,
            listOf(RedactionAnnotation("r", Color.Red, 4f, Offset(50f, 50f), Offset(80f, 80f), RedactionStyle.Pixelate)),
        )
        assertEquals(white, flat.image.pixel(10, 10))
    }

    @Test
    fun `flatten output is a PNG of the base size`() = runTest {
        val base = processor.decode(png(25, 15) { _, _ -> white })
        val flat = processor.flatten(base, listOf(StepAnnotation("s", Color.Red, 2f, Offset(5f, 5f), 1)))
        assertEquals(25 to 15, Png.size(flat.pngBytes))
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            flat.pngBytes.copyOfRange(0, 4),
        )
    }
}
