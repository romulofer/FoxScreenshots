package com.foxdevelops.foxscreenshots.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.hasContentDescription
import com.foxdevelops.foxscreenshots.core.desktop.DependencyIssue
import com.foxdevelops.foxscreenshots.core.desktop.DependencySeverity
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @Test
    fun `an empty session shows the placeholder and disables the session actions`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithText("Nenhuma captura ainda. Use a barra acima para começar.").assertExists()
        onNodeWithContentDescription("Salvar tudo").assertIsNotEnabled()
        onNodeWithContentDescription("Limpar sessão").assertIsNotEnabled()
    }

    @Test
    fun `the toolbar offers the four capture modes`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        for (label in listOf("Instantâneo", "Temporizador", "Tela cheia", "Janela ativa")) onNodeWithText(label).assertExists()
    }

    @Test
    fun `a capture shows up as a tile with its four actions and a counter`() = runComposeUiTest {
        val app = TestApp()
        app.session.add(testCapture("a"))
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Visualizar"))
        for (action in listOf("Editar", "Copiar", "Salvar", "Excluir")) onNodeWithContentDescription(action).assertExists()
        onNodeWithText("1/30").assertExists()
        onNodeWithContentDescription("Salvar tudo").assertIsEnabled()
    }

    @Test
    fun `delete removes the tile`() = runComposeUiTest {
        val app = TestApp()
        app.session.add(testCapture("a"))
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Excluir"))
        onNodeWithContentDescription("Excluir").performClick()
        waitForIdle()
        assertTrue(app.session.state.value.isEmpty())
        onNodeWithText("Nenhuma captura ainda. Use a barra acima para começar.").assertExists()
    }

    @Test
    fun `copy puts the capture on the clipboard and says so`() = runComposeUiTest {
        val app = TestApp()
        app.session.add(testCapture("a"))
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Copiar"))
        onNodeWithContentDescription("Copiar").performClick()
        waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("Copiado para a área de transferência"))
        assertEquals(1, app.clipboard.copied.size)
    }

    @Test
    fun `clear session asks first, then empties it`() = runComposeUiTest {
        val app = TestApp()
        app.session.add(testCapture("a"))
        setContent { AppRoot(app.container) }
        onNodeWithContentDescription("Limpar sessão").performClick()
        onNodeWithText("Limpar a sessão?").assertExists()
        onNodeWithText("Cancelar").performClick()
        assertEquals(1, app.session.state.value.size)

        onNodeWithContentDescription("Limpar sessão").performClick()
        onNodeWithText("Limpar").performClick()
        waitForIdle()
        assertTrue(app.session.state.value.isEmpty())
    }

    @Test
    fun `the capture buttons run the capture flow`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithText("Tela cheia").performClick()
        waitUntil(timeoutMillis = 5_000) { app.session.state.value.isNotEmpty() }
        assertEquals(1, app.service.fullGrabs)
    }

    @Test
    fun `a capture failure is explained on the snackbar`() = runComposeUiTest {
        val app = TestApp()
        app.service.activeWindow = null
        setContent { AppRoot(app.container) }
        onNodeWithText("Janela ativa").performClick()
        waitUntilAtLeastOneExists(androidx.compose.ui.test.hasText("Nenhuma janela ativa para capturar."), timeoutMillis = 5_000)
    }

    @Test
    fun `missing system parts are explained, and the notice can be dismissed`() = runComposeUiTest {
        val app = TestApp()
        app.issues.report(DependencyIssue(SystemDependency.XDisplay, DependencySeverity.Blocking))
        setContent { AppRoot(app.container) }
        onNodeWithText("A captura de tela está indisponível neste sistema").assertExists()
        onNodeWithText("Servidor gráfico X inacessível", substring = true).assertExists()
        onNodeWithText("Dispensar").performClick()
        onNodeWithText("A captura de tela está indisponível neste sistema").assertDoesNotExist()
    }

    @Test
    fun `degraded issues get the milder title`() = runComposeUiTest {
        val app = TestApp(language = "en")
        app.issues.report(DependencyIssue(SystemDependency.Hotkeys, DependencySeverity.Degraded))
        setContent { AppRoot(app.container) }
        onNodeWithText("Some features are unavailable on this system").assertExists()
    }

    @Test
    fun `no notice at all when the machine has everything`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithText("Dispensar").assertDoesNotExist()
    }
}
