package com.foxdevelops.foxscreenshots.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.desktop.DependencyIssue
import com.foxdevelops.foxscreenshots.core.desktop.DependencySeverity
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.features.editor.EDITOR_CANVAS_TAG
import com.foxdevelops.foxscreenshots.features.home.runCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The UX review's fixes, end to end through the real screens. */
@OptIn(ExperimentalTestApi::class)
class UxFeaturesTest {
    private fun ComposeUiTest.ctrl(key: Key) = onRoot().performKeyInput {
        keyDown(Key.CtrlLeft)
        pressKey(key)
        keyUp(Key.CtrlLeft)
    }

    // ---- hub ----

    @Test
    fun `icon buttons explain themselves on hover, with their shortcut`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithContentDescription("Configurações").performMouseInput { moveTo(center) }
        waitUntilAtLeastOneExists(hasText("Configurações (Ctrl+,)"), timeoutMillis = 5_000)
    }

    @Test
    fun `deleting offers an undo that brings the capture back where it was`() = runComposeUiTest {
        val app = TestApp()
        app.session.add(testCapture("a"))
        app.session.add(testCapture("b"))
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Excluir"))
        // Newest first: "b" is the first tile.
        onAllNodesWithContentDescriptionFirst("Excluir").performClick()
        waitUntilAtLeastOneExists(hasText("Captura excluída"), timeoutMillis = 5_000)
        assertEquals(listOf("a"), app.session.state.value.map { it.id })

        onNodeWithText("Desfazer").performClick()
        waitUntil(timeoutMillis = 5_000) { app.session.state.value.size == 2 }
        assertEquals(listOf("b", "a"), app.session.state.value.map { it.id })
    }

    @Test
    fun `reveal needs a saved file, then opens its folder`() = runComposeUiTest {
        val app = TestApp()
        val capture = testCapture("a")
        app.session.add(capture)
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Mostrar na pasta"))
        onNodeWithContentDescription("Mostrar na pasta").assertIsNotEnabled()

        app.session.markSaved("a", "/shots/a.png")
        waitForIdle()
        onNodeWithContentDescription("Mostrar na pasta").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { app.output.revealed.isNotEmpty() }
        assertEquals(listOf("/shots/a.png"), app.output.revealed)
    }

    @Test
    fun `a failed reveal says so`() = runComposeUiTest {
        val app = TestApp()
        app.output.revealable = false
        app.session.add(testCapture("a"))
        app.session.markSaved("a", "/shots/a.png")
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Mostrar na pasta"))
        onNodeWithContentDescription("Mostrar na pasta").performClick()
        waitUntilAtLeastOneExists(hasText("Não foi possível abrir a pasta"), timeoutMillis = 5_000)
    }

    @Test
    fun `each tile says how big the capture is`() = runComposeUiTest {
        val app = TestApp()
        app.session.add(testCapture("a", 200, 120))
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasText("200×120", substring = true))
    }

    @Test
    fun `the counter turns into a warning when the session is full`() = runComposeUiTest {
        val app = TestApp()
        repeat(30) { app.session.add(testCapture("s$it", 40, 30)) }
        setContent { AppRoot(app.container) }
        onNodeWithText("30/30").assertExists()
        onNodeWithText("30/30").performMouseInput { moveTo(center) }
        waitUntilAtLeastOneExists(hasText("Sessão cheia: a próxima captura descarta a mais antiga"), timeoutMillis = 5_000)
    }

    /** What the messenger said while [block] ran: the hub need not be on screen to be told. */
    private fun TestApp.messagesDuring(block: suspend () -> Unit): List<String> {
        val seen = java.util.concurrent.CopyOnWriteArrayList<String>()
        runBlocking {
            val collector = launch(Dispatchers.Unconfined) { container.messenger.messages.collect { seen += it.text } }
            block()
            collector.cancel()
        }
        return seen
    }

    @Test
    fun `a capture confirms itself even with no window to show it in`() {
        val app = TestApp()
        val seen = app.messagesDuring { runCapture(app.container, CaptureMode.FullScreen) }
        assertEquals(listOf("Captura copiada e adicionada à sessão"), seen)
        assertEquals(1, app.session.state.value.size)
    }

    @Test
    fun `a full session says the oldest was dropped`() {
        val app = TestApp()
        repeat(30) { app.session.add(testCapture("s$it", 40, 30)) }
        val seen = app.messagesDuring { runCapture(app.container, CaptureMode.FullScreen) }
        assertEquals(listOf("Captura copiada — a mais antiga da sessão foi descartada"), seen)
    }

    @Test
    fun `a failed capture says why instead of confirming`() {
        val app = TestApp()
        app.service.activeWindow = null
        val seen = app.messagesDuring { runCapture(app.container, CaptureMode.ActiveWindow) }
        assertEquals(listOf("Nenhuma janela ativa para capturar."), seen)
        assertTrue(app.session.state.value.isEmpty())
    }

    @Test
    fun `a cancelled capture says nothing`() {
        val app = TestApp()
        app.surface.selection = null
        val seen = app.messagesDuring { runCapture(app.container, CaptureMode.Instant) }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `dismissing a notice is remembered, so it does not nag again`() = runComposeUiTest {
        val app = TestApp()
        app.issues.report(DependencyIssue(SystemDependency.Tray, DependencySeverity.Degraded))
        setContent { AppRoot(app.container) }
        onNodeWithText("Dispensar").performClick()
        waitForIdle()
        assertEquals(setOf("Tray"), app.settings.state.value.dismissedIssues)
        onNodeWithText("Dispensar").assertDoesNotExist()
    }

    @Test
    fun `an already dismissed notice stays hidden but a new kind shows`() = runComposeUiTest {
        val app = TestApp()
        app.settings.dismissIssues(setOf("Tray"))
        app.issues.report(DependencyIssue(SystemDependency.Tray, DependencySeverity.Degraded))
        setContent { AppRoot(app.container) }
        onNodeWithText("Dispensar").assertDoesNotExist()

        app.issues.report(DependencyIssue(SystemDependency.Hotkeys, DependencySeverity.Degraded))
        waitUntilAtLeastOneExists(hasText("Dispensar"), timeoutMillis = 5_000)
    }

    @Test
    fun `control O opens an image into the editor`() = runComposeUiTest {
        val app = TestApp()
        app.output.pickedImage = solidPng(64, 32)
        setContent { AppRoot(app.container) }
        waitForIdle()
        ctrl(Key.O)
        waitUntil(timeoutMillis = 5_000) { app.container.navigator.current is Screen.Editor }
        assertEquals(1, app.session.state.value.size)
    }

    @Test
    fun `control comma opens the settings`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        waitForIdle()
        ctrl(Key.Comma)
        waitUntil(timeoutMillis = 5_000) { app.container.navigator.current == Screen.Settings }
    }

    // ---- preview ----

    @Test
    fun `the preview can edit, copy and save without going back first`() = runComposeUiTest {
        val app = TestApp()
        val capture = testCapture("p", 320, 200)
        app.session.add(capture)
        app.container.navigator.push(Screen.Preview(capture))
        setContent { AppRoot(app.container) }

        onNodeWithContentDescription("Copiar").performClick()
        waitUntil(timeoutMillis = 5_000) { app.clipboard.copied.isNotEmpty() }

        onNodeWithContentDescription("Salvar").performClick()
        waitUntil(timeoutMillis = 5_000) { app.session.savedPaths.value.containsKey("p") }

        onNodeWithContentDescription("Editar").performClick()
        waitForIdle()
        val screen = app.container.navigator.current
        assertIs<Screen.Editor>(screen)
        // Back from the editor lands on the hub, not on the preview it was opened from.
        app.container.navigator.back()
        assertEquals(Screen.Home, app.container.navigator.current)
    }

    // ---- settings ----

    @Test
    fun `selection confirm is a switch, on by default`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        assertTrue(app.settings.state.value.selectionConfirm)
        onAllNodes(isToggleable())[0].performClick()
        waitForIdle()
        assertEquals(false, app.settings.state.value.selectionConfirm)
    }

    @Test
    fun `the file name field shows what a file would be called`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        onNodeWithText("Exemplo: foxshot_20260813_084205.png").assertExists()
    }

    @Test
    fun `a hotkey can be recorded by pressing it`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodes(hasText("Gravar…"))[0].performClick()
        onNodeWithText("Pressione a combinação…").assertExists()
        onNodeWithText("Pressione a combinação…").performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.K); keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft)
        }
        waitUntil(timeoutMillis = 5_000) { app.settings.state.value.hotkey == "Ctrl+Shift+K" }
        onNodeWithText("Pressione a combinação…").assertDoesNotExist()
        // Back to the normal row.
        onAllNodes(hasText("Gravar…")).fetchSemanticsNodes().isNotEmpty().let { assertTrue(it) }
    }

    @Test
    fun `recording a hotkey another action uses is refused with the reason`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodes(hasText("Gravar…"))[0].performClick()
        // F7 belongs to the timer by default.
        onNodeWithText("Pressione a combinação…").performKeyInput { pressKey(Key.F7) }
        waitUntilAtLeastOneExists(hasText("Esse atalho já é usado por “Atalho do temporizador”"), timeoutMillis = 5_000)
        assertEquals("PrintScreen", app.settings.state.value.hotkey)
    }

    @Test
    fun `a bare letter is refused when recording`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodes(hasText("Gravar…"))[0].performClick()
        onNodeWithText("Pressione a combinação…").performKeyInput { pressKey(Key.S) }
        waitUntilAtLeastOneExists(hasText("Letras precisam de Ctrl, Alt ou Meta junto; Esc é reservado"), timeoutMillis = 5_000)
        assertEquals("PrintScreen", app.settings.state.value.hotkey)
    }

    @Test
    fun `cancel stops recording`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodes(hasText("Gravar…"))[0].performClick()
        onNodeWithText("Pressione a combinação…").assertExists()
        onNodeWithText("Cancelar").performClick()
        waitUntilDoesNotExist(hasText("Pressione a combinação…"), timeoutMillis = 5_000)
        assertEquals("PrintScreen", app.settings.state.value.hotkey)
    }

    @Test
    fun `escape closes the recorder without changing anything`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        onAllNodes(hasText("Gravar…"))[0].performClick()
        onNodeWithText("Pressione a combinação…").performKeyInput { pressKey(Key.Escape) }
        waitUntilDoesNotExist(hasText("Pressione a combinação…"), timeoutMillis = 5_000)
        assertEquals("PrintScreen", app.settings.state.value.hotkey)
        assertEquals(Screen.Settings, app.container.navigator.current)
    }

    @Test
    fun `escape leaves the settings`() = runComposeUiTest {
        val app = TestApp()
        app.container.navigator.openSettings()
        setContent { AppRoot(app.container) }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitUntil(timeoutMillis = 5_000) { app.container.navigator.current == Screen.Home }
    }

    // ---- editor ----

    private val original = testCapture("a", 400, 300)

    private fun ComposeUiTest.openEditor(app: TestApp = TestApp()): TestApp {
        app.session.add(original)
        app.container.navigator.push(Screen.Editor(original))
        setContent { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription("Seta"), timeoutMillis = 5_000)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasTestTag(EDITOR_CANVAS_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
        return app
    }

    private fun ComposeUiTest.dragCanvas(dx: Float, dy: Float) = onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
        moveTo(center - Offset(dx / 2, dy / 2))
        press()
        moveBy(Offset(dx / 2, dy / 2))
        moveBy(Offset(dx / 2, dy / 2))
        release()
    }

    @Test
    fun `a letter picks a tool`() = runComposeUiTest {
        openEditor()
        onNodeWithText("Preencher").assertDoesNotExist()
        onRoot().performKeyInput { pressKey(Key.R) }
        waitUntilAtLeastOneExists(hasText("Preencher"), timeoutMillis = 5_000)
    }

    @Test
    fun `tools say their shortcut on hover`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("Seta").performMouseInput { moveTo(center) }
        waitUntilAtLeastOneExists(hasText("Seta (A)"), timeoutMillis = 5_000)
    }

    @Test
    fun `an annotation can be picked, deleted and brought back`() = runComposeUiTest {
        openEditor()
        dragCanvas(120f, 0f)
        onNodeWithContentDescription("Excluir anotação").assertIsNotEnabled()

        onNodeWithContentDescription("Selecionar e mover").performClick()
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput { click(center) }
        waitForIdle()
        onNodeWithContentDescription("Excluir anotação").assertIsEnabled()

        onRoot().performKeyInput { pressKey(Key.Delete) }
        waitForIdle()
        onNodeWithContentDescription("Excluir anotação").assertIsNotEnabled()
        onNodeWithContentDescription("Desfazer").performClick()
        waitForIdle()
        // The arrow is back, and it is the thing that Redo would remove again.
        onNodeWithContentDescription("Refazer").assertIsEnabled()
    }

    @Test
    fun `a picked annotation can be dragged to another place`() = runComposeUiTest {
        val app = openEditor()
        dragCanvas(120f, 0f)
        val before = app.container.navigator.current
        onNodeWithContentDescription("Selecionar e mover").performClick()
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, 40f))
            moveBy(Offset(0f, 40f))
            release()
        }
        waitForIdle()
        // Moving is an edit: it is undoable and the title still shows unsaved changes.
        onNodeWithText("• Editor").assertExists()
        onNodeWithContentDescription("Desfazer").assertIsEnabled()
        assertEquals(before, app.container.navigator.current)
    }

    @Test
    fun `the editor says when there are changes not applied`() = runComposeUiTest {
        openEditor()
        onNodeWithText("alterações não aplicadas").assertDoesNotExist()
        dragCanvas(100f, 40f)
        onNodeWithText("alterações não aplicadas").assertExists()
    }

    @Test
    fun `control C copies and control enter applies`() = runComposeUiTest {
        val app = openEditor()
        dragCanvas(100f, 40f)
        ctrl(Key.C)
        waitUntil(timeoutMillis = 5_000) { app.clipboard.copied.isNotEmpty() }

        ctrl(Key.Enter)
        waitUntil(timeoutMillis = 5_000) { app.container.navigator.current == Screen.Home }
        assertTrue(app.session.state.value.single().pngBytes.size != original.pngBytes.size)
    }

    @Test
    fun `control S saves through the dialog and remembers the file`() = runComposeUiTest {
        val app = openEditor()
        ctrl(Key.S)
        waitUntil(timeoutMillis = 5_000) { app.session.savedPaths.value.containsKey("a") }
    }

    @Test
    fun `the zoom buttons zoom and fit again`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("Ajustar à janela").assertIsNotEnabled()
        onNodeWithContentDescription("Ampliar").performClick()
        waitForIdle()
        onNodeWithContentDescription("Ajustar à janela").assertIsEnabled()
        onNodeWithContentDescription("Ajustar à janela").performClick()
        waitForIdle()
        onNodeWithContentDescription("Ajustar à janela").assertIsNotEnabled()
    }

    @Test
    fun `the wheel zooms and control zero fits`() = runComposeUiTest {
        openEditor()
        onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput { moveTo(center); scroll(-3f) }
        waitForIdle()
        onNodeWithContentDescription("Ajustar à janela").assertIsEnabled()
        ctrl(Key.Zero)
        waitForIdle()
        onNodeWithContentDescription("Ajustar à janela").assertIsNotEnabled()
    }

    @Test
    fun `escape leaves a clean editor`() = runComposeUiTest {
        val app = openEditor()
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitUntil(timeoutMillis = 5_000) { app.container.navigator.current == Screen.Home }
    }

    @Test
    fun `escape with unsaved edits asks first`() = runComposeUiTest {
        val app = openEditor()
        dragCanvas(100f, 40f)
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitUntilAtLeastOneExists(hasText("Descartar alterações?"), timeoutMillis = 5_000)
        assertTrue(app.container.navigator.current is Screen.Editor)
    }

    @Test
    fun `text and numbers are sized, not thickened`() = runComposeUiTest {
        openEditor()
        onNodeWithText("Espessura").assertExists()
        onNodeWithContentDescription("Texto").performClick()
        onNodeWithText("Tamanho").assertExists()
        onNodeWithText("Espessura").assertDoesNotExist()
    }
}

/** The first node with this content description when several tiles carry it. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.onAllNodesWithContentDescriptionFirst(description: String) =
    onAllNodes(hasContentDescription(description))[0]
