package com.foxdevelops.foxscreenshots.features.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.features.editor.CanvasFit
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.ui.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.roundToInt

private const val MAX_ZOOM = 8f

/** Full-size, read-only look at a session capture, without opening the editor. Scroll zooms, drag pans, Esc or the close button dismisses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(app: AppContainer, capture: CaptureResult, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val focus = remember { FocusRequester() }
    var image by remember(capture) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(capture) {
        image = withContext(Dispatchers.Default) { app.processor.decode(capture.pngBytes) }
        focus.requestFocus()
    }
    DisposableEffect(capture) { onDispose { image?.let(app.processor::release) } }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                    app.navigator.back()
                    true
                } else {
                    false
                }
            },
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("${strings.previewTitle} · ${capture.width}×${capture.height}")
                    Text(strings.previewHint, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
            },
            navigationIcon = {
                IconButton(onClick = { app.navigator.back() }) {
                    Icon(Icons.Outlined.Close, contentDescription = strings.actionClose)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        )
        ZoomableImage(image, Modifier.fillMaxWidth().weight(1f))
    }
}

/** The image fitted into the viewport, with scroll-wheel zoom around the pointer and drag-to-pan. */
@Composable
private fun ZoomableImage(image: ImageBitmap?, modifier: Modifier = Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }

    Box(modifier.padding(bottom = 4.dp)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pan += drag
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.first()
                            val delta = change.scrollDelta.y
                            val newZoom = (zoom * exp(-delta * 0.1f)).coerceIn(1f, MAX_ZOOM)
                            // Keep the point under the cursor fixed while zooming.
                            val focus = change.position - Offset(viewport.width / 2f, viewport.height / 2f)
                            pan = (pan - focus) * (newZoom / zoom) + focus
                            zoom = newZoom
                            if (zoom == 1f) pan = Offset.Zero
                            change.consume()
                        }
                    }
                },
        ) {
            viewport = size
            val bitmap = image ?: return@Canvas
            val fit = CanvasFit.contain(Size(bitmap.width.toFloat(), bitmap.height.toFloat()), size)
            val scale = fit.scale * zoom
            val width = (bitmap.width * scale).roundToInt()
            val height = (bitmap.height * scale).roundToInt()
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(
                    ((size.width - width) / 2f + pan.x).roundToInt(),
                    ((size.height - height) / 2f + pan.y).roundToInt(),
                ),
                dstSize = IntSize(width, height),
                filterQuality = if (scale >= 1f) FilterQuality.None else FilterQuality.Medium,
            )
        }
    }
}
