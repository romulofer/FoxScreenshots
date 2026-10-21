package com.foxdevelops.foxscreenshots.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.stringsFor
import com.foxdevelops.foxscreenshots.core.theme.FoxTheme
import com.foxdevelops.foxscreenshots.features.editor.EditorScreen
import com.foxdevelops.foxscreenshots.features.home.HomeScreen
import com.foxdevelops.foxscreenshots.features.preview.PreviewScreen
import com.foxdevelops.foxscreenshots.features.settings.SettingsScreen

/**
 * Root of the hub window: wires theme and language from the settings, hosts the
 * screens, and shows messages from the [Messenger] — which outlives any single
 * screen, so a capture started from the tray or a hotkey has somewhere to report.
 */
@Composable
fun AppRoot(app: AppContainer, modifier: Modifier = Modifier) {
    val settings by app.settings.state.collectAsState()
    val strings = remember(settings.language, app.systemLanguage) { stringsFor(settings.language, app.systemLanguage) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(app) {
        app.messenger.messages.collect { notice ->
            snackbar.currentSnackbarData?.dismiss()
            // Collected in its own coroutine: showSnackbar suspends until the bubble goes, and the next notice must not wait.
            launch {
                val result = snackbar.showSnackbar(
                    message = notice.text,
                    actionLabel = notice.actionLabel,
                    duration = if (notice.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) notice.onAction?.invoke()
            }
        }
    }

    CompositionLocalProvider(LocalStrings provides strings) {
        FoxTheme(settings.theme) {
            Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (val screen = app.navigator.current) {
                            Screen.Home -> HomeScreen(app)
                            Screen.Settings -> SettingsScreen(app)
                            is Screen.Preview -> PreviewScreen(app, screen.capture)
                            is Screen.Editor -> EditorScreen(app, screen.capture)
                        }
                    }
                }
                if (app.navigator.aboutVisible) AboutDialog(app.appVersion) { app.navigator.aboutVisible = false }
            }
        }
    }
}
