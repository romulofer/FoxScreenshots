package com.foxdevelops.foxscreenshots.features.home

import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.ui.AppContainer
import com.foxdevelops.foxscreenshots.ui.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lets the user pick an image file, files it in the session as a PNG and opens
 * it in the editor. Any readable image format works; it is re-encoded to PNG so
 * the rest of the app only ever sees PNG. Silent when the user cancels.
 */
suspend fun openImage(app: AppContainer) {
    val strings = app.currentStrings()
    try {
        val settings = app.settings.state.value
        val bytes = app.output.pickImage(settings.lastSaveDir ?: settings.outputDir) ?: return
        val png = withContext(Dispatchers.Default) {
            val decoded = app.processor.decode(bytes)
            try {
                app.processor.flatten(decoded, emptyList())
            } finally {
                app.processor.release(decoded)
            }
        }
        val now = System.currentTimeMillis()
        val capture = CaptureResult("open-$now-${app.session.state.value.size}", png.pngBytes, png.width, png.height, now)
        app.session.add(capture)
        app.navigator.push(Screen.Editor(capture))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        app.messenger.show(strings.openImageFailed)
    }
}
