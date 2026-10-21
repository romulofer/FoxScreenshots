package com.foxdevelops.foxscreenshots.platform.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import com.foxdevelops.foxscreenshots.features.editor.Annotation
import com.foxdevelops.foxscreenshots.features.editor.ArrowAnnotation
import com.foxdevelops.foxscreenshots.features.editor.EllipseAnnotation
import com.foxdevelops.foxscreenshots.features.editor.HighlightAnnotation
import com.foxdevelops.foxscreenshots.features.editor.PenAnnotation
import com.foxdevelops.foxscreenshots.features.editor.RectangleAnnotation
import com.foxdevelops.foxscreenshots.features.editor.RedactionAnnotation
import com.foxdevelops.foxscreenshots.features.editor.RedactionStyle
import com.foxdevelops.foxscreenshots.features.editor.StepAnnotation
import com.foxdevelops.foxscreenshots.features.editor.TextAnnotation
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.util.WeakHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws annotations onto a Skia canvas in **image coordinates**.
 *
 * One painter for both jobs: the live preview (canvas scaled to fit the
 * window) and the export (canvas at 1:1). Sharing the drawing code is what
 * guarantees the saved PNG matches what the user framed on screen.
 */
class SkiaAnnotationPainter(private val base: ImageBitmap?) {

    fun paintAll(canvas: Canvas, annotations: List<Annotation>) {
        for (annotation in annotations) paint(canvas, annotation)
    }

    fun paint(canvas: Canvas, annotation: Annotation) {
        when (annotation) {
            is ArrowAnnotation -> paintArrow(canvas, annotation)
            is RectangleAnnotation -> canvas.drawRect(
                annotation.rect.toSkia(),
                if (annotation.filled) fill(annotation) else stroke(annotation),
            )
            is EllipseAnnotation -> canvas.drawOval(annotation.rect.toSkia(), stroke(annotation))
            is HighlightAnnotation -> canvas.drawRect(
                annotation.rect.toSkia(),
                Paint().apply { color = annotation.color.copy(alpha = HighlightAnnotation.OPACITY).toArgb() },
            )
            is RedactionAnnotation -> paintRedaction(canvas, annotation)
            is PenAnnotation -> paintPen(canvas, annotation)
            is TextAnnotation -> paintText(canvas, annotation)
            is StepAnnotation -> paintStep(canvas, annotation)
        }
    }

    private fun stroke(annotation: Annotation) = Paint().apply {
        mode = PaintMode.STROKE
        strokeWidth = annotation.strokeWidth
        strokeCap = PaintStrokeCap.ROUND
        strokeJoin = PaintStrokeJoin.ROUND
        color = annotation.color.toArgb()
        isAntiAlias = true
    }

    private fun fill(annotation: Annotation) = Paint().apply {
        mode = PaintMode.FILL
        color = annotation.color.toArgb()
        isAntiAlias = true
    }

    private fun paintArrow(canvas: Canvas, arrow: ArrowAnnotation) {
        val dx = arrow.end.x - arrow.start.x
        val dy = arrow.end.y - arrow.start.y
        val length = kotlin.math.hypot(dx, dy)
        if (length == 0f) return

        val angle = atan2(dy, dx)
        val head = min(arrow.headLength, length)

        // Stop the shaft where the head begins, so a thick stroke does not poke out of the tip.
        val shaftEndX = arrow.end.x - cos(angle) * head * 0.6f
        val shaftEndY = arrow.end.y - sin(angle) * head * 0.6f
        canvas.drawLine(arrow.start.x, arrow.start.y, shaftEndX, shaftEndY, stroke(arrow))

        val spread = Math.PI.toFloat() / 7f
        val path = PathBuilder()
            .moveTo(arrow.end.x, arrow.end.y)
            .lineTo(arrow.end.x - head * cos(angle - spread), arrow.end.y - head * sin(angle - spread))
            .lineTo(arrow.end.x - head * cos(angle + spread), arrow.end.y - head * sin(angle + spread))
            .closePath()
            .detach()
        canvas.drawPath(path, fill(arrow))
    }

    private fun paintPen(canvas: Canvas, pen: PenAnnotation) {
        val first = pen.points.firstOrNull() ?: return
        if (pen.points.size == 1) {
            canvas.drawCircle(first.x, first.y, pen.strokeWidth / 2f, fill(pen))
            return
        }
        val builder = PathBuilder().moveTo(first.x, first.y)
        for (point in pen.points.drop(1)) builder.lineTo(point.x, point.y)
        canvas.drawPath(builder.detach(), stroke(pen))
    }

