package com.foxdevelops.foxscreenshots.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.captureFailureMessage
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.ui.AppContainer
import com.foxdevelops.foxscreenshots.ui.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The hub window: capture toolbar on top, session gallery below. Left-clicking
 * the tray icon opens this window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppContainer, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val session by app.session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(strings.appTitle) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            actions = {
                IconButton(onClick = { scope.launch { openImage(app) } }) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = strings.actionOpenImage)
                }
                IconButton(
                    enabled = session.isNotEmpty(),
                    onClick = { scope.launch { saveAll(app, session) } },
                ) { Icon(Icons.Outlined.SaveAlt, contentDescription = strings.actionSaveAll) }
                IconButton(enabled = session.isNotEmpty(), onClick = { confirmClear = true }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = strings.actionClearSession)
                }
                IconButton(onClick = { app.navigator.openSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = strings.settingsTitle)
                }
                IconButton(onClick = { app.navigator.aboutVisible = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = strings.aboutTitle)
                }
            },
        )
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            DependencyBanner(app.issues)
            CaptureToolbar(onCapture = { mode -> scope.launch { runCapture(app, mode) } })
            Spacer(Modifier.height(8.dp))
            TimerDelayChips(
                selected = app.settings.state.collectAsState().value.timerDelaySeconds,
                onSelect = app.settings::setTimerDelaySeconds,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.sessionGalleryTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                if (session.isNotEmpty()) {
                    Text(
                        strings.sessionCount(session.size, SessionController.MAX_SESSION_CAPTURES),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Gallery(app, session, scope)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(strings.clearSessionTitle) },
            text = { Text(strings.clearSessionMessage) },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(strings.actionCancel) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    app.session.clear()
                }) { Text(strings.actionClear) }
            },
        )
    }
}

/** Runs a capture from the toolbar and reports a failure on the messenger. */
suspend fun runCapture(app: AppContainer, mode: CaptureMode) {
    try {
        app.capture.capture(mode)
    } catch (e: CancellationException) {
        throw e
    } catch (e: CaptureException) {
        app.messenger.show(app.currentStrings().captureFailureMessage(e.failure))
    }
}

/** Writes every session capture into a folder the user picks. A single failure aborts and shows the generic error. */
private suspend fun saveAll(app: AppContainer, session: List<CaptureResult>) {
    val strings = app.currentStrings()
    val current = app.settings.state.value
    val dir = app.output.pickDirectory(current.lastSaveDir ?: current.outputDir) ?: return
    try {
        for (capture in session) app.output.savePngToDir(capture.pngBytes, dir, current.fileNamePattern)
        app.settings.setLastSaveDir(dir)
        app.messenger.show(strings.savedCountTo(session.size, dir))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        app.messenger.show(strings.saveAllFailed)
    }
}

@Composable
private fun Gallery(app: AppContainer, session: List<CaptureResult>, scope: kotlinx.coroutines.CoroutineScope) {
    val strings = LocalStrings.current
    if (session.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    strings.sessionEmpty,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(session, key = { it.id }) { capture ->
            ThumbnailTile(
                capture = capture,
                processor = app.processor,
                onPreview = { app.navigator.push(Screen.Preview(capture)) },
                onEdit = { app.navigator.push(Screen.Editor(capture)) },
                onCopy = { scope.launch { copyToClipboard(app, capture.pngBytes) } },
                onSave = { scope.launch { saveWithDialog(app, capture.pngBytes) } },
                onDelete = { app.session.remove(capture.id) },
            )
        }
    }
}

/** Copies [png] and says how it went. */
suspend fun copyToClipboard(app: AppContainer, png: ByteArray) {
    val strings = app.currentStrings()
    val ok = try {
        app.clipboard.copyPng(png)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
    app.messenger.show(if (ok) strings.copiedToClipboard else strings.copyToClipboardFailed)
}

/** Saves [png] through a dialog and says how it went; silent when the user cancels. */
suspend fun saveWithDialog(app: AppContainer, png: ByteArray) {
    val strings = app.currentStrings()
    try {
        val settings = app.settings.state.value
        val path = app.output.savePngWithDialog(png, settings.lastSaveDir ?: settings.outputDir)
        if (path != null) {
            parentDirectory(path)?.let(app.settings::setLastSaveDir)
            app.messenger.show(strings.savedTo(path))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        app.messenger.show(strings.saveFailed)
    }
}

/** Folder part of a saved file path, for either separator; `null` when the path has no folder. */
internal fun parentDirectory(path: String): String? {
    val cut = path.lastIndexOfAny(charArrayOf('/', '\\'))
    return if (cut > 0) path.substring(0, cut) else null
}
