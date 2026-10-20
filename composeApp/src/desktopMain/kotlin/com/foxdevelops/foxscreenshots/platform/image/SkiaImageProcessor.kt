package com.foxdevelops.foxscreenshots.platform.image

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.foxdevelops.foxscreenshots.features.editor.Annotation
import com.foxdevelops.foxscreenshots.features.editor.ImageProcessor
import com.foxdevelops.foxscreenshots.features.editor.ProcessedImage
import com.foxdevelops.foxscreenshots.features.editor.roundedOut
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Rect as SkiaRect

/** [ImageProcessor] on Skia, which Compose Desktop already ships: no second imaging library. */
class SkiaImageProcessor : ImageProcessor {

    override fun decode(png: ByteArray): ImageBitmap = Image.makeFromEncoded(png).use { it.toComposeImageBitmap() }

    override suspend fun crop(base: ImageBitmap, rect: Rect): ProcessedImage {
        val source = SkiaAnnotationPainter.skiaImage(base)
        // Whole-pixel bounds: a fractional source rect would resample the crop and soften text.
        val area = rect.roundedOut().intersect(Rect(0f, 0f, source.width.toFloat(), source.height.toFloat()))
        val width = area.width.toInt()
        val height = area.height.toInt()
        require(width > 0 && height > 0) { "crop outside the image" }
        return render(width, height) { canvas ->
            canvas.drawImageRect(
                source,
                SkiaRect.makeLTRB(area.left, area.top, area.right, area.bottom),
                SkiaRect.makeWH(width.toFloat(), height.toFloat()),
                SamplingMode.DEFAULT,
                null,
                true,
            )
        }
    }

    override suspend fun flatten(base: ImageBitmap, annotations: List<Annotation>): ProcessedImage {
        val source = SkiaAnnotationPainter.skiaImage(base)
        return render(base.width, base.height) { canvas ->
            canvas.drawImage(source, 0f, 0f)
            SkiaAnnotationPainter(base).paintAll(canvas, annotations)
        }
    }

    override fun release(image: ImageBitmap) {
        image.asSkiaBitmap().close()
    }

    private fun render(width: Int, height: Int, draw: (org.jetbrains.skia.Canvas) -> Unit): ProcessedImage {
        Surface.makeRasterN32Premul(width, height).use { surface ->
            draw(surface.canvas)
            surface.makeImageSnapshot().use { snapshot ->
                val data = snapshot.encodeToData(EncodedImageFormat.PNG)
                    ?: throw IllegalStateException("could not encode the edited image")
                return ProcessedImage(snapshot.toComposeImageBitmap(), data.bytes)
            }
        }
    }
}
