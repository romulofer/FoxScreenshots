package com.foxdevelops.foxscreenshots.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foxdevelops.foxscreenshots.core.desktop.DesktopIntegration
import com.foxdevelops.foxscreenshots.core.desktop.SystemIssues
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.core.i18n.stringsFor
import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.core.storage.ClipboardService
import com.foxdevelops.foxscreenshots.core.storage.OutputService
import com.foxdevelops.foxscreenshots.features.capture.CaptureController
import com.foxdevelops.foxscreenshots.features.editor.ImageProcessor
import com.foxdevelops.foxscreenshots.features.home.SessionController
import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The screens of the single app window. */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Preview(val capture: CaptureResult) : Screen
    data class Editor(val capture: CaptureResult) : Screen
}

/** A back stack over [Screen]. Captures can start from the tray or a hotkey, so navigation lives outside composition. */
class AppNavigator {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    /** Whether the About dialog is open. Lives here because the menu bar and the hub window both ask for it. */
    var aboutVisible by mutableStateOf(false)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Back to the hub. */
    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Opens settings once, even when asked repeatedly (e.g. from the tray). */
    fun openSettings() {
        if (current != Screen.Settings) push(Screen.Settings)
    }
}

/**
 * One-line confirmations and errors for the window to show. A capture started
 * from the tray or a hotkey has no screen of its own to report on.
 */
class Messenger {
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = mutableMessages.asSharedFlow()

    fun show(text: String) {
        mutableMessages.tryEmit(text)
    }
}

/** Everything the screens need, wired once at startup. */
class AppContainer(
    val settings: SettingsController,
    val session: SessionController,
    val processor: ImageProcessor,
    val clipboard: ClipboardService,
    val output: OutputService,
    val issues: SystemIssues,
    val capture: CaptureController,
    val desktop: DesktopIntegration,
    /** The OS language code, for [LanguagePreference.System]. */
    val systemLanguage: String = "pt",
    val navigator: AppNavigator = AppNavigator(),
    val messenger: Messenger = Messenger(),
    val appVersion: String = "",
) {
    /** Strings for the language in force right now; read at call time so a late message uses the current language. */
    fun currentStrings(): Strings = stringsFor(settings.state.value.language, systemLanguage)
}
