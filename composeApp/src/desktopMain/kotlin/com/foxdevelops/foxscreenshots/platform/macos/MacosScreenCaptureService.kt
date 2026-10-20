package com.foxdevelops.foxscreenshots.platform.macos

import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.capture.ScreenSize
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.platform.image.Png
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class MacCaptureException(message: String, val deniedPermission: Boolean = false) : Exception(message)

/**
 * macOS capture backend, through CoreGraphics (`CGWindowListCreateImage`) —
 * behind the Screen Recording permission.
 */
internal class MacosScreenCaptureService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val ownPid: Int = ProcessHandle.current().pid().toInt(),
) : ScreenCaptureService {

    private val cg by lazy { CoreGraphics.load() }
    private val cf by lazy { CoreFoundation.load() }

    override suspend fun grabFullVirtualScreen(): CaptureResult = grab(null)

    override suspend fun grabRegion(region: CaptureRegion): CaptureResult = grab(region)

    override suspend fun activeWindowRegion(): CaptureRegion? = io { activeWindow() }

    override suspend fun virtualScreenSize(): ScreenSize = io {
        val layout = readLayout()
        ScreenSize((layout.width * layout.scale).roundToInt(), (layout.height * layout.scale).roundToInt())
    }

    /** The union of every display in points, plus pixels per point of the main display. */
    private class Layout(val x: Double, val y: Double, val width: Double, val height: Double, val scale: Double)

    private fun readLayout(): Layout {
        val displays = IntArray(MAX_DISPLAYS)
        val count = IntByReference()
        if (cg.CGGetActiveDisplayList(MAX_DISPLAYS, displays, count) != 0 || count.value == 0) {
            throw MacCaptureException("No active display to capture")
        }
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (i in 0 until count.value) {
            val bounds = cg.CGDisplayBounds(displays[i])
            minX = minOf(minX, bounds.x)
            minY = minOf(minY, bounds.y)
            maxX = maxOf(maxX, bounds.x + bounds.width)
            maxY = maxOf(maxY, bounds.y + bounds.height)
        }

        var scale = 1.0
        val mode = cg.CGDisplayCopyDisplayMode(cg.CGMainDisplayID())
        if (mode != null) {
            val points = cg.CGDisplayModeGetWidth(mode).toLong()
            val pixels = cg.CGDisplayModeGetPixelWidth(mode).toLong()
            if (points > 0 && pixels > 0) scale = pixels.toDouble() / points
            cg.CGDisplayModeRelease(mode)
        }
        return Layout(minX, minY, maxX - minX, maxY - minY, scale)
    }

    private fun requireScreenRecording() {
        if (cg.CGPreflightScreenCaptureAccess() != 0.toByte()) return
        // Opens System Settings > Privacy the first time; the answer only takes effect on a later
        // launch, so this attempt still fails.
        cg.CGRequestScreenCaptureAccess()
        throw MacCaptureException("Screen Recording permission is not granted", deniedPermission = true)
    }

    private suspend fun grab(region: CaptureRegion?): CaptureResult = io {
        requireScreenRecording()
        val layout = readLayout()
        val full = cg.CGWindowListCreateImage(
            CGRect.ByValue(layout.x, layout.y, layout.width, layout.height),
            CoreGraphics.WINDOW_LIST_ON_SCREEN_ONLY,
            CoreGraphics.NULL_WINDOW_ID,
            CoreGraphics.WINDOW_IMAGE_DEFAULT,
        ) ?: throw MacCaptureException("CGWindowListCreateImage returned no image")

        var cropped: Pointer? = null
        try {
            val image = if (region == null) {
                full
            } else {
                val area = region.clampedTo(cg.CGImageGetWidth(full).toInt(), cg.CGImageGetHeight(full).toInt())
                if (area.isEmpty) throw MacCaptureException("The selected region is outside the screen")
                // CGImageCreateWithImageInRect crops in the image's own pixel space, which is exactly
                // the space the region is expressed in.
                cropped = cg.CGImageCreateWithImageInRect(
                    full,
                    CGRect.ByValue(area.x.toDouble(), area.y.toDouble(), area.width.toDouble(), area.height.toDouble()),
                ) ?: throw MacCaptureException("Could not crop the captured image")
                cropped
            }
            encode(image)
        } finally {
            cropped?.let(cg::CGImageRelease)
            cg.CGImageRelease(full)
        }
    }

    private fun encode(image: Pointer): CaptureResult {
        val width = cg.CGImageGetWidth(image).toInt()
        val height = cg.CGImageGetHeight(image).toInt()
        if (width <= 0 || height <= 0) throw MacCaptureException("Captured image has no area")
        if (cg.CGImageGetBitsPerPixel(image).toInt() != 32) throw MacCaptureException("Unsupported pixel depth")

        val provider = cg.CGImageGetDataProvider(image) ?: throw MacCaptureException("CGImage has no data provider")
        // CGDataProviderCopyData returns an owned CFData; the byte pointer is valid until it is released.
        val data = cg.CGDataProviderCopyData(provider) ?: throw MacCaptureException("Could not copy the image pixels")
        try {
            val bytes = cf.CFDataGetBytePtr(data)
            val length = cf.CFDataGetLength(data).toLong()
            if (bytes == null || length <= 0) throw MacCaptureException("CGImage pixel buffer is empty")
            val rowBytes = cg.CGImageGetBytesPerRow(image).toInt()
            // 32-bit little-endian premultiplied BGRA; declaring the frame opaque makes premultiplication a no-op.
            val png = Png.encodeOpaque(bytes.getByteArray(0, length.toInt()), width, height, rowBytes)
            return CaptureResult(
                id = "shot-${clock()}-${System.nanoTime()}",
                pngBytes = png,
                width = width,
                height = height,
                takenAtMillis = clock(),
            )
        } finally {
            cf.CFRelease(data)
        }
    }

    /**
     * The front-most normal-layer window that is not ours, in screenshot pixels.
     * `CGWindowListCopyWindowInfo` is ordered front to back, so the first match
     * is the window the user was looking at.
     */
    private fun activeWindow(): CaptureRegion? {
        val windows = cg.CGWindowListCopyWindowInfo(
            CoreGraphics.WINDOW_LIST_ON_SCREEN_ONLY or CoreGraphics.WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS,
            CoreGraphics.NULL_WINDOW_ID,
        ) ?: return null
        try {
            val layout = readLayout()
            val layerKey = CoreGraphics.key("kCGWindowLayer")
            val pidKey = CoreGraphics.key("kCGWindowOwnerPID")
            val boundsKey = CoreGraphics.key("kCGWindowBounds")
            val count = cf.CFArrayGetCount(windows).toLong()
            for (i in 0 until count) {
                val window = cf.CFArrayGetValueAtIndex(windows, com.sun.jna.NativeLong(i)) ?: continue
                // Layer 0 is the normal window layer; menus, the Dock and the wallpaper live elsewhere.
                if (readInt(window, layerKey) != 0) continue
                if (readInt(window, pidKey) == ownPid) continue

                val boundsDict = cf.CFDictionaryGetValue(window, boundsKey) ?: continue
                val rect = CGRect()
                if (cg.CGRectMakeWithDictionaryRepresentation(boundsDict, rect) == 0.toByte()) continue

                val region = CaptureRegion(
                    x = ((rect.x - layout.x) * layout.scale).roundToInt(),
                    y = ((rect.y - layout.y) * layout.scale).roundToInt(),
                    width = (rect.width * layout.scale).roundToInt(),
                    height = (rect.height * layout.scale).roundToInt(),
                ).clampedTo((layout.width * layout.scale).roundToInt(), (layout.height * layout.scale).roundToInt())
                if (region.width <= 1 || region.height <= 1) continue
                return region
            }
            return null
        } finally {
            cf.CFRelease(windows)
        }
    }

    private fun readInt(dict: Pointer, key: Pointer): Int? {
        val number = cf.CFDictionaryGetValue(dict, key) ?: return null
        val out = IntByReference()
        return if (cf.CFNumberGetValue(number, CoreFoundation.NUMBER_INT_TYPE, out) != 0.toByte()) out.value else null
    }

    /** Runs [block] off the UI thread and translates failures into the app-wide [CaptureException]. */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: MacCaptureException) {
            throw CaptureException(
                if (e.deniedPermission) CaptureFailure.ScreenRecordingDenied else CaptureFailure.DisplayUnavailable,
                e.message,
            )
        } catch (e: UnsatisfiedLinkError) {
            throw CaptureException(CaptureFailure.DisplayUnavailable, e.message)
        }
    }

    private companion object {
        const val MAX_DISPLAYS = 16
    }
}
