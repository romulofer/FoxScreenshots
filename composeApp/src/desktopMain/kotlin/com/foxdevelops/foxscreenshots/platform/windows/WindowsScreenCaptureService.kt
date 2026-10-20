package com.foxdevelops.foxscreenshots.platform.windows

import com.foxdevelops.foxscreenshots.core.capture.CaptureException
import com.foxdevelops.foxscreenshots.core.capture.CaptureFailure
import com.foxdevelops.foxscreenshots.core.capture.ScreenCaptureService
import com.foxdevelops.foxscreenshots.core.capture.ScreenSize
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult
import com.foxdevelops.foxscreenshots.platform.image.Png
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class Win32Exception(message: String) : Exception(message)

/** `dwmapi.dll`, for the window frame without the invisible resize border. */
private interface Dwmapi : StdCallLibrary {
    fun DwmGetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: WinDef.RECT, size: Int): Int
}

/**
 * Windows capture backend.
 *
 * Copies the whole multi-monitor virtual screen with GDI: a `BitBlt` from the
 * desktop device context into a top-down 32-bit DIB section, whose bits are
 * already B, G, R, X — the layout [Png.encodeOpaque] takes directly.
 *
 * `CAPTUREBLT` is ORed into the raster op so layered windows (alpha overlays,
 * drop shadows) appear in the grab instead of leaving holes.
 */
