package com.foxdevelops.foxscreenshots.platform.portal

import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.capture.ScreenSize
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.platform.image.Png
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wayland capture backend, through `xdg-desktop-portal`.
 *
 * Wayland gives a client no way to read the screen, so every frame comes from
 * the portal, which asks the user once and then hands over a finished PNG of
 * the whole desktop. The portal has no notion of a region: [grabRegion] takes a
 * fresh full frame and crops it, which is also what makes timer mode work there.
 */
class PortalScreenCaptureService(
    private val portal: ScreenshotPortal,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScreenCaptureService {

    override suspend fun grabFullVirtualScreen(): CaptureResult {
        val frame = capture()
        return result(frame.png, frame.width, frame.height)
    }

    override suspend fun grabRegion(region: CaptureRegion): CaptureResult {
        val full = capture()
        val cropped = withContext(Dispatchers.Default) { Png.crop(full.png, region) }
            ?: throw CaptureException(CaptureFailure.DisplayUnavailable, "the selected region is outside the screen")
        return result(cropped.pngBytes, cropped.width, cropped.height)
    }

    /** Always `null`: under Wayland a client cannot ask what other windows are, let alone where. */
    override suspend fun activeWindowRegion(): CaptureRegion? = null

    /** So "active window" hands the choice to the desktop's own picker, the only way a Wayland client can capture one window. */
    override val windowCaptureUsesSystemPicker: Boolean = true

    override suspend fun grabWithSystemPicker(): CaptureResult? {
        val frame = try {
            capture(interactive = true)
        } catch (e: CaptureCancelled) {
            return null
        }
        return result(frame.png, frame.width, frame.height)
    }

    override suspend fun virtualScreenSize(): ScreenSize {
        val frame = capture()
        return ScreenSize(frame.width, frame.height)
    }

    private class Frame(val png: ByteArray, val width: Int, val height: Int)

    /** The user closed the picker: unwinds to [grabWithSystemPicker], which turns it into `null`. */
    private class CaptureCancelled : Exception()

    /** Runs a portal request and measures what came back, translating every failure into [CaptureException]. */
    private suspend fun capture(interactive: Boolean = false): Frame {
        try {
            val png = if (interactive) portal.captureInteractive() else portal.capture()
            val (width, height) = Png.size(png)
            return Frame(png, width, height)
        } catch (e: PortalException) {
            if (e.failure == PortalFailure.Cancelled && interactive) throw CaptureCancelled()
            throw CaptureException(
                when (e.failure) {
                    PortalFailure.Denied, PortalFailure.Cancelled -> CaptureFailure.PortalDenied
                    PortalFailure.Unavailable -> CaptureFailure.PortalUnavailable
                },
                e.details,
            )
        } catch (e: IllegalArgumentException) {
            throw CaptureException(
                CaptureFailure.PortalUnavailable,
                "the portal returned something that is not a PNG: ${e.message}",
            )
        }
    }

    private fun result(png: ByteArray, width: Int, height: Int) = CaptureResult(
        id = "shot-${clock()}-${System.nanoTime()}",
        pngBytes = png,
        width = width,
        height = height,
        takenAtMillis = clock(),
    )
}
