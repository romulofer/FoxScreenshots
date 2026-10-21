package com.foxdevelops.foxscreenshots.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.core.i18n.LocalStrings
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.core.i18n.StringsEn
import com.foxdevelops.foxscreenshots.core.i18n.StringsPt
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference
import com.foxdevelops.foxscreenshots.core.theme.FoxTheme
import com.foxdevelops.foxscreenshots.features.capture.CaptureSelectionOverlay
import com.foxdevelops.foxscreenshots.features.capture.FrozenFrame
import com.foxdevelops.foxscreenshots.features.editor.EDITOR_CANVAS_TAG
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.platform.image.SkiaImageProcessor
import org.junit.Assume.assumeTrue
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Regenerates the README images from the real screens:
 *
 * ```
 * FOX_README_SCREENSHOTS=$PWD/screenshots xvfb-run -a ./gradlew :composeApp:desktopTest --tests '*ReadmeScreenshotsTest*'
 * ```
 *
 * Skipped unless that variable names a folder. The "captures" in the images are drawn by [SyntheticShots].
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeScreenshotsTest {
    private val outDir: File? = System.getenv("FOX_README_SCREENSHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

    private fun requireOutput(): File {
        assumeTrue("set FOX_README_SCREENSHOTS to a folder to regenerate the README images", outDir != null)
        return outDir!!.also { it.mkdirs() }
    }

    private fun millisAt(hour: Int, minute: Int) =
        LocalDateTime.of(2026, 10, 21, hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun capture(id: String, w: Int, h: Int, png: ByteArray, at: Long) = CaptureResult(id, png, w, h, at)

    private fun ComposeUiTest.window(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
        setContent { Box(Modifier.size(width.dp, height.dp).testTag(SHOT)) { content() } }
    }

    private fun ComposeUiTest.save(dir: File, name: String) {
        // Thumbnails and the editor's image decode off the UI thread; give them time to land.
        repeat(3) {
            waitForIdle()
            Thread.sleep(500)
        }
        ImageIO.write(onNodeWithTag(SHOT).captureToImage().toAwtImage(), "png", File(dir, name))
    }

    private fun appFor(english: Boolean) = TestApp(language = if (english) "en" else "pt").also {
        it.settings.setTheme(ThemePreference.Light)
    }

    private fun strings(english: Boolean): Strings = if (english) StringsEn else StringsPt

    private fun suffix(english: Boolean) = if (english) "en" else "pt"

    // ---- hub ----

    private fun hub(english: Boolean) = runDesktopComposeUiTest(SCENE, SCENE) {
        val dir = requireOutput()
        val app = appFor(english)
        val st = strings(english)
        app.session.add(capture("c", 560, 320, SyntheticShots.png(SyntheticShots.terminal(560, 320)), millisAt(9, 12)))
        app.session.add(capture("b", 640, 420, SyntheticShots.png(SyntheticShots.settingsWindow(640, 420, english)), millisAt(9, 38)))
        app.session.add(capture("a", 640, 420, SyntheticShots.png(SyntheticShots.document(640, 420, english)), millisAt(9, 41)))
        app.session.markSaved("a", "/home/fox/Pictures/foxshot_20261021_094100.png")
        window(960, 700) { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasText(st.captureInstant))
        // The tooltip is part of what the window teaches: show it on the main button.
        onNodeWithText(st.captureInstant).performMouseInput { moveTo(center) }
        waitUntilAtLeastOneExists(hasText("${st.captureInstant} (PrintScreen)"), timeoutMillis = 5_000)
        save(dir, "janela-principal-${suffix(english)}.png")
    }

    @Test
    fun `hub, pt`() = hub(english = false)

    @Test
    fun `hub, en`() = hub(english = true)

    // ---- editor ----

    private fun editor(english: Boolean) = runDesktopComposeUiTest(SCENE, SCENE) {
        val dir = requireOutput()
        val app = appFor(english)
        val st = strings(english)
        val png = SyntheticShots.png(SyntheticShots.document(640, 420, english))
        val shot = capture("a", 640, 420, png, millisAt(9, 41))
        app.session.add(shot)
        app.container.navigator.push(Screen.Editor(shot))
        window(960, 700) { AppRoot(app.container) }
        waitUntilAtLeastOneExists(hasContentDescription(st.toolArrow), timeoutMillis = 5_000)
        waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(EDITOR_CANVAS_TAG)).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()

        // Image pixel -> position inside the canvas node (the image is centred, 1:1 when it fits).
        val node = onNodeWithTag(EDITOR_CANVAS_TAG).fetchSemanticsNode()
        val scale = minOf(node.size.width / 640f, node.size.height / 420f, 1f)
        val origin = Offset((node.size.width - 640 * scale) / 2f, (node.size.height - 420 * scale) / 2f)
        fun at(x: Float, y: Float) = origin + Offset(x * scale, y * scale)
        fun drag(from: Offset, to: Offset) = onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput {
            moveTo(from); press(); moveTo((from + to) / 2f); moveTo(to); release()
        }
        fun tap(point: Offset) = onNodeWithTag(EDITOR_CANVAS_TAG).performMouseInput { moveTo(point); press(); release() }

        onNodeWithContentDescription(st.toolRectangle).performClick()
        drag(at(14f, 64f), at(300f, 108f))
        onNodeWithContentDescription(st.toolHighlight).performClick()
        drag(at(24f, 112f), at(520f, 134f))
        onNodeWithContentDescription(st.toolPixelate).performClick()
        drag(at(24f, 176f), at(430f, 198f))
        onNodeWithContentDescription(st.toolArrow).performClick()
        drag(at(420f, 400f), at(236f, 318f))
        onNodeWithContentDescription(st.toolText).performClick()
        tap(at(300f, 360f))
        onNode(hasSetTextAction()).performTextInput(if (english) "Click here" else "Clique aqui")
        onNodeWithText(st.actionConfirm).performClick()
        onNodeWithContentDescription(st.toolNumber).performClick()
        tap(at(8f, 86f))
        tap(at(500f, 290f))
        // End on the select tool with the arrow picked, to show the dashed outline.
        onNodeWithContentDescription(st.toolSelect).performClick()
        tap(at(262f, 329.6f))
        save(dir, "editor-${suffix(english)}.png")
    }

    @Test
    fun `editor, pt`() = editor(english = false)

    @Test
    fun `editor, en`() = editor(english = true)

    // ---- settings ----

    private fun settings(english: Boolean) = runDesktopComposeUiTest(SCENE, SCENE) {
        val dir = requireOutput()
        val app = appFor(english)
        app.settings.setOutputDir("/home/fox/Pictures")
        app.container.navigator.openSettings()
        window(960, 980) { AppRoot(app.container) }
        waitForIdle()
        save(dir, "configuracoes-${suffix(english)}.png")
    }

    @Test
    fun `settings, pt`() = settings(english = false)

    @Test
    fun `settings, en`() = settings(english = true)

    // ---- selection ----

    private fun selection(english: Boolean) = runDesktopComposeUiTest(SCENE, SCENE) {
        val dir = requireOutput()
        val strings = strings(english)
        val processor = SkiaImageProcessor()
        val frame = FrozenFrame(processor.decode(SyntheticShots.png(SyntheticShots.desktop(1000, 640, english))), 1000, 640)
        setContent {
            FoxTheme(ThemePreference.Light) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    Box(Modifier.size(1000.dp, 640.dp).testTag(SHOT)) {
                        CaptureSelectionOverlay(frame = frame, adjustable = true, onSelected = {}, onCancel = {})
                    }
                }
            }
        }
        onNodeWithTag(SHOT).performMouseInput {
            moveTo(Offset(100f, 70f)); press(); moveTo(Offset(400f, 250f)); moveTo(Offset(670f, 390f)); release()
        }
        save(dir, "selecao-${suffix(english)}.png")
    }

    @Test
    fun `selection, pt`() = selection(english = false)

    @Test
    fun `selection, en`() = selection(english = true)

    private companion object {
        const val SHOT = "readme-shot"

        /** The test window is larger than any image, so nothing is clipped. */
        const val SCENE = 1200
    }
}
