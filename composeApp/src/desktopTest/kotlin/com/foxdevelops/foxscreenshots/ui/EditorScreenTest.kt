package com.foxdevelops.foxscreenshots.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.ComposeUiTest
import com.foxdevelops.foxscreenshots.features.editor.EDITOR_CANVAS_TAG
import com.foxdevelops.foxscreenshots.platform.image.Png
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EditorScreenTest {
    private val original = testCapture("a", 400, 300)

    private fun ComposeUiTest.openEditor(app: TestApp = TestApp()): TestApp {
        app.session.add(original)
        app.container.navigator.push(Screen.Editor(original))
        setContent { AppRoot(app.container) }
        // The base image decodes off the UI thread; the canvas appears once it has.
        waitUntilAtLeastOneExists(hasContentDescription("Seta"), timeoutMillis = 5_000)
        waitUntil(timeoutMillis = 5_000) { onAllNodesNoCanvasSpinner() }
        return app
    }

    private fun ComposeUiTest.onAllNodesNoCanvasSpinner(): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(EDITOR_CANVAS_TAG)).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.tapCanvas() = onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput { click(center) }

    private fun ComposeUiTest.dragCanvas(dx: Float, dy: Float) = onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
        moveTo(center - Offset(dx / 2, dy / 2))
        press()
        moveBy(Offset(dx / 2, dy / 2))
        moveBy(Offset(dx / 2, dy / 2))
        release()
    }

    @Test
    fun `opens clean with every tool on the rail`() = runComposeUiTest {
        openEditor()
        onNodeWithText("Editor").assertExists()
        onNodeWithText("Aplicar na captura").assertIsNotEnabled()
        for (tool in listOf("Recortar", "Seta", "Retângulo", "Elipse", "Marca-texto", "Desfoque", "Pixelar", "Caneta", "Texto", "Marcador numerado")) {
            onNodeWithContentDescription(tool).assertExists()
        }
        onNodeWithContentDescription("Desfazer").assertIsNotEnabled()
        onNodeWithContentDescription("Refazer").assertIsNotEnabled()
    }

    @Test
    fun `a numbered marker makes the document dirty and undoable`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("Marcador numerado").performClick()
        tapCanvas()
        onNodeWithText("• Editor").assertExists()
        onNodeWithContentDescription("Desfazer").assertIsEnabled()
        onNodeWithText("Aplicar na captura").assertIsEnabled()

        onNodeWithContentDescription("Desfazer").performClick()
        onNodeWithText("Editor").assertExists()
        onNodeWithContentDescription("Refazer").assertIsEnabled()
    }

    @Test
    fun `dragging with the arrow adds an annotation`() = runComposeUiTest {
        openEditor()
        dragCanvas(120f, 60f)
        onNodeWithText("• Editor").assertExists()
    }

    @Test
    fun `a click with a drag tool leaves nothing behind`() = runComposeUiTest {
        openEditor()
        tapCanvas()
        onNodeWithText("Editor").assertExists()
        onNodeWithContentDescription("Desfazer").assertIsNotEnabled()
    }

    @Test
    fun `control z undoes and control shift z redoes`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("Marcador numerado").performClick()
        tapCanvas()
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft); pressKey(Key.Z); keyUp(Key.CtrlLeft)
        }
        onNodeWithText("Editor").assertExists()
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.Z); keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft)
        }
        onNodeWithText("• Editor").assertExists()
    }

    @Test
    fun `the text tool asks for a caption and ignores an empty one`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("Texto").performClick()
        tapCanvas()
        onNodeWithText("Adicionar texto").assertExists()
        onNodeWithText("OK").performClick()
        onNodeWithText("Editor").assertExists()

        tapCanvas()
        onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("olá")
        onNodeWithText("OK").performClick()
        onNodeWithText("• Editor").assertExists()
    }

    @Test
    fun `apply bakes the edits into the session entry and returns to the hub`() = runComposeUiTest {
        val app = openEditor()
        onNodeWithContentDescription("Marcador numerado").performClick()
        tapCanvas()
        onNodeWithText("Aplicar na captura").performClick()
        waitUntil(timeoutMillis = 5_000) { app.session.state.value.single().pngBytes.size != original.pngBytes.size }

        val applied = app.session.state.value.single()
        assertEquals(original.id, applied.id)
        assertEquals(400 to 300, applied.width to applied.height)
        assertEquals(400 to 300, Png.size(applied.pngBytes))
        waitUntilAtLeastOneExists(hasText("Instantâneo"), timeoutMillis = 5_000)
        waitUntilAtLeastOneExists(hasText("Alterações aplicadas"), timeoutMillis = 5_000)
    }

    @Test
    fun `leaving with unsaved edits asks first`() = runComposeUiTest {
        val app = openEditor()
        onNodeWithContentDescription("Marcador numerado").performClick()
        tapCanvas()
        onNodeWithContentDescription("Fechar").performClick()
        onNodeWithText("Descartar alterações?").assertExists()
        onNodeWithText("Cancelar").performClick()
        onNodeWithText("• Editor").assertExists()

        onNodeWithContentDescription("Fechar").performClick()
        onNodeWithText("Descartar").performClick()
        onNodeWithText("Instantâneo").assertExists()
        assertContentEquals(original.pngBytes, app.session.state.value.single().pngBytes)
    }

    @Test
    fun `leaving a clean editor needs no confirmation`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("Fechar").performClick()
        onNodeWithText("Instantâneo").assertExists()
        onNodeWithText("Descartar alterações?").assertDoesNotExist()
    }

    @Test
    fun `copy exports the flattened image to the clipboard`() = runComposeUiTest {
        val app = openEditor()
        onNodeWithContentDescription("Marcador numerado").performClick()
        tapCanvas()
        onNodeWithContentDescription("Copiar").performClick()
        waitUntil(timeoutMillis = 5_000) { app.clipboard.copied.isNotEmpty() }
        assertEquals(400 to 300, Png.size(app.clipboard.copied.single()))
        assertFalse(app.clipboard.copied.single().contentEquals(original.pngBytes), "the marker is baked in")
    }

    @Test
    fun `fill appears only for the rectangle tool`() = runComposeUiTest {
        openEditor()
        onNodeWithText("Preencher").assertDoesNotExist()
        onNodeWithContentDescription("Retângulo").performClick()
        onNodeWithText("Preencher").assertExists()
    }

    @Test
    fun `ink colors are offered`() = runComposeUiTest {
        openEditor()
        onNodeWithContentDescription("#e53935").assertExists()
        onNodeWithContentDescription("#ffffff").assertExists()
        assertTrue(true)
    }
}
