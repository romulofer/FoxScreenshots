package com.foxdevelops.foxscreenshots.features.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.foxdevelops.foxscreenshots.core.capture.ScreenMapping
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.theme.LocalFoxColors
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlin.math.roundToInt

/** Drags shorter than this (in canvas pixels) are a stray click rather than a selection. */
const val MIN_SELECTION_DRAG = 4f

/**
 * Fullscreen overlay with a rubber-band selection over the frozen frame of
 * every screen, so what the user drags over is a still image and open menus or
 * tooltips stay in the shot.
 *
 * The frame is fitted inside the window: 1:1 when the window covers the virtual
 * screen exactly, shrunk when a window manager clamped it. Either way the
 * selection is reported in exact screenshot pixels.
 */
@Composable
fun CaptureSelectionOverlay(
    frame: FrozenFrame,
    onSelected: (CaptureRegion) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = LocalFoxColors.current
    val measurer = rememberTextMeasurer()
    val focus = remember { FocusRequester() }

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Offset?>(null) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var finished by remember { mutableStateOf(false) }

    val mapping = remember(canvasSize, frame) {
        ScreenMapping.fitted(Size(frame.width.toFloat(), frame.height.toFloat()), canvasSize)
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun cancel() {
        if (finished) return
        finished = true
        onCancel()
    }

    fun finish(from: Offset, to: Offset) {
        if (finished) return
        if ((to - from).getDistance() < MIN_SELECTION_DRAG) {
            start = null
            current = null
            return
        }
        val region = mapping.toRegion(from, to, frame.width, frame.height)
        if (region.isEmpty) return
        finished = true
        onSelected(region)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    cancel()
                    true
                } else {
                    false
                }
            }
            .pointerInput(mapping) {
                awaitPointerEventScope {
                    var dragFrom: Offset? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.first().position
                        pointer = position
                        when (event.type) {
                            PointerEventType.Press ->
                                if (event.buttons.isSecondaryPressed) {
                                    cancel()
                                } else {
                                    dragFrom = position
                                    start = position
                                    current = position
                                }
                            PointerEventType.Move -> if (dragFrom != null) current = position
                            PointerEventType.Release -> {
                                dragFrom?.let { finish(it, position) }
                                dragFrom = null
                            }
                        }
                    }
                }
            },
    ) {
        canvasSize = size
        paintSelectionOverlay(
            frame = frame,
            mapping = mapping,
            selection = if (start != null && current != null) rectOf(start!!, current!!) else null,
            pointer = pointer,
            accent = colors.brand,
            hint = strings.selectionHint,
            measurer = measurer,
        )
    }
}

private fun rectOf(a: Offset, b: Offset) =
    Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

private const val MAGNIFIER_SIZE = 132f
private const val MAGNIFIER_ZOOM = 6f
private val FrozenDim = Color(0x8C000000)

/**
 * Draws the whole capture overlay in one pass: the frozen backdrop, dimmed
 * surroundings, the rubber band with its live size badge, and a pixel magnifier.
 * One pass keeps the drag smooth on a 4K frame, since everything repaints on
 * every pointer move.
 */
internal fun DrawScope.paintSelectionOverlay(
    frame: FrozenFrame,
    mapping: ScreenMapping,
    selection: Rect?,
    pointer: Offset?,
    accent: Color,
    hint: String,
    measurer: TextMeasurer,
) {
    val canvasRect = Rect(Offset.Zero, size)
    val scale = mapping.imagePixelsPerCanvasPixel

    // Draw the screenshot where the mapping says it lies, 1:1 with the desktop when the window covers it.
    drawImage(
        image = frame.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(frame.width, frame.height),
        dstOffset = IntOffset((-mapping.imageOrigin.x / scale).roundToInt(), (-mapping.imageOrigin.y / scale).roundToInt()),
        dstSize = IntSize((frame.width / scale).roundToInt(), (frame.height / scale).roundToInt()),
        filterQuality = FilterQuality.None,
    )

    paintDim(canvasRect, selection)
    if (selection == null) {
        paintCrosshair(canvasRect, pointer, accent)
        paintHint(canvasRect, hint, measurer)
    } else {
        paintSelection(selection, accent)
        paintSizeBadge(canvasRect, selection, scale, accent, measurer)
    }
    paintMagnifier(frame, mapping, canvasRect, pointer, accent)
}

private fun DrawScope.paintDim(canvasRect: Rect, selection: Rect?) {
    if (selection == null) {
        drawRect(FrozenDim, canvasRect.topLeft, canvasRect.size)
        return
    }
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(canvasRect)
        addRect(selection)
    }
    drawPath(path, FrozenDim)
}

