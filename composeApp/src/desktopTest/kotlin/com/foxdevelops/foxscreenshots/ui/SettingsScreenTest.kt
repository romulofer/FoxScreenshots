package com.foxdevelops.foxscreenshots.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.foxdevelops.foxscreenshots.core.settings.LanguagePreference
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    private fun TestApp.openSettings() = container.navigator.openSettings()

    @Test
    fun `the gear opens settings and the arrow returns`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithContentDescription("Configurações").performClick()
        onNodeWithText("Aparência").assertExists()
        onNodeWithContentDescription("Fechar").performClick()
        onNodeWithText("Instantâneo").assertExists()
    }

    @Test
    fun `switching language relabels the whole window at once`() = runComposeUiTest {
        val app = TestApp()
        app.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodesWithText("Sistema")[1].performClick()
        onNodeWithText("Inglês (EUA)").performClick()
        onNodeWithText("Appearance").assertExists()
        onNodeWithText("Timer delay (s)").assertExists()
        assertEquals(LanguagePreference.English, app.settings.state.value.language)
    }

    @Test
    fun `the system language is followed when nothing was chosen, with pt-BR as the fallback`() = runComposeUiTest {
        val english = TestApp(language = "en").also { it.openSettings() }
        setContent { AppRoot(english.container) }
        onNodeWithText("Appearance").assertExists()
    }

    @Test
    fun `an unsupported system language falls back to pt-BR`() = runComposeUiTest {
        val app = TestApp(language = "de").also { it.openSettings() }
        setContent { AppRoot(app.container) }
        onNodeWithText("Aparência").assertExists()
    }

    @Test
    fun `theme choice is stored`() = runComposeUiTest {
        val app = TestApp()
        app.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodesWithText("Sistema")[0].performClick()
        onNodeWithText("Escuro").performClick()
        assertEquals(ThemePreference.Dark, app.settings.state.value.theme)
    }

    @Test
    fun `a hotkey can be rebound from the list`() = runComposeUiTest {
        val app = TestApp()
        app.openSettings()
        setContent { AppRoot(app.container) }
        onNodeWithText("PrintScreen").performClick()
        onNodeWithText("F9").performClick()
        assertEquals("F9", app.settings.state.value.hotkey)
    }

    @Test
    fun `a stored hotkey missing from the list is still shown`() = runComposeUiTest {
        val app = TestApp()
        app.settings.setHotkey("Ctrl+Alt+K")
        app.openSettings()
        setContent { AppRoot(app.container) }
        onNodeWithText("Ctrl+Alt+K").assertExists()
    }

    @Test
    fun `changing the folder asks the output service`() = runComposeUiTest {
        val app = TestApp()
        app.openSettings()
        setContent { AppRoot(app.container) }
        onNodeWithText("Nenhuma pasta escolhida").assertExists()
        onNodeWithText("Alterar").performClick()
        waitUntil(timeoutMillis = 5_000) { app.settings.state.value.outputDir == "/picked" }
        onNodeWithText("/picked").assertExists()
    }

    @Test
    fun `auto-save without a folder asks for one first`() = runComposeUiTest {
        val app = TestApp()
        app.openSettings()
        setContent { AppRoot(app.container) }
        assertFalse(app.settings.state.value.autoSave)
        // The switches, top to bottom: selection confirm, then auto-save.
        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { app.settings.state.value.autoSave }
        assertEquals("/picked", app.settings.state.value.outputDir)
    }

    @Test
    fun `the version is shown at the bottom`() = runComposeUiTest {
        val app = TestApp()
        app.openSettings()
        setContent { AppRoot(app.container) }
        onNodeWithText("v.9.9.9").assertExists()
        assertTrue(app.container.appVersion.isNotEmpty())
    }
}