    private fun paintText(canvas: Canvas, text: TextAnnotation) {
        val font = Font(boldTypeface, text.fontSize)
        val metrics = font.metrics
        val firstBaseline = text.position.y - metrics.ascent
        val lineHeight = text.fontSize * TextAnnotation.LINE_HEIGHT_SCALE
        // Screenshots are busy; a dark halo keeps light text readable over light content and vice versa.
        val halo = Paint().apply {
            color = HALO_COLOR
            maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 1.5f)
            isAntiAlias = true
        }
        text.text.lines().forEachIndexed { index, line ->
            val baseline = firstBaseline + index * lineHeight
            canvas.drawString(line, text.position.x, baseline, font, halo)
            canvas.drawString(line, text.position.x, baseline, font, fill(text))
        }
    }

    private fun paintStep(canvas: Canvas, step: StepAnnotation) {
        canvas.drawCircle(step.center.x, step.center.y, step.radius, fill(step))
        canvas.drawCircle(
            step.center.x,
            step.center.y,
            step.radius,
            Paint().apply {
                mode = PaintMode.STROKE
                strokeWidth = max(1f, step.strokeWidth / 3f)
                color = WHITE
                isAntiAlias = true
            },
        )
        val font = Font(boldTypeface, step.radius * 1.1f)
        val label = step.number.toString()
        val width = font.measureTextWidth(label)
        val metrics = font.metrics
        val labelPaint = Paint().apply {
            color = WHITE
            isAntiAlias = true
        }
        canvas.drawString(
            label,
            step.center.x - width / 2f,
            step.center.y - (metrics.ascent + metrics.descent) / 2f,
            font,
            labelPaint,
        )
    }

    /**
     * Re-draws the region through a blur or pixelate filter. The source is
     * padded so a blur has real pixels to sample at the edges (otherwise it
     * fades into transparency and leaves a halo); the clip trims the result
     * back to exactly the region the user drew.
     */
    private fun paintRedaction(canvas: Canvas, redaction: RedactionAnnotation) {
        val bitmap = base ?: return
        val image = skiaImage(bitmap)
        val rect = redaction.rect
        if (rect.isEmpty) return

        when (redaction.style) {
            RedactionStyle.Blur -> {
                val padded = rect.inflate(redaction.sigma * 3f)
                val source = Rect.makeLTRB(
                    max(padded.left, 0f),
                    max(padded.top, 0f),
                    min(padded.right, image.width.toFloat()),
                    min(padded.bottom, image.height.toFloat()),
                )
                if (source.width <= 0f || source.height <= 0f) return
                val paint = Paint().apply {
                    imageFilter = ImageFilter.makeBlur(redaction.sigma, redaction.sigma, FilterTileMode.DECAL)
                }
                canvas.save()
                canvas.clipRect(rect.toSkia(), ClipMode.INTERSECT, true)
                canvas.drawImageRect(image, source, source, SamplingMode.DEFAULT, paint, true)
                canvas.restore()
            }
            RedactionStyle.Pixelate -> paintPixelate(canvas, image, rect.toSkia(), redaction.blockSize)
        }
    }

    /**
     * Averages the region down to one pixel per block, then scales it back up
     * with nearest-neighbour: square blocks with nothing recoverable by eye.
     */
    private fun paintPixelate(canvas: Canvas, image: Image, region: Rect, block: Float) {
        val clipped = Rect.makeLTRB(
            max(region.left, 0f),
            max(region.top, 0f),
            min(region.right, image.width.toFloat()),
            min(region.bottom, image.height.toFloat()),
        )
        if (clipped.width <= 0f || clipped.height <= 0f) return
        val cols = max(1, kotlin.math.ceil(clipped.width / block).toInt())
        val rows = max(1, kotlin.math.ceil(clipped.height / block).toInt())

        Surface.makeRasterN32Premul(cols, rows).use { small ->
            small.canvas.drawImageRect(
                image,
                clipped,
                Rect.makeWH(cols.toFloat(), rows.toFloat()),
                SamplingMode.LINEAR,
                null,
                true,
            )
            small.makeImageSnapshot().use { mosaic ->
                canvas.drawImageRect(
                    mosaic,
                    Rect.makeWH(cols.toFloat(), rows.toFloat()),
                    Rect.makeXYWH(clipped.left, clipped.top, cols * block, rows * block).let {
                        Rect.makeLTRB(it.left, it.top, min(it.right, clipped.right), min(it.bottom, clipped.bottom))
                    },
                    SamplingMode.DEFAULT,
                    null,
                    true,
                )
            }
        }
    }

    private fun androidx.compose.ui.geometry.Rect.toSkia() = Rect.makeLTRB(left, top, right, bottom)

    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val HALO_COLOR = 0x99000000.toInt()

        /** Families tried in order: the fontconfig alias on Linux, then what Windows and macOS ship. */
        private val FONT_FAMILIES = listOf("sans-serif", "Segoe UI", "Helvetica Neue", "Arial", "DejaVu Sans")

        private val boldTypeface: Typeface by lazy {
            val manager = FontMgr.default
            FONT_FAMILIES.firstNotNullOfOrNull { manager.matchFamilyStyle(it, FontStyle.BOLD) }
                ?: manager.legacyMakeTypeface("", FontStyle.BOLD)
                ?: Typeface.makeEmpty()
        }

        private val imageCache = WeakHashMap<ImageBitmap, Image>()

        /** Skia image behind a Compose bitmap, cached per bitmap so a repaint does not re-wrap the pixels. */
        fun skiaImage(bitmap: ImageBitmap): Image = synchronized(imageCache) {
            imageCache.getOrPut(bitmap) { Image.makeFromBitmap(bitmap.asSkiaBitmap()) }
        }
    }
}
