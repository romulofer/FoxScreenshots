package com.foxdevelops.foxscreenshots.platform.x11

import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.capture.ScreenSize
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.platform.image.PixelOrder
import com.foxdevelops.foxscreenshots.platform.image.Png
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Linux/X11 capture backend.
 *
 * Reads pixels straight off the root window with `XGetImage`, which spans the
 * whole multi-monitor virtual screen, so no compositing step is needed. Each
 * call opens its own X connection and closes it before returning, so nothing
 * X-related is shared between threads.
 *
 * Wayland sessions never reach this backend: the X root window under Wayland is
 * not the real desktop, and a grab there would quietly return a black image.
 */
internal class X11ScreenCaptureService(
    private val xlib: () -> Xlib = { Xlib.load() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val ownPid: Long = ProcessHandle.current().pid(),
) : ScreenCaptureService {

    override suspend fun grabFullVirtualScreen(): CaptureResult = grab(null)

    override suspend fun grabRegion(region: CaptureRegion): CaptureResult = grab(region)

    override suspend fun activeWindowRegion(): CaptureRegion? = io { withDisplay { x, display -> activeWindow(x, display) } }

    override suspend fun virtualScreenSize(): ScreenSize = io {
        withDisplay { x, display ->
            val screen = x.XDefaultScreen(display)
            ScreenSize(x.XDisplayWidth(display, screen), x.XDisplayHeight(display, screen))
        }
    }

    private suspend fun grab(region: CaptureRegion?): CaptureResult = io {
        withDisplay { x, display ->
            val screen = x.XDefaultScreen(display)
            val screenWidth = x.XDisplayWidth(display, screen)
            val screenHeight = x.XDisplayHeight(display, screen)
            val area = (region ?: CaptureRegion(0, 0, screenWidth, screenHeight)).clampedTo(screenWidth, screenHeight)
            if (area.isEmpty) throw X11Exception("The selected region is outside the screen")

            val png = readPng(x, display, area)
            CaptureResult(
                id = "shot-${clock()}-${System.nanoTime()}",
                pngBytes = png,
                width = area.width,
                height = area.height,
                takenAtMillis = clock(),
            )
        }
    }

    private fun readPng(x: Xlib, display: Pointer, area: CaptureRegion): ByteArray {
        val root = x.XDefaultRootWindow(display)
        val imagePointer = x.XGetImage(
            display, root, area.x, area.y, area.width, area.height, Xlib.ALL_PLANES, Xlib.Z_PIXMAP,
        ) ?: throw X11Exception("XGetImage returned no data")
        val image = XImage(imagePointer)
        try {
            return encode(image)
        } finally {
            image.destroy()
        }
    }

    private fun encode(image: XImage): ByteArray {
        val lsbFirst = 0
        if (image.byteOrder != lsbFirst) throw X11Exception("Unsupported X server byte order (MSBFirst)")
        if (image.width <= 0 || image.height <= 0 || image.width > MAX_DIMENSION || image.height > MAX_DIMENSION) {
            throw X11Exception("XImage dimensions out of range (${image.width}x${image.height})")
        }
        if (image.bitsPerPixel != 32) throw X11Exception("Unsupported pixel depth (${image.bitsPerPixel} bpp)")
        val data = image.data ?: throw X11Exception("XImage has a null data pointer")
        if (image.bytesPerLine < image.width * 4) throw X11Exception("XImage bytesPerLine shorter than a scanline")

        // With LSBFirst byte order the low mask byte is the first byte in memory.
        val order = when {
            image.blueMask.toLong() and 0xFF == 0xFFL -> PixelOrder.Bgra
            image.redMask.toLong() and 0xFF == 0xFFL -> PixelOrder.Rgba
            else -> throw X11Exception(
                "Unsupported pixel layout (red 0x${image.redMask.toLong().toString(16)}, " +
                    "blue 0x${image.blueMask.toLong().toString(16)})",
            )
        }
        val bytes = data.getByteArray(0, image.bytesPerLine * image.height)
        return Png.encodeOpaque(bytes, image.width, image.height, image.bytesPerLine, order)
    }

    /**
     * Geometry of the active window in root coordinates, or `null` when nothing
     * usable is focused. The window manager's own answer (`_NET_ACTIVE_WINDOW`)
     * is asked first, because `XGetInputFocus` frequently reports a 1x1 focus
     * proxy that Muffin, Metacity and Mutter all park the input focus on.
     */
    private fun activeWindow(x: Xlib, display: Pointer): CaptureRegion? {
        val root = x.XDefaultRootWindow(display)
        val activeAtom = internAtom(x, display, "_NET_ACTIVE_WINDOW")
        val candidates = readCardinals(x, display, root, activeAtom) + focusedWindow(x, display)

        for (window in candidates) {
            if (window == 0L || window == 1L || window == root.toLong()) continue
            // Our own window is never the answer: the hub is hidden by the time this runs.
            if (windowPid(x, display, window) == ownPid) continue
            val region = windowRegion(x, display, root, window)
            if (region != null && !region.isEmpty) return region
        }
        return null
    }

    private fun focusedWindow(x: Xlib, display: Pointer): Long {
        val focus = NativeLongByReference()
        x.XGetInputFocus(display, focus, IntByReference())
        return focus.value.toLong()
    }

    /** Geometry of [window] in root coordinates, clipped to the screen; a 1-pixel window is a focus proxy, not a target. */
    private fun windowRegion(x: Xlib, display: Pointer, root: NativeLong, window: Long): CaptureRegion? {
        val w = IntByReference()
        val h = IntByReference()
        val ok = x.XGetGeometry(
            display, NativeLong(window), NativeLongByReference(), IntByReference(), IntByReference(),
            w, h, IntByReference(), IntByReference(),
        )
        if (ok == 0 || w.value <= 1 || h.value <= 1) return null

        // The geometry above is relative to the parent, so map (0,0) of the window onto the root.
        val absX = IntByReference()
        val absY = IntByReference()
        if (x.XTranslateCoordinates(display, NativeLong(window), root, 0, 0, absX, absY, NativeLongByReference()) == 0) {
            return null
        }
        val screen = x.XDefaultScreen(display)
        return CaptureRegion(absX.value, absY.value, w.value, h.value)
            .clampedTo(x.XDisplayWidth(display, screen), x.XDisplayHeight(display, screen))
    }

    private fun internAtom(x: Xlib, display: Pointer, name: String): NativeLong = x.XInternAtom(display, name, 1)

    /** Reads a 32-bit-format property as numbers; empty when absent. */
    private fun readCardinals(x: Xlib, display: Pointer, window: NativeLong, property: NativeLong): List<Long> {
        if (property.toLong() == 0L) return emptyList()
        val actualType = NativeLongByReference()
        val actualFormat = IntByReference()
        val itemCount = NativeLongByReference()
        val bytesAfter = NativeLongByReference()
        val data = PointerByReference()
        val status = x.XGetWindowProperty(
            display, window, property, NativeLong(0), NativeLong(MAX_LONGS), 0, NativeLong(0),
            actualType, actualFormat, itemCount, bytesAfter, data,
        )
        val buffer = data.value
        if (status != 0 || buffer == null) return emptyList()
        try {
            if (actualFormat.value != 32) return emptyList()
            // X hands `format 32` data back as C longs, 64 bits wide on the platforms this backend runs on.
            return List(itemCount.value.toInt()) { buffer.getLong(it * 8L) }
        } finally {
            x.XFree(buffer)
        }
    }

    private fun windowPid(x: Xlib, display: Pointer, window: Long): Long? {
        val atom = internAtom(x, display, "_NET_WM_PID")
        return readCardinals(x, display, NativeLong(window), atom).firstOrNull()
    }

    private inline fun <T> withDisplay(block: (Xlib, Pointer) -> T): T {
        val x = xlib()
        val display = x.XOpenDisplay(null) ?: throw X11Exception("Could not connect to the X server (is DISPLAY set?)")
        try {
            return block(x, display)
        } finally {
            x.XCloseDisplay(display)
        }
    }

    /** Runs [block] off the UI thread and translates X failures into the app-wide [CaptureException]. */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: X11Exception) {
            throw CaptureException(CaptureFailure.DisplayUnavailable, e.message)
        } catch (e: UnsatisfiedLinkError) {
            throw CaptureException(CaptureFailure.DisplayUnavailable, e.message)
        }
    }

    private companion object {
        const val MAX_DIMENSION = 16384
        const val MAX_LONGS = 1024L
    }
}
