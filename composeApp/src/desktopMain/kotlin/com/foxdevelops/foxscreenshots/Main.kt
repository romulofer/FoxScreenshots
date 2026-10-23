package com.foxdevelops.foxscreenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.foxdevelops.foxscreenshots.app.AppRuntime
import com.foxdevelops.foxscreenshots.app.ComposeCaptureSurface
import com.foxdevelops.foxscreenshots.app.OverlayRequest
import com.foxdevelops.foxscreenshots.app.Shutdown
import com.foxdevelops.foxscreenshots.app.virtualScreenBounds
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.desktop.DesktopEvents
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.i18n.stringsFor
import com.foxdevelops.foxscreenshots.core.theme.FoxTheme
import com.foxdevelops.foxscreenshots.features.capture.CaptureSelectionOverlay
import com.foxdevelops.foxscreenshots.features.capture.CountdownBadge
import com.foxdevelops.foxscreenshots.features.home.runCapture
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.ui.AppRoot
import com.foxdevelops.foxscreenshots.ui.FoxToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.launch

private const val APP_ID = "com.foxdevelops.foxscreenshots"

fun main() {
    FileKit.init(appId = APP_ID)
    application {
        val runtime = remember { AppRuntime.create() }
        val app = runtime.container
        val surface = runtime.surface
        val scope = rememberCoroutineScope()
        val settings by app.settings.state.collectAsState()
        val strings = remember(settings.language) { stringsFor(settings.language, app.systemLanguage) }
        val issues by app.issues.state.collectAsState()
        val icon = remember { BitmapPainter(loadAppIcon()) }

        fun quit() {
            exitApplication()
            Shutdown.finish(app.desktop)
        }

        fun capture(mode: CaptureMode) {
            scope.launch { runCapture(app, mode) }
        }

        // Probes the display server once, then keeps the tray icon and hotkeys in step with language and bindings.
        LaunchedEffect(Unit) { app.issues.reportAll(runtime.dependencyChecker.check()) }
        val events = remember {
            object : DesktopEvents {
                override fun onOpenWindow() = surface.showHub()

                override fun onTrayAction(action: TrayAction) {
                    when (action) {
                        TrayAction.Show -> surface.showHub()
                        TrayAction.Instant -> capture(CaptureMode.Instant)
                        TrayAction.Timer -> capture(CaptureMode.Timer)
                        TrayAction.Repeat -> capture(CaptureMode.Repeat)
                        TrayAction.Settings -> {
                            surface.showHub()
                            app.navigator.openSettings()
                        }
                        // The tray calls from its own thread; closing the application belongs to the UI thread.
                        TrayAction.Quit -> scope.launch { quit() }
                    }
                }

                override fun onHotkey(action: HotkeyAction) = when (action) {
                    HotkeyAction.Instant -> capture(CaptureMode.Instant)
                    HotkeyAction.Timer -> capture(CaptureMode.Timer)
                    HotkeyAction.Repeat -> capture(CaptureMode.Repeat)
                    HotkeyAction.Cancel -> surface.cancelCountdown()
                }
            }
        }
        LaunchedEffect(settings.language, settings.hotkey, settings.timerHotkey, settings.repeatHotkey) {
            // Off the UI thread: connecting to D-Bus and unpacking the keyboard hook can take seconds, and would hold the window back.
            withContext(Dispatchers.IO) { app.desktop.attach(strings, settings, events) }
        }

        // With the hub hidden in the tray a snackbar has nowhere to appear: surface the same messages as a toast.
        LaunchedEffect(app) {
            app.messenger.messages.collect { notice -> if (!surface.hubVisible) surface.showToast(notice.text) }
        }
        LaunchedEffect(surface.toast) {
            if (surface.toast != null) {
                delay(TOAST_MILLIS)
                surface.clearToast()
            }
        }

        val hubState = rememberWindowState(size = DpSize(960.dp, 700.dp))
        Window(
            visible = surface.hubVisible,
            state = hubState,
            title = strings.appTitle,
            icon = icon,
            // Closing keeps the app in the tray; with no tray to come back from, closing has to quit.
            onCloseRequest = { if (issues.any { it.dependency == SystemDependency.Tray }) quit() else surface.hideToTray() },
        ) {
            window.minimumSize = java.awt.Dimension(640, 480)
            AppRoot(app)
        }

        surface.overlay?.let { request ->
            SelectionWindow(request, settings.theme, settings.language, app, settings.selectionConfirm, runtime.overlayFullscreen)
        }
        surface.countdown?.let { seconds ->
            CountdownWindow(seconds, settings.theme, strings, surface::cancelCountdown, runtime.escapeCancelsTimer)
        }
        surface.toast?.let { toast -> ToastWindow(toast.text, settings.theme, strings) }
    }
}

