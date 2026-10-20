package com.foxdevelops.foxscreenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.foxdevelops.foxscreenshots.app.AppRuntime
import com.foxdevelops.foxscreenshots.app.ComposeCaptureSurface
import com.foxdevelops.foxscreenshots.app.OverlayRequest
import com.foxdevelops.foxscreenshots.app.virtualScreenBounds
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.desktop.DesktopEvents
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.stringsFor
import com.foxdevelops.foxscreenshots.core.theme.FoxTheme
import com.foxdevelops.foxscreenshots.features.capture.CaptureSelectionOverlay
import com.foxdevelops.foxscreenshots.features.capture.CountdownBadge
import com.foxdevelops.foxscreenshots.features.home.runCapture
import com.foxdevelops.foxscreenshots.ui.AppRoot
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider

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
            app.desktop.close()
            exitApplication()
        }

        fun capture(mode: CaptureMode) {
            scope.launch { runCapture(app, mode) }
        }

        // Probes the display server once, then keeps the tray icon and hotkeys in step with language and bindings.
        LaunchedEffect(Unit) { app.issues.reportAll(runtime.dependencyChecker.check()) }
        LaunchedEffect(settings.language, settings.hotkey, settings.timerHotkey, settings.repeatHotkey) {
            app.desktop.attach(
                strings,
                settings,
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
                            TrayAction.Quit -> quit()
                        }
                    }

                    override fun onHotkey(action: HotkeyAction) = when (action) {
                        HotkeyAction.Instant -> capture(CaptureMode.Instant)
                        HotkeyAction.Timer -> capture(CaptureMode.Timer)
                        HotkeyAction.Repeat -> capture(CaptureMode.Repeat)
                    }
                },
            )
        }

        val hubState = rememberWindowState(size = DpSize(920.dp, 640.dp))
        Window(
            visible = surface.hubVisible,
            state = hubState,
            title = strings.appTitle,
            icon = icon,
            // Closing keeps the app in the tray; with no tray to come back from, closing has to quit.
            onCloseRequest = { if (issues.any { it.dependency == SystemDependency.Tray }) quit() else surface.hideToTray() },
        ) {
            window.minimumSize = java.awt.Dimension(640, 480)
            CompositionLocalProvider(LocalStrings provides strings) {
                MainMenu(app, surface, strings, ::capture, ::quit)
            }
            AppRoot(app)
        }

        surface.overlay?.let { request -> SelectionWindow(request, settings.theme) }
        surface.countdown?.let { seconds -> CountdownWindow(seconds, settings.theme) }
    }
}

private fun loadAppIcon() = AppRuntime::class.java.getResourceAsStream("/icon.png")!!.use { loadImageBitmap(it) }

/** The fullscreen overlay: its own undecorated, always-on-top window across every monitor. */
@Composable
private fun SelectionWindow(request: OverlayRequest, theme: com.foxdevelops.foxscreenshots.core.settings.ThemePreference) {
    val bounds = remember { virtualScreenBounds() }
    val state = rememberWindowState(
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
            CaptureSelectionOverlay(
                frame = request.frame,
                onSelected = { request.answer.complete(it) },
                onCancel = { request.answer.complete(null) },
            )
        }
    }
}

/** The timer badge: small, always on top, never focused, so the desktop underneath stays usable for opening menus. */
@Composable
private fun CountdownWindow(seconds: Int, theme: com.foxdevelops.foxscreenshots.core.settings.ThemePreference) {
    val bounds = remember { virtualScreenBounds() }
    val width = 160
    val state = rememberWindowState(
        position = WindowPosition((bounds.x + (bounds.width - width) / 2).dp, (bounds.y + 24).dp),
        size = DpSize(width.dp, 100.dp),
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
        FoxTheme(theme) { CountdownBadge(seconds) }
    }
}
