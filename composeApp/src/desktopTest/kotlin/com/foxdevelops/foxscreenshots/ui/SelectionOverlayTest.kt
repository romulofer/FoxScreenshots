package com.foxdevelops.foxscreenshots.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.unit.dp
import com.foxdevelops.foxscreenshots.features.capture.CaptureSelectionOverlay
import com.foxdevelops.foxscreenshots.features.capture.FrozenFrame
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SelectionOverlayTest {
    private class Outcome {
        var region: CaptureRegion? = null
        var selected = 0
        var cancelled = 0
    }

    /** An overlay window of [windowWidth]x[windowHeight] over a frozen frame of [frameWidth]x[frameHeight]. */
    private fun ComposeUiTest.show(
        frameWidth: Int,
        frameHeight: Int,
        windowWidth: Int,
        windowHeight: Int,
        adjustable: Boolean = false,
    ): Outcome {
        val outcome = Outcome()
        val frame = FrozenFrame(ImageBitmap(frameWidth, frameHeight), frameWidth, frameHeight)
        setContent {
            Box(Modifier.size(windowWidth.dp, windowHeight.dp)) {
                CaptureSelectionOverlay(
                    frame = frame,
                    adjustable = adjustable,
                    onSelected = { outcome.region = it; outcome.selected++ },
                    onCancel = { outcome.cancelled++ },
                )
            }
        }
        return outcome
    }

    private fun ComposeUiTest.drag(from: Offset, to: Offset) = onRoot().performMouseInput {
        moveTo(from)
        press()
        moveTo((from + to) / 2f)
        moveTo(to)
        release()
    }

    @Test
    fun `a window the size of the frame reports the drag in screenshot pixels`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        assertEquals(CaptureRegion(100, 100, 200, 150), outcome.region)
    }

    @Test
    fun `any drag direction gives the same region`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600)
        drag(Offset(300f, 250f), Offset(100f, 100f))
        assertEquals(CaptureRegion(100, 100, 200, 150), outcome.region)
    }

    @Test
    fun `a smaller window shows the frame shrunk but still reports exact pixels`() = runComposeUiTest {
        val outcome = show(1600, 1200, 800, 600)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        assertEquals(CaptureRegion(200, 200, 400, 300), outcome.region)
    }

    @Test
    fun `a stray click is not a selection`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600)
        drag(Offset(100f, 100f), Offset(102f, 101f))
        assertEquals(0, outcome.selected)
        assertNull(outcome.region)
    }

    @Test
    fun `escape cancels`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600)
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, outcome.cancelled)
    }

    @Test
    fun `right click cancels`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600)
        onRoot().performMouseInput {
            moveTo(Offset(50f, 50f))
            press(androidx.compose.ui.test.MouseButton.Secondary)
            release(androidx.compose.ui.test.MouseButton.Secondary)
        }
        waitForIdle()
        assertEquals(1, outcome.cancelled)
        assertEquals(0, outcome.selected)
    }

    @Test
    fun `only one answer is ever given`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        onRoot().performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, outcome.selected)
        assertEquals(0, outcome.cancelled)
        assertTrue(outcome.region != null)
    }

    // ---- adjustable mode: the drag leaves a pending selection ----

    private fun ComposeUiTest.pressKeys(keys: List<Key>) = onRoot().performKeyInput { keys.forEach { pressKey(it) } }

    private fun ComposeUiTest.pressKeys(key: Key) = pressKeys(listOf(key))

    @Test
    fun `an adjustable drag waits for confirmation instead of capturing on release`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        waitForIdle()
        assertEquals(0, outcome.selected)

        pressKeys(Key.Enter)
        waitForIdle()
        assertEquals(CaptureRegion(100, 100, 200, 150), outcome.region)
        assertEquals(1, outcome.selected)
    }

    @Test
    fun `enter with no selection does nothing`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        pressKeys(Key.Enter)
        waitForIdle()
        assertEquals(0, outcome.selected)
    }

    @Test
    fun `an edge can be dragged to resize the pending selection`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        // The bottom-right corner is a grip: pull it out.
        drag(Offset(300f, 250f), Offset(400f, 350f))
        pressKeys(Key.Enter)
        waitForIdle()
        assertEquals(CaptureRegion(100, 100, 300, 250), outcome.region)
    }

    @Test
    fun `dragging inside the pending selection moves it`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        drag(Offset(200f, 175f), Offset(250f, 200f))
        pressKeys(Key.Enter)
        waitForIdle()
        assertEquals(CaptureRegion(150, 125, 200, 150), outcome.region)
    }

    @Test
    fun `arrow keys nudge by a pixel, shift by ten`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        pressKeys(listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionRight, Key.DirectionDown))
        onRoot().performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.ShiftLeft)
        }
        pressKeys(Key.Enter)
        waitForIdle()
        // +3 right, -10 left, +1 down.
        assertEquals(CaptureRegion(93, 101, 200, 150), outcome.region)
    }

    @Test
    fun `a double click on the pending selection captures it`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        onRoot().performMouseInput {
            doubleClick(Offset(200f, 175f))
        }
        waitForIdle()
        assertEquals(CaptureRegion(100, 100, 200, 150), outcome.region)
    }

    @Test
    fun `the capture button confirms`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        // Under the bottom-right corner: see SelectionGeometry.confirmButton.
        onRoot().performMouseInput {
            click(Offset(250f, 275f))
        }
        waitForIdle()
        assertEquals(CaptureRegion(100, 100, 200, 150), outcome.region)
    }

    @Test
    fun `a new drag outside the pending selection starts over`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        drag(Offset(500f, 400f), Offset(600f, 500f))
        pressKeys(Key.Enter)
        waitForIdle()
        assertEquals(CaptureRegion(500, 400, 100, 100), outcome.region)
    }

    @Test
    fun `escape cancels a pending selection`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(300f, 250f))
        pressKeys(Key.Escape)
        waitForIdle()
        assertEquals(1, outcome.cancelled)
        assertEquals(0, outcome.selected)
    }

    @Test
    fun `a stray click does not leave a pending selection`() = runComposeUiTest {
        val outcome = show(800, 600, 800, 600, adjustable = true)
        drag(Offset(100f, 100f), Offset(102f, 101f))
        pressKeys(Key.Enter)
        waitForIdle()
        assertEquals(0, outcome.selected)
    }
}
