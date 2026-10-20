package com.foxdevelops.foxscreenshots.platform.image

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** Byte order of a raw 32-bit frame as it sits in memory. */
enum class PixelOrder(val colorType: ColorType) {
    Bgra(ColorType.BGRA_8888),
    Rgba(ColorType.RGBA_8888),
}

/** PNG helpers shared by the capture backends. */
object Png {
    /**
     * Encodes raw 32-bit pixels — what X11, GDI and CoreGraphics hand back — as
     * an opaque PNG. The fourth byte is undefined on some of them, so the image
     * is declared opaque rather than trusting it.
     */
    fun encodeOpaque(
        pixels: ByteArray,
        width: Int,
        height: Int,
        rowBytes: Int = width * 4,
        order: PixelOrder = PixelOrder.Bgra,
    ): ByteArray {
        require(width > 0 && height > 0) { "invalid size ${width}x$height" }
        require(rowBytes >= width * 4) { "rowBytes $rowBytes shorter than a $width-pixel row" }
        require(pixels.size >= (height - 1) * rowBytes + width * 4) { "pixels too short for ${width}x$height" }
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
        require(png.size >= 24) { "too short for PNG" }
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in signature.indices) require(png[i] == signature[i]) { "not a PNG" }
        fun int(at: Int) = ((png[at].toInt() and 0xFF) shl 24) or ((png[at + 1].toInt() and 0xFF) shl 16) or
            ((png[at + 2].toInt() and 0xFF) shl 8) or (png[at + 3].toInt() and 0xFF)
        return int(16) to int(20)
    }

    /** A cropped PNG with its pixel size. */
    class Cropped(val pngBytes: ByteArray, val width: Int, val height: Int)

    /**
     * Crops [png] to [region] (clipped to the image), or returns `null` when the
     * region does not overlap it.
     */
    fun crop(png: ByteArray, region: com.foxdevelops.foxscreenshots.model.CaptureRegion): Cropped? {
        val image = Image.makeFromEncoded(png)
        try {
            val area = region.clampedTo(image.width, image.height)
            if (area.isEmpty) return null
            org.jetbrains.skia.Surface.makeRasterN32Premul(area.width, area.height).use { surface ->
                surface.canvas.drawImageRect(
                    image,
                    org.jetbrains.skia.Rect.makeXYWH(area.x.toFloat(), area.y.toFloat(), area.width.toFloat(), area.height.toFloat()),
                    org.jetbrains.skia.Rect.makeWH(area.width.toFloat(), area.height.toFloat()),
                    org.jetbrains.skia.SamplingMode.DEFAULT,
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
