package com.foxdevelops.foxscreenshots.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.hasText
import com.foxdevelops.foxscreenshots.features.home.openImage
import com.foxdevelops.foxscreenshots.features.home.parentDirectory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShellFeaturesTest {
    @Test
    fun `quick delay chips set the timer delay`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithText("Atraso rápido").assertExists()
        onNodeWithText("10 s").performClick()
        waitForIdle()
        assertEquals(10, app.settings.state.value.timerDelaySeconds)
    }

    @Test
    fun `the about dialog shows version and privacy promise, then closes`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        app.container.navigator.aboutVisible = true
        waitUntilAtLeastOneExists(hasText("Sobre o FoxScreenShots"))
        onNodeWithText("v.9.9.9").assertExists()
        onNodeWithText("Fechar").performClick()
        waitForIdle()
        assertTrue(!app.container.navigator.aboutVisible)
    }

    @Test
    fun `the open image button files a picked image in the session and opens the editor`() = runComposeUiTest {
        val app = TestApp()
        app.output.pickedImage = solidPng(64, 32)
        setContent { AppRoot(app.container) }
        runBlocking { openImage(app.container) }
        assertEquals(1, app.session.state.value.size)
        assertIs<Screen.Editor>(app.container.navigator.current)
    }

    @Test
    fun `opening an unreadable file reports the failure and stays on the hub`() = runComposeUiTest {
        val app = TestApp()
        app.output.pickedImage = ByteArray(12)
        setContent { AppRoot(app.container) }
        runBlocking { openImage(app.container) }
        assertTrue(app.session.state.value.isEmpty())
        assertEquals(Screen.Home, app.container.navigator.current)
    }

    @Test
    fun `cancelling the picker changes nothing`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        runBlocking { openImage(app.container) }
        assertTrue(app.session.state.value.isEmpty())
    }

    @Test
    fun `settings edits the file name pattern`() = runComposeUiTest {
        val app = TestApp()
        setContent { AppRoot(app.container) }
        onNodeWithContentDescription("Configurações").performClick()
        onNodeWithText("foxshot_{date}_{time}").performTextReplacement("tela-{date}")
        waitForIdle()
        assertEquals("tela-{date}", app.settings.state.value.fileNamePattern)
    }

    @Test
    fun `parent directory handles both separators`() {
        assertEquals("/home/me", parentDirectory("/home/me/a.png"))
        assertEquals("C:\\Users\\me", parentDirectory("C:\\Users\\me\\a.png"))
        assertNull(parentDirectory("a.png"))
    }
}