private fun loadAppIcon() =
    org.jetbrains.skia.Image.makeFromEncoded(AppRuntime::class.java.getResourceAsStream("/icon.png")!!.use { it.readBytes() })
        .toComposeImageBitmap()

/** How long a toast stays up. */
private const val TOAST_MILLIS = 2_500L

/** The fullscreen overlay: its own undecorated, always-on-top window across every monitor. */
@Composable
private fun SelectionWindow(
    request: OverlayRequest,
    theme: com.foxdevelops.foxscreenshots.core.settings.ThemePreference,
    language: com.foxdevelops.foxscreenshots.core.settings.LanguagePreference,
    app: com.foxdevelops.foxscreenshots.ui.AppContainer,
    adjustable: Boolean,
    fullscreen: Boolean,
) {
    val strings = remember(language) { stringsFor(language, app.systemLanguage) }
    val bounds = remember { virtualScreenBounds() }
    val state = rememberWindowState(
        // Wayland ignores where a window asks to be put: fullscreen is the one placement every compositor honours.
        placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating,
        position = WindowPosition(bounds.x.dp, bounds.y.dp),
        size = DpSize(bounds.width.dp, bounds.height.dp),
    )
    Window(
        onCloseRequest = { request.answer.complete(null) },
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        title = "",
    ) {
        FoxTheme(theme) {
            CompositionLocalProvider(LocalStrings provides strings) {
                CaptureSelectionOverlay(
                    frame = request.frame,
                    adjustable = adjustable,
                    onSelected = { request.answer.complete(it) },
                    onCancel = { request.answer.complete(null) },
                )
            }
        }
    }
}

/** The timer badge: small, always on top, never focused, so the desktop underneath stays usable for opening menus. */
@Composable
private fun CountdownWindow(
    seconds: Int,
    theme: com.foxdevelops.foxscreenshots.core.settings.ThemePreference,
    strings: com.foxdevelops.foxscreenshots.core.i18n.Strings,
    onCancel: () -> Unit,
    escapeCancels: Boolean,
) {
    val bounds = remember { virtualScreenBounds() }
    val width = 220
    val state = rememberWindowState(
        position = WindowPosition((bounds.x + (bounds.width - width) / 2).dp, (bounds.y + 24).dp),
        size = DpSize(width.dp, 120.dp),
    )
    Window(
        onCloseRequest = {},
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        title = "",
    ) {
        FoxTheme(theme) { CompositionLocalProvider(LocalStrings provides strings) { CountdownBadge(seconds, onCancel, escapeCancels = escapeCancels) } }
    }
}

/** A small always-on-top message near the bottom-right corner, never focused so it cannot steal typing from the app underneath. */
@Composable
private fun ToastWindow(text: String, theme: com.foxdevelops.foxscreenshots.core.settings.ThemePreference, strings: com.foxdevelops.foxscreenshots.core.i18n.Strings) {
    val bounds = remember { virtualScreenBounds() }
    val width = 360
    val height = 72
    val state = rememberWindowState(
        position = WindowPosition((bounds.x + bounds.width - width - 24).dp, (bounds.y + bounds.height - height - 72).dp),
        size = DpSize(width.dp, height.dp),
    )
    Window(
        onCloseRequest = {},
        state = state,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
        title = "",
    ) {
        FoxTheme(theme) { CompositionLocalProvider(LocalStrings provides strings) { FoxToast(text) } }
    }
}