private fun DrawScope.paintCrosshair(canvasRect: Rect, at: Offset?, accent: Color) {
    at ?: return
    val color = accent.copy(alpha = 0.7f)
    drawLine(color, Offset(canvasRect.left, at.y), Offset(canvasRect.right, at.y), strokeWidth = 1f)
    drawLine(color, Offset(at.x, canvasRect.top), Offset(at.x, canvasRect.bottom), strokeWidth = 1f)
}

private fun DrawScope.paintSelection(selection: Rect, accent: Color) {
    drawRect(accent, selection.topLeft, selection.size, style = Stroke(width = 1.5f))

    // Corner ticks, so the exact edge stays visible against busy wallpaper.
    val tick = 14f
    val corners = listOf(
        Triple(selection.topLeft, Offset(tick, 0f), Offset(0f, tick)),
        Triple(selection.topRight, Offset(-tick, 0f), Offset(0f, tick)),
        Triple(selection.bottomLeft, Offset(tick, 0f), Offset(0f, -tick)),
        Triple(selection.bottomRight, Offset(-tick, 0f), Offset(0f, -tick)),
    )
    for ((corner, horizontal, vertical) in corners) {
        drawLine(accent, corner, corner + horizontal, strokeWidth = 3f)
        drawLine(accent, corner, corner + vertical, strokeWidth = 3f)
    }
}

/** Live dimensions, in screenshot pixels: what the saved file will measure. */
private fun DrawScope.paintSizeBadge(canvasRect: Rect, selection: Rect, scale: Float, accent: Color, measurer: TextMeasurer) {
    val label = "${(selection.width * scale).roundToInt()} × ${(selection.height * scale).roundToInt()} px"
    val text = measurer.measure(label, TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    val padX = 8f
    val padY = 4f
    val badgeWidth = text.size.width + padX * 2
    val badgeHeight = text.size.height + padY * 2

    // Above the selection, flipping inside when there is no room.
    var origin = Offset(selection.left, selection.top - badgeHeight - 6f)
    if (origin.y < canvasRect.top) origin = Offset(selection.left, selection.bottom + 6f)

    drawRoundRect(accent, origin, Size(badgeWidth, badgeHeight), CornerRadius(6f))
    drawText(text, topLeft = origin + Offset(padX, padY))
}

private fun DrawScope.paintHint(canvasRect: Rect, hint: String, measurer: TextMeasurer) {
    val text: TextLayoutResult =
        measurer.measure(hint, TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    val padX = 14f
    val padY = 10f
    val width = text.size.width + padX * 2
    val height = text.size.height + padY * 2
    val topLeft = Offset(canvasRect.center.x - width / 2f, canvasRect.top + 64f - height / 2f)
    drawRoundRect(Color(0xCC000000), topLeft, Size(width, height), CornerRadius(10f))
    drawText(text, topLeft = topLeft + Offset(padX, padY))
}

/** Zoomed view of the pixels under the cursor, for pixel-precise edges. */
private fun DrawScope.paintMagnifier(frame: FrozenFrame, mapping: ScreenMapping, canvasRect: Rect, at: Offset?, accent: Color) {
    at ?: return

    // Keep the loupe clear of the cursor, flipping near the screen edges.
    var origin = at + Offset(24f, 24f)
    if (origin.x + MAGNIFIER_SIZE > canvasRect.right) origin = Offset(at.x - MAGNIFIER_SIZE - 24f, origin.y)
    if (origin.y + MAGNIFIER_SIZE > canvasRect.bottom) origin = Offset(origin.x, at.y - MAGNIFIER_SIZE - 24f)
    val box = Rect(origin, Size(MAGNIFIER_SIZE, MAGNIFIER_SIZE))

    val source = (MAGNIFIER_SIZE / MAGNIFIER_ZOOM * mapping.imagePixelsPerCanvasPixel).roundToInt().coerceAtLeast(1)
    val center = mapping.toImage(at)
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(box, CornerRadius(10f))) }
    clipPath(clip) {
        drawImage(
            image = frame.image,
            srcOffset = IntOffset((center.x - source / 2f).roundToInt(), (center.y - source / 2f).roundToInt()),
            srcSize = IntSize(source, source),
            dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
            dstSize = IntSize(MAGNIFIER_SIZE.roundToInt(), MAGNIFIER_SIZE.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
    drawLine(accent, Offset(box.left, box.center.y), Offset(box.right, box.center.y), strokeWidth = 1f)
    drawLine(accent, Offset(box.center.x, box.top), Offset(box.center.x, box.bottom), strokeWidth = 1f)
    drawRoundRect(accent, box.topLeft, box.size, CornerRadius(10f), style = Stroke(width = 2f))
}
