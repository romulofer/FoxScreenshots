package com.foxdevelops.foxscreenshots.platform.x11

import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.platform.image.Png
import com.foxdevelops.foxscreenshots.platform.session.DesktopSession
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Real grabs against a live X server. Skipped without a display (CI runs it under xvfb). */
class X11CaptureSmokeTest {
    private val service = X11ScreenCaptureService()

    private fun requireX11() = assumeTrue(DesktopSession.detect() == DesktopSession.X11)

    @Test
    fun `the full grab matches the reported virtual screen size`() = runTest {
        requireX11()
        val size = service.virtualScreenSize()
        val frame = service.grabFullVirtualScreen()
        assertEquals(size.width, frame.width)
        assertEquals(size.height, frame.height)
        assertEquals(frame.width to frame.height, Png.size(frame.pngBytes))
    }

    @Test
    fun `a region grab returns just that region`() = runTest {
        requireX11()
        val frame = service.grabRegion(CaptureRegion(0, 0, 64, 48))
        assertEquals(64, frame.width)
        assertEquals(48, frame.height)
    }

    @Test
    fun `a region hanging off the screen is clipped`() = runTest {
        requireX11()
        val size = service.virtualScreenSize()
        val frame = service.grabRegion(CaptureRegion(size.width - 10, size.height - 10, 100, 100))
        assertEquals(10, frame.width)
        assertEquals(10, frame.height)
    }

    @Test
    fun `a region outside the screen is a display error`() = runTest {
        requireX11()
        val size = service.virtualScreenSize()
        val error = kotlin.runCatching { service.grabRegion(CaptureRegion(size.width + 5, 0, 10, 10)) }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is com.foxdevelops.foxscreenshots.core.capture.CaptureException)
    }

    @Test
    fun `active window lookup does not crash`() = runTest {
        requireX11()
        service.activeWindowRegion() // may be null on a bare server; must not throw
    }

    @Test
    fun `active window is the focused window, measured in root coordinates`() = runTest {
        assumeTrue("set FOX_ISOLATED_DISPLAY=1 under xvfb-run", System.getenv("FOX_ISOLATED_DISPLAY") == "1")
        requireX11()
        val window = ProcessBuilder("xmessage", "-geometry", "300x200+120+80", "-name", "fox-probe", "hello").start()
        try {
            val id = run {
                val search = ProcessBuilder("xdotool", "search", "--sync", "--onlyvisible", "--pid", window.pid().toString()).start()
                if (!search.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    search.destroyForcibly()
                    null
                } else {
                    search.inputStream.bufferedReader().readLine()?.trim()
                }
            }
            assumeTrue("xmessage/xdotool unavailable", id != null)
            ProcessBuilder("xdotool", "windowfocus", id).start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            Thread.sleep(300)
            val region = assertNotNull(service.activeWindowRegion())
            assertEquals(300, region.width)
            assertEquals(200, region.height)
            assertEquals(120, region.x)
            assertEquals(80, region.y)
        } finally {
            window.destroy()
        }
    }
}
