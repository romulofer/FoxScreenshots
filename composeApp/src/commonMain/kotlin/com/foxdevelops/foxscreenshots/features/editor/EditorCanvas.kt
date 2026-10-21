package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.foxdevelops.foxscreenshots.core.theme.LocalFoxColors
import kotlin.math.exp

/** Movement (in widget pixels) below which a press-and-release counts as a tap. */
private const val TAP_SLOP = 4f

/** Test hook: the pointer-driven drawing surface. */
const val EDITOR_CANVAS_TAG = "editor-canvas"

/** How strongly one notch of the mouse wheel zooms. */
private const val WHEEL_ZOOM_SENSITIVITY = 0.1f

/**
 * The editing surface: the capture, its annotations, and the pointer handling
 * that draws new ones.
 *
 * Gestures are translated to image pixels through [CanvasFit] before they reach
 * the controller, so the controller never has to know how big the window is or
 * how far it is zoomed. The wheel zooms around the pointer and a middle-button
 * drag pans (see [EditorViewport]). Committed annotations and the in-progress
 * drag sit in separate layers: a drag repaints only the cheap one.
 */
@Composable
fun EditorCanvas(
    image: ImageBitmap,
    annotations: List<Annotation>,
    draft: Annotation?,
    cropDraft: Rect?,
    selected: Annotation?,
    tool: EditorTool,
    viewport: EditorViewport,
    onDragStart: (Offset) -> Unit,
    onDragUpdate: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widget by remember { mutableStateOf(Size.Zero) }
    val fit = viewport.fit(Size(image.width.toFloat(), image.height.toFloat()), widget)
    val colors = LocalFoxColors.current
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .fillMaxSize()
            .testTag(EDITOR_CANVAS_TAG)
            .pointerHoverIcon(
                when {
                    tool == EditorTool.Select -> PointerIcon.Default
                    tool.isDragTool -> PointerIcon.Crosshair
                    else -> PointerIcon.Hand
                },
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.first()
                        // Around the pointer, measured from the widget centre: that is the origin the pan is relative to.
                        val around = change.position - Offset(size.width / 2f, size.height / 2f)
                        viewport.zoomBy(exp(-change.scrollDelta.y * WHEEL_ZOOM_SENSITIVITY), around)
                        change.consume()
                    }
                }
            }
            .pointerInput(fit, tool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentEvent.buttons.isTertiaryPressed) {
                        // Middle button: pan, never draw.
                        while (true) {
                            val change = awaitPointerEvent().changes.first()
                            viewport.panBy(change.position - change.previousPosition)
                            change.consume()
                            if (change.changedToUp()) break
                        }
                        return@awaitEachGesture
                    }
                    val start = fit.toImage(down.position)
                    onDragStart(start)
                    var moved = false
                    while (true) {
                        val change = awaitPointerEvent().changes.first()
                        if ((change.position - down.position).getDistance() > TAP_SLOP) moved = true
                        onDragUpdate(fit.toImage(change.position))
                        if (change.changedToUp()) break
                    }
                    // A press that never travelled is a tap: tools that place instead of drag act on it.
                    if (!moved) onTap(start)
                    onDragEnd()
                }
            },
    ) {
        BaseLayer(image, annotations, fit, onSize = { widget = it })
        DraftLayer(image, draft, cropDraft, selected, fit, accent, colors.scrim)
    }
}

/** The capture plus every committed annotation — everything a drag does not touch. */
@Composable
private fun BaseLayer(image: ImageBitmap, annotations: List<Annotation>, fit: CanvasFit, onSize: (Size) -> Unit) {
    Canvas(Modifier.fillMaxSize()) {
        onSize(size)
        // Everything below draws in image pixels: one transform means the preview and the exported PNG run the same painter.
        withTransform({
            translate(fit.offset.x, fit.offset.y)
            scale(fit.scale, fit.scale, pivot = Offset.Zero)
        }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(image.width, image.height),
                // Zoomed past 1:1 the user is checking pixels; smoothing them would hide exactly what they came to see.
                filterQuality = if (fit.scale >= 1f) FilterQuality.None else FilterQuality.Medium,
            )
            drawAnnotations(image, annotations)
        }
    }
}

/** The annotation being dragged, the picked one's outline, and the crop rectangle — redrawn on every pointer move. */
@Composable
private fun DraftLayer(
    image: ImageBitmap,
    draft: Annotation?,
    cropDraft: Rect?,
    selected: Annotation?,
    fit: CanvasFit,
    accent: Color,
    scrim: Color,
) {
    Canvas(Modifier.fillMaxSize()) {
        withTransform({
            translate(fit.offset.x, fit.offset.y)
            scale(fit.scale, fit.scale, pivot = Offset.Zero)
        }) {
            if (draft != null) drawAnnotations(image, listOf(draft))
            if (cropDraft != null) paintCropDraft(cropDraft, fit, accent, scrim)
            if (selected != null) paintSelection(selected, fit, accent)
        }
    }
}

/** Dims everything the crop is about to discard, so the user judges the result rather than the rectangle. */
private fun DrawScope.paintCropDraft(crop: Rect, fit: CanvasFit, accent: Color, scrim: Color) {
    val imageRect = Rect(0f, 0f, fit.imageSize.width, fit.imageSize.height)
    val discarded = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(imageRect)
        addRect(crop)
    }
    drawPath(discarded, scrim)
    // Constant on screen regardless of zoom: the canvas is scaled, so divide the stroke back out.
    drawRect(accent, crop.topLeft, crop.size, style = Stroke(width = 1.5f / fit.scale))
}

/** A dashed box around the annotation the Select tool picked, a constant width on screen whatever the zoom. */
private fun DrawScope.paintSelection(annotation: Annotation, fit: CanvasFit, accent: Color) {
    val box = annotation.bounds.inflate(4f / fit.scale)
    val dash = 6f / fit.scale
    drawRect(
        accent,
        box.topLeft,
        box.size,
        style = Stroke(width = 1.5f / fit.scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}
