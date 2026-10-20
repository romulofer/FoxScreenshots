package com.foxdevelops.foxscreenshots.platform.portal

import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.platform.image.Png
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PortalScreenCaptureServiceTest {
    private fun frame(width: Int, height: Int) = Png.encodeOpaque(ByteArray(width * height * 4), width, height)

    private class FakePortal(val answer: () -> ByteArray) : ScreenshotPortal {
        var calls = 0
        override suspend fun capture(): ByteArray {
            calls++
            return answer()
        }
    }

    @Test
    fun `a full grab returns the portal frame with its measured size`() = runTest {
        val portal = FakePortal { frame(120, 80) }
        val result = PortalScreenCaptureService(portal).grabFullVirtualScreen()
        assertEquals(120, result.width)
        assertEquals(80, result.height)
    }

    @Test
    fun `a region grab takes a fresh frame and crops it`() = runTest {
        val portal = FakePortal { frame(120, 80) }
        val result = PortalScreenCaptureService(portal).grabRegion(CaptureRegion(10, 10, 50, 30))
        assertEquals(50, result.width)
        assertEquals(30, result.height)
        assertEquals(50 to 30, Png.size(result.pngBytes))
        assertEquals(1, portal.calls)
    }

    @Test
    fun `a region outside the frame is a display error`() = runTest {
        val service = PortalScreenCaptureService(FakePortal { frame(120, 80) })
        val error = assertFailsWith<CaptureException> { service.grabRegion(CaptureRegion(500, 500, 10, 10)) }
        assertEquals(CaptureFailure.DisplayUnavailable, error.failure)
    }

    @Test
    fun `the screen size comes from a frame`() = runTest {
        val size = PortalScreenCaptureService(FakePortal { frame(64, 48) }).virtualScreenSize()
        assertEquals(64, size.width)
        assertEquals(48, size.height)
    }

    @Test
    fun `wayland cannot report the active window`() = runTest {
        val portal = FakePortal { frame(10, 10) }
        assertNull(PortalScreenCaptureService(portal).activeWindowRegion())
        assertEquals(0, portal.calls)
    }

    @Test
    fun `a refused request is reported as denied`() = runTest {
        val service = PortalScreenCaptureService(FakePortal { throw PortalException(PortalFailure.Denied) })
        assertEquals(CaptureFailure.PortalDenied, assertFailsWith<CaptureException> { service.grabFullVirtualScreen() }.failure)
    }

    @Test
    fun `a missing portal is reported as unavailable`() = runTest {
        val service = PortalScreenCaptureService(FakePortal { throw PortalException(PortalFailure.Unavailable, "no bus") })
        val error = assertFailsWith<CaptureException> { service.grabFullVirtualScreen() }
        assertEquals(CaptureFailure.PortalUnavailable, error.failure)
        assertEquals("no bus", error.details)
    }

    @Test
    fun `something that is not a PNG is reported as unavailable`() = runTest {
        val service = PortalScreenCaptureService(FakePortal { ByteArray(64) })
        assertEquals(CaptureFailure.PortalUnavailable, assertFailsWith<CaptureException> { service.grabFullVirtualScreen() }.failure)
    }
}
