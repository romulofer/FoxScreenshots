package com.foxdevelops.foxscreenshots.features.capture

import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.CaptureMode
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.core.storage.ClipboardService
import com.foxdevelops.foxscreenshots.core.storage.OutputService
import com.foxdevelops.foxscreenshots.features.editor.ImageProcessor
import com.foxdevelops.foxscreenshots.features.home.SessionController
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the capture flows end to end: hide the hub window, grab pixels, let the
 * user pick a region on the frozen frame, then hand the result to the session.
 *
 * Every flow returns `null` when the user cancels, and throws
 * [CaptureException] when the platform cannot deliver a frame. The hub window
 * is always restored, success or not.
 */
class CaptureController(
    private val service: ScreenCaptureService,
    private val surface: CaptureSurface,
    private val processor: ImageProcessor,
    private val session: SessionController,
    private val settings: SettingsController,
    private val clipboard: ClipboardService,
    private val output: OutputService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Guards against overlapping captures from toolbar, tray and hotkey. */
    private val busy = Mutex()

    /** Region selected during the last instant or timer capture; used by [captureRepeat]. */
    private var lastRegion: CaptureRegion? = null

    /** Entry point for the toolbar, the tray menu and the global hotkey. */
    suspend fun capture(mode: CaptureMode): CaptureResult? = when (mode) {
        CaptureMode.Instant -> captureInstant()
        CaptureMode.Timer -> captureWithTimer()
        CaptureMode.FullScreen -> captureFullScreen()
        CaptureMode.ActiveWindow -> captureActiveWindow()
        CaptureMode.Repeat -> captureRepeat()
    }

    /**
     * Instant mode: freeze every screen, drag a region, crop it out of the
     * frozen frame. Cropping the freeze — rather than re-grabbing — is what
     * makes open menus and tooltips survive in the shot.
     */
    suspend fun captureInstant(): CaptureResult? = guarded {
        run {
            val frame = freeze()
            try {
                val region = surface.selectRegion(frame) ?: return@run null
                lastRegion = region
                val area = region.clampedTo(frame.width, frame.height)
                if (area.isEmpty) return@run null
                val cropped = withContext(Dispatchers.Default) { processor.crop(frame.image, area.toRect()) }
                processor.release(cropped.image)
                record(newResult(cropped.pngBytes, cropped.width, cropped.height))
            } finally {
                processor.release(frame.image)
            }
        }
    }

    /**
     * Timer mode: frame a region, wait, then grab that region live so
     * menus/tooltips opened during the delay appear in the shot. The framing
     * step runs over a frozen snapshot, only as a backdrop — the pixels that end
     * up in the file are grabbed after the delay.
     */
    suspend fun captureWithTimer(delay: Duration? = null): CaptureResult? = guarded {
        run {
            val frame = freeze()
            val region = try {
                surface.selectRegion(frame)
            } finally {
                processor.release(frame.image)
            } ?: return@run null
            lastRegion = region
            surface.countdown(delay ?: settings.state.value.timerDelaySeconds.seconds)
            record(service.grabRegion(region))
        }
    }

    /** Whole virtual screen, no selection step. */
    suspend fun captureFullScreen(): CaptureResult? = guarded {
        run { record(service.grabFullVirtualScreen()) }
    }

    /** The window that had focus after the hub was hidden. */
    suspend fun captureActiveWindow(): CaptureResult? = guarded {
        run {
            val region = resolveActiveWindow()
            if (region == null || region.isEmpty) throw CaptureException(CaptureFailure.NoActiveWindow)
            record(service.grabRegion(region))
        }
    }

    /** Repeats the last instant or timer capture without showing the overlay. `null` if there was none. */
    suspend fun captureRepeat(): CaptureResult? = guarded {
        val region = lastRegion ?: return@guarded null
        run { record(service.grabRegion(region)) }
    }

    /** Rejects overlapping captures from toolbar, tray and hotkey. */
    private suspend fun guarded(body: suspend () -> CaptureResult?): CaptureResult? {
        if (!busy.tryLock()) return null
        try {
            return body()
        } finally {
            busy.unlock()
        }
    }

    /** Hides the hub for the duration of [body] and always brings it back. */
    private suspend fun run(body: suspend () -> CaptureResult?): CaptureResult? {
        surface.hideHub()
        try {
            return body()
        } finally {
            surface.restoreHub()
        }
    }

    private suspend fun freeze(): FrozenFrame {
        val frozen = service.grabFullVirtualScreen()
        val image = withContext(Dispatchers.Default) { processor.decode(frozen.pngBytes) }
        return FrozenFrame(image, frozen.width, frozen.height)
    }

    /** Prefer a non-empty focus after hide; ask twice in case the first read still sees nothing focused. */
    private suspend fun resolveActiveWindow(): CaptureRegion? {
        val first = service.activeWindowRegion()
        if (first != null && !first.isEmpty) return first
        return service.activeWindowRegion()
    }

    /**
     * Files the capture in the session and puts it on the clipboard. Copying
     * straight away is what makes the hotkey useful on its own: hit PrintScreen,
     * then paste. A clipboard-less session must not lose the capture, so the
     * failure is swallowed — the shot is still in the gallery.
     */
    private suspend fun record(result: CaptureResult): CaptureResult {
        session.add(result)
        try {
            clipboard.copyPng(result.pngBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Nothing to report from here: the capture is safe in the gallery.
        }
        autoSave(result)
        return result
    }

    /** Writes the capture to the output folder when auto-save is on. Best effort, like the clipboard. */
    private suspend fun autoSave(result: CaptureResult) {
        val current = settings.state.value
        val dir = current.outputDir
        if (!current.autoSave || dir == null) return
        try {
            output.savePngToDir(result.pngBytes, dir)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Folder gone/read-only: the capture stays in the session.
        }
    }

    private fun newResult(png: ByteArray, width: Int, height: Int) = CaptureResult(
        id = "shot-${clock()}-${session.state.value.size}",
        pngBytes = png,
        width = width,
        height = height,
        takenAtMillis = clock(),
    )
}