internal class WindowsScreenCaptureService(
    private val clock: () -> Long = System::currentTimeMillis,
) : ScreenCaptureService {

    override suspend fun grabFullVirtualScreen(): CaptureResult = grab(null)

    override suspend fun grabRegion(region: CaptureRegion): CaptureResult = grab(region)

    override suspend fun activeWindowRegion(): CaptureRegion? = io { activeWindow() }

    override suspend fun virtualScreenSize(): ScreenSize = io {
        val user32 = User32.INSTANCE
        val width = user32.GetSystemMetrics(SM_CXVIRTUALSCREEN)
        val height = user32.GetSystemMetrics(SM_CYVIRTUALSCREEN)
        if (width <= 0 || height <= 0) throw Win32Exception("The virtual screen reports no area")
        ScreenSize(width, height)
    }

    private suspend fun grab(region: CaptureRegion?): CaptureResult = io {
        val user32 = User32.INSTANCE
        val gdi32 = GDI32.INSTANCE
        val originX = user32.GetSystemMetrics(SM_XVIRTUALSCREEN)
        val originY = user32.GetSystemMetrics(SM_YVIRTUALSCREEN)
        val fullWidth = user32.GetSystemMetrics(SM_CXVIRTUALSCREEN)
        val fullHeight = user32.GetSystemMetrics(SM_CYVIRTUALSCREEN)
        if (fullWidth <= 0 || fullHeight <= 0) throw Win32Exception("The virtual screen reports no area")

        val area = (region ?: CaptureRegion(0, 0, fullWidth, fullHeight)).clampedTo(fullWidth, fullHeight)
        if (area.isEmpty) throw Win32Exception("The selected region is outside the screen")

        val screenDc = user32.GetDC(null) ?: throw Win32Exception("GetDC(NULL) returned no screen device context")
        var memDc: WinDef.HDC? = null
        var dib: WinDef.HBITMAP? = null
        var original: WinNT.HANDLE? = null
        try {
            memDc = gdi32.CreateCompatibleDC(screenDc) ?: throw Win32Exception("CreateCompatibleDC failed")

            val info = WinGDI.BITMAPINFO()
            info.bmiHeader.biSize = info.bmiHeader.size()
            info.bmiHeader.biWidth = area.width
            // A negative height requests a top-down DIB, so row 0 is the top of the screen.
            info.bmiHeader.biHeight = -area.height
            info.bmiHeader.biPlanes = 1
            info.bmiHeader.biBitCount = 32
            info.bmiHeader.biCompression = WinGDI.BI_RGB

            val bits = PointerByReference()
            dib = gdi32.CreateDIBSection(memDc, info, WinGDI.DIB_RGB_COLORS, bits, null, 0)
            val pixels = bits.value
            if (dib == null || pixels == null) throw Win32Exception("CreateDIBSection returned no pixel buffer")

            original = gdi32.SelectObject(memDc, dib)
            val copied = gdi32.BitBlt(
                memDc, 0, 0, area.width, area.height, screenDc, originX + area.x, originY + area.y,
                SRCCOPY or CAPTUREBLT,
            )
            if (!copied) throw Win32Exception("BitBlt failed to copy the screen")

            // 32-bit DIB rows are already DWORD-aligned, so the stride is exactly width * 4.
            val png = Png.encodeOpaque(pixels.getByteArray(0, area.width * area.height * 4), area.width, area.height)
            CaptureResult(
                id = "shot-${clock()}-${System.nanoTime()}",
                pngBytes = png,
                width = area.width,
                height = area.height,
                takenAtMillis = clock(),
            )
        } finally {
            // DeleteObject silently fails on a bitmap still selected into a DC, so the original goes back first.
            if (memDc != null && original != null) gdi32.SelectObject(memDc, original)
            if (dib != null) gdi32.DeleteObject(dib)
            if (memDc != null) gdi32.DeleteDC(memDc)
            user32.ReleaseDC(null, screenDc)
        }
    }

    /**
     * The foreground window's frame, translated to virtual-screen origin, or
     * `null` when nothing usable is focused or the focus is this app (the hub is
     * hidden by the time this runs; framing where it used to be would capture
     * the desktop underneath it).
     */
    private fun activeWindow(): CaptureRegion? {
        val user32 = User32.INSTANCE
        val hwnd = user32.GetForegroundWindow() ?: return null

        val pid = IntByReference()
        user32.GetWindowThreadProcessId(hwnd, pid)
        if (pid.value == Kernel32.INSTANCE.GetCurrentProcessId()) return null

        val frame = WinDef.RECT()
        if (!windowFrame(hwnd, frame)) return null

        val originX = user32.GetSystemMetrics(SM_XVIRTUALSCREEN)
        val originY = user32.GetSystemMetrics(SM_YVIRTUALSCREEN)
        val region = CaptureRegion(
            x = frame.left - originX,
            y = frame.top - originY,
            width = frame.right - frame.left,
            height = frame.bottom - frame.top,
        ).clampedTo(
            user32.GetSystemMetrics(SM_CXVIRTUALSCREEN),
            user32.GetSystemMetrics(SM_CYVIRTUALSCREEN),
        )
        // A one-pixel frame is a hidden or collapsed window, not a capture target.
        return region.takeIf { it.width > 1 && it.height > 1 }
    }

    /** Prefers the DWM frame (no invisible resize border on Windows 10/11), falling back to `GetWindowRect`. */
    private fun windowFrame(hwnd: WinDef.HWND, out: WinDef.RECT): Boolean {
        val dwm = runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
        if (dwm != null) {
            val rect = WinDef.RECT()
            if (dwm.DwmGetWindowAttribute(hwnd, DWMWA_EXTENDED_FRAME_BOUNDS, rect, rect.size()) == 0) {
                out.left = rect.left
                out.top = rect.top
                out.right = rect.right
                out.bottom = rect.bottom
                return true
            }
        }
        return User32.INSTANCE.GetWindowRect(hwnd, out)
    }

    /** Runs [block] off the UI thread and translates Win32 failures into the app-wide [CaptureException]. */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Win32Exception) {
            throw CaptureException(CaptureFailure.DisplayUnavailable, e.message)
        } catch (e: UnsatisfiedLinkError) {
            throw CaptureException(CaptureFailure.DisplayUnavailable, e.message)
        }
    }

    private companion object {
        const val SM_XVIRTUALSCREEN = 76
        const val SM_YVIRTUALSCREEN = 77
        const val SM_CXVIRTUALSCREEN = 78
        const val SM_CYVIRTUALSCREEN = 79
        const val SRCCOPY = 0x00CC0020
        const val CAPTUREBLT = 0x40000000
        const val DWMWA_EXTENDED_FRAME_BOUNDS = 9
    }
}
