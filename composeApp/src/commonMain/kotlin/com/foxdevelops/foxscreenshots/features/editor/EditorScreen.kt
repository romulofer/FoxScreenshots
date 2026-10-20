package com.foxdevelops.foxscreenshots.features.editor

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.features.home.copyToClipboard
import com.foxdevelops.foxscreenshots.features.home.saveWithDialog
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.ui.AppContainer
import kotlinx.coroutines.launch

/**
 * Non-destructive annotation editor. The annotation layer only becomes pixels
 * when the user copies, saves, or applies — until then every mark can be undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(app: AppContainer, capture: CaptureResult, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val controller = remember(capture) { EditorController(capture, app.processor) }
    val state by controller.state.collectAsState()
    val focus = remember { FocusRequester() }
    var askingText by remember { mutableStateOf<Offset?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Focus first, on its own: it touches the layout, so it must not run after a hop to a background thread.
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(controller) { controller.load() }
    DisposableEffect(controller) { onDispose { controller.close() } }

    val image = state.document.image
    val canUndo by remember { derivedStateOf { state.canUndo && !state.isBusy } }
    val canRedo by remember { derivedStateOf { state.canRedo && !state.isBusy } }

    fun leave() {
        // Edits live only in this screen; leaving with unsaved marks needs a deliberate confirmation.
        if (state.isDirty) confirmDiscard = true else app.navigator.back()
    }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            // Ctrl/Cmd+Z undoes, Ctrl/Cmd+Y or Ctrl/Cmd+Shift+Z redoes: the usual editor bindings.
            .onPreviewKeyEvent { event ->
                val command = event.isCtrlPressed || event.isMetaPressed
                if (event.type != KeyEventType.KeyDown || !command) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Z && event.isShiftPressed -> { if (canRedo) controller.redo(); true }
                    event.key == Key.Z -> { if (canUndo) controller.undo(); true }
                    event.key == Key.Y -> { if (canRedo) controller.redo(); true }
                    else -> false
                }
            },
    ) {
        TopAppBar(
            title = { Text(if (state.isDirty) "• ${strings.editorTitle}" else strings.editorTitle) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            navigationIcon = {
                IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = strings.actionClose) }
            },
            actions = {
                IconButton(enabled = canUndo, onClick = controller::undo) {
                    Icon(Icons.Outlined.Undo, contentDescription = strings.undo)
                }
                IconButton(enabled = canRedo, onClick = controller::redo) {
                    Icon(Icons.Outlined.Redo, contentDescription = strings.redo)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = image != null,
                    onClick = { scope.launch { exportBytes(app, controller)?.let { copyToClipboard(app, it) } } },
                ) { Icon(Icons.Outlined.ContentCopy, contentDescription = strings.actionCopy) }
                IconButton(
                    enabled = image != null,
                    onClick = { scope.launch { exportBytes(app, controller)?.let { saveWithDialog(app, it) } } },
                ) { Icon(Icons.Outlined.Save, contentDescription = strings.actionSave) }
                Button(
                    enabled = image != null && state.isDirty,
                    onClick = { scope.launch { apply(app, controller, capture) } },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.editorApply)
                }
            },
        )
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.weight(1f).fillMaxSize()) {
                ToolRail(state.tool, controller::selectTool)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).fillMaxSize()) {
                    if (image == null) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        EditorCanvas(
                            image = image,
                            annotations = state.annotations,
                            draft = state.draft,
                            cropDraft = state.cropDraft,
                            tool = state.tool,
                            onDragStart = controller::startDraft,
                            onDragUpdate = controller::updateDraft,
                            onDragEnd = { scope.launch { controller.endDraft() } },
                            onTap = { point ->
                                // Tap-to-place tools; drag tools ignore taps so a click does not drop a zero-length arrow.
                                when (state.tool) {
                                    EditorTool.Step -> controller.addStep(point)
                                    EditorTool.Text -> askingText = point
                                    else -> Unit
                                }
                            },
                        )
                        if (state.isBusy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            StyleBar(
                tool = state.tool,
                color = state.color,
                strokeWidth = state.strokeWidth,
                filled = state.filled,
                onColorSelected = controller::selectColor,
                onStrokeWidthChanged = controller::setStrokeWidth,
                onFilledChanged = controller::setFilled,
            )
        }
    }

    askingText?.let { position ->
        TextPromptDialog(
            onDismiss = { askingText = null },
            onConfirm = { text ->
                askingText = null
                controller.addText(position, text)
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(strings.editorDiscardTitle) },
            text = { Text(strings.editorDiscardMessage) },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(strings.actionCancel) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    app.navigator.back()
                }) { Text(strings.actionDiscard) }
            },
        )
    }
}

/** Flattens the edits for the clipboard or a file; reports a failure and returns `null`. */
private suspend fun exportBytes(app: AppContainer, controller: EditorController): ByteArray? {
    val flattened = try {
        controller.flatten()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    if (flattened == null) app.messenger.show(app.currentStrings().editorExportFailed)
    return flattened?.pngBytes
}

/** Bakes the edits back into the session entry, so the gallery thumbnail and every later action see the annotated image. */
private suspend fun apply(app: AppContainer, controller: EditorController, capture: CaptureResult) {
    val strings = app.currentStrings()
    val flattened = try {
        controller.flatten()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    if (flattened == null) {
        app.messenger.show(strings.editorExportFailed)
        return
    }
    app.session.replace(
        CaptureResult(
            id = capture.id,
            pngBytes = flattened.pngBytes,
            width = flattened.width,
            height = flattened.height,
            takenAtMillis = capture.takenAtMillis,
        ),
    )
    // Marked before leaving: the guard must not ask to discard edits the session has just taken.
    controller.markApplied()
    app.messenger.show(strings.editorApplied)
    app.navigator.back()
}

/** Asks for the caption of a text annotation. */
@Composable
private fun TextPromptDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val strings = LocalStrings.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.editorTextTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(strings.editorTextHint) },
                singleLine = true,
                modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        onConfirm(text)
                        true
                    } else {
                        false
                    }
                },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.actionCancel) } },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(strings.actionConfirm) } },
    )
}
