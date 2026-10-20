package com.foxdevelops.foxscreenshots.features.capture

import com.foxdevelops.foxscreenshots.FakeCaptureService
import com.foxdevelops.foxscreenshots.FakeClipboard
import com.foxdevelops.foxscreenshots.FakeImageProcessor
import com.foxdevelops.foxscreenshots.FakeOutput
import com.foxdevelops.foxscreenshots.FakeSurface
import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.settings.MemorySettingsStore
import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.features.home.SessionController
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CaptureControllerTest {
    private val service = FakeCaptureService()
    private val surface = FakeSurface()
    private val processor = FakeImageProcessor()
    private val session = SessionController()
    private val settings = SettingsController(MemorySettingsStore())
    private val clipboard = FakeClipboard()
    private val output = FakeOutput()

    private fun controller(clipboard: FakeClipboard = this.clipboard) =
        CaptureController(service, surface, processor, session, settings, clipboard, output)

    @Test
    fun `instant crops the frozen frame instead of grabbing again`() = runTest {
        val result = controller().captureInstant()
        assertNotNull(result)
        assertEquals(1, service.fullGrabs)
        assertTrue(service.regions.isEmpty(), "open menus must survive, so no second grab")
        assertEquals(listOf(CaptureRegion(10, 20, 30, 40).toRect()), processor.cropped)
        assertEquals(30, result.width)
        assertEquals(40, result.height)
    }

    @Test
    fun `instant hides the hub and always restores it`() = runTest {
        controller().captureInstant()
        assertEquals(listOf("hide", "select", "restore"), surface.events)
    }

    @Test
    fun `cancelling the selection records nothing`() = runTest {
        surface.selection = null
        assertNull(controller().captureInstant())
        assertTrue(session.state.value.isEmpty())
        assertTrue(clipboard.copied.isEmpty())
        assertEquals("restore", surface.events.last())
    }

    @Test
    fun `the frozen frame bitmap is released`() = runTest {
        controller().captureInstant()
        // one for the cropped result, one for the backdrop
        assertEquals(2, processor.released.size)
    }

    @Test
    fun `a selection dragged past the edge is clipped, and an empty one cancels`() = runTest {
        surface.selection = CaptureRegion(190, 90, 50, 50)
        val result = controller().captureInstant()
        assertEquals(10, result?.width)
        assertEquals(10, result?.height)

        surface.selection = CaptureRegion(500, 500, 10, 10)
        assertNull(controller().captureInstant())
    }

    @Test
    fun `timer waits the configured delay and then grabs the region live`() = runTest {
        settings.setTimerDelaySeconds(5)
        val result = controller().captureWithTimer()
        assertNotNull(result)
        assertEquals(5.seconds, surface.countdownFor)
        assertEquals(listOf(CaptureRegion(10, 20, 30, 40)), service.regions)
        assertEquals(listOf("hide", "select", "countdown", "restore"), surface.events)
    }

    @Test
    fun `timer accepts an explicit delay`() = runTest {
        controller().captureWithTimer(delay = 2.seconds)
        assertEquals(2.seconds, surface.countdownFor)
    }

    @Test
    fun `full screen grabs everything without a selection`() = runTest {
        assertNotNull(controller().captureFullScreen())
        assertTrue(surface.events.none { it == "select" })
    }

    @Test
    fun `active window grabs the focused window and fails when there is none`() = runTest {
        val result = controller().captureActiveWindow()
        assertEquals(50, result?.width)
        assertEquals(listOf(CaptureRegion(10, 10, 50, 40)), service.regions)

        service.activeWindow = null
        val error = assertFailsWith<CaptureException> { controller().captureActiveWindow() }
        assertEquals(CaptureFailure.NoActiveWindow, error.failure)
        assertEquals("restore", surface.events.last(), "the hub must come back after a failure")
    }

    @Test
    fun `repeat reuses the last region and does nothing before the first capture`() = runTest {
        val capture = controller()
        assertNull(capture.captureRepeat())

        capture.captureInstant()
        service.regions.clear()
        assertNotNull(capture.captureRepeat())
        assertEquals(listOf(CaptureRegion(10, 20, 30, 40)), service.regions)
    }

    @Test
    fun `every capture lands in the session and on the clipboard`() = runTest {
        controller().captureFullScreen()
        assertEquals(1, session.state.value.size)
        assertEquals(1, clipboard.copied.size)
    }

    @Test
    fun `a broken clipboard does not lose the capture`() = runTest {
        val result = controller(FakeClipboard(works = false)).captureFullScreen()
        assertNotNull(result)
        assertEquals(1, session.state.value.size)
    }

    @Test
    fun `auto-save writes to the output folder only when on`() = runTest {
        controller().captureFullScreen()
        assertTrue(output.saved.isEmpty())

        settings.setOutputDir("/shots")
        settings.setAutoSave(true)
        controller().captureFullScreen()
        assertEquals("/shots", output.saved.single().first)
    }

    @Test
    fun `an auto-save failure does not lose the capture`() = runTest {
        settings.setOutputDir("/shots")
        settings.setAutoSave(true)
        output.failing = true
        assertNotNull(controller().captureFullScreen())
        assertEquals(1, session.state.value.size)
    }

    @Test
    fun `dispatches by mode`() = runTest {
        val capture = controller()
        assertNotNull(capture.capture(CaptureMode.FullScreen))
        assertNotNull(capture.capture(CaptureMode.Instant))
        assertNotNull(capture.capture(CaptureMode.Timer))
        assertNotNull(capture.capture(CaptureMode.ActiveWindow))
        assertNotNull(capture.capture(CaptureMode.Repeat))
    }

    @Test
    fun `capture ids are unique within a session`() = runTest {
        val capture = controller()
        capture.captureFullScreen()
        capture.captureFullScreen()
        assertEquals(2, session.state.value.map { it.id }.toSet().size)
    }
}
