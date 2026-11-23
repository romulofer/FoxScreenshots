package com.foxdevelops.foxscreenshots.platform.image

import com.foxdevelops.foxscreenshots.core.storage.requirePng
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer

/** Byte order of a raw 32-bit frame as it sits in memory. */
enum class PixelOrder(val colorType: ColorType) {
    Bgra(ColorType.BGRA_8888),
    Rgba(ColorType.RGBA_8888),
}

/** PNG helpers shared by the capture backends. */
object Png {
    /** Bytes per pixel of the 32-bit rasters the capture backends hand back. */
    private const val BYTES_PER_PIXEL = Int.SIZE_BYTES

    /** Where the IHDR chunk's width and height sit: 8-byte signature, 4-byte length, 4-byte type. */
    private const val IHDR_WIDTH_OFFSET = 16
    private const val IHDR_HEIGHT_OFFSET = 20
    private const val IHDR_END = 24

    /**
     * Encodes raw 32-bit pixels — what X11, GDI and CoreGraphics hand back — as
     * an opaque PNG. The fourth byte is undefined on some of them, so the image
     * is declared opaque rather than trusting it.
     */
    fun encodeOpaque(
        pixels: ByteArray,
        width: Int,
        height: Int,
        rowBytes: Int = width * BYTES_PER_PIXEL,
        order: PixelOrder = PixelOrder.Bgra,
    ): ByteArray {
        require(width > 0 && height > 0) { "invalid size ${width}x$height" }
        require(rowBytes >= width * BYTES_PER_PIXEL) { "rowBytes $rowBytes shorter than a $width-pixel row" }
        require(pixels.size >= (height - 1) * rowBytes + width * BYTES_PER_PIXEL) { "pixels too short for ${width}x$height" }
        val info = ImageInfo(ColorInfo(order.colorType, ColorAlphaType.OPAQUE, ColorSpace.sRGB), width, height)
        val image = Image.makeRaster(info, pixels, rowBytes)
        try {
            val data = image.encodeToData(EncodedImageFormat.PNG)
                ?: throw IllegalStateException("could not encode the frame as PNG")
            return data.bytes
        } finally {
            image.close()
        }
    }

    /** Width and height from the IHDR chunk, without decoding. Throws [IllegalArgumentException] if [png] is not a PNG. */
    fun size(png: ByteArray): Pair<Int, Int> {
        require(png.size >= IHDR_END) { "too short for PNG" }
        requirePng(png)
        val header = ByteBuffer.wrap(png) // big-endian, as PNG integers are
        return header.getInt(IHDR_WIDTH_OFFSET) to header.getInt(IHDR_HEIGHT_OFFSET)
    }

    /** A cropped PNG with its pixel size. */
    class Cropped(val pngBytes: ByteArray, val width: Int, val height: Int)

    /**
     * Crops [png] to [region] (clipped to the image), or returns `null` when the
     * region does not overlap it.
     */
    fun crop(png: ByteArray, region: CaptureRegion): Cropped? {
        val image = Image.makeFromEncoded(png)
        try {
            val area = region.clampedTo(image.width, image.height)
            if (area.isEmpty) return null
            Surface.makeRasterN32Premul(area.width, area.height).use { surface ->
                surface.canvas.drawImageRect(
                    image,
                    Rect.makeXYWH(area.x.toFloat(), area.y.toFloat(), area.width.toFloat(), area.height.toFloat()),
                    Rect.makeWH(area.width.toFloat(), area.height.toFloat()),
                    SamplingMode.DEFAULT,
                    null,
                    true,
                )
                val data = surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG) }
                    ?: throw IllegalStateException("could not encode the cropped frame as PNG")
                return Cropped(data.bytes, area.width, area.height)
            }
        } finally {
            image.close()
        }
    }
}
