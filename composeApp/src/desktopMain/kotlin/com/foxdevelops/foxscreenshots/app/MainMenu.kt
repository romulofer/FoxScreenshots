package com.foxdevelops.foxscreenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.foxdevelops.foxscreenshots.app.ComposeCaptureSurface
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.features.home.openImage
import com.foxdevelops.foxscreenshots.ui.AppContainer
import kotlinx.coroutines.launch

/** The app's menu bar: File (captures, session, quit), Settings, Help. */
@Composable
fun FrameWindowScope.MainMenu(
    app: AppContainer,
    surface: ComposeCaptureSurface,
    strings: Strings,
    capture: (CaptureMode) -> Unit,
    quit: () -> Unit,
) {
    val session by app.session.state.collectAsState()
    val scope = rememberCoroutineScope()
    MenuBar {
        Menu(strings.menuFile) {
            Item(strings.captureInstant, onClick = { capture(CaptureMode.Instant) })
            Item(strings.captureTimer, onClick = { capture(CaptureMode.Timer) })
            Item(strings.captureFullScreen, onClick = { capture(CaptureMode.FullScreen) })
            Item(strings.captureActiveWindow, onClick = { capture(CaptureMode.ActiveWindow) })
            Item(strings.captureRepeat, onClick = { capture(CaptureMode.Repeat) })
            Separator()
            Item(strings.actionOpenImage, onClick = { scope.launch { openImage(app) } })
            Item(strings.actionClearSession, enabled = session.isNotEmpty(), onClick = { app.session.clear() })
            Separator()
            Item(strings.quit, onClick = quit)
        }
        Menu(strings.menuSettings) {
            Item(strings.settingsTitle, onClick = { app.navigator.openSettings() })
        }
        Menu(strings.menuHelp) {
            Item(strings.aboutTitle, onClick = { app.navigator.aboutVisible = true })
        }
    }
}
