package com.foxdevelops.foxscreenshots.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PreviewScreenTest {
    private fun androidx.compose.ui.test.ComposeUiTest.openPreview(): TestApp {
        val app = TestApp()
        val capture = testCapture("p", 320, 200)
        app.session.add(capture)
        app.container.navigator.push(Screen.Preview(capture))
        setContent { AppRoot(app.container) }
        return app
    }

    @Test
    fun `shows the size and the usage hint`() = runComposeUiTest {
        openPreview()
        onNodeWithText("Pré-visualização · 320×200").assertExists()
        onNodeWithText("Role para ampliar · Esc fecha").assertExists()
    }

    @Test
    fun `the close button returns to the hub`() = runComposeUiTest {
        openPreview()
        onNodeWithContentDescription("Fechar").performClick()
        onNodeWithText("Instantâneo").assertExists()
    }

    @Test
    fun `escape returns to the hub`() = runComposeUiTest {
        openPreview()
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithText("Instantâneo").assertExists()
    }
}
