package com.foxdevelops.foxscreenshots.core.capture

import com.foxdevelops.foxscreenshots.model.CaptureRegion
import com.foxdevelops.foxscreenshots.model.CaptureResult

/** How a capture is framed. */
enum class CaptureMode { Instant, Timer, FullScreen, ActiveWindow, Repeat }

/** Why a capture could not be taken. A closed set so the UI can localize each case. */
enum class CaptureFailure {
    /** The desktop refused the screenshot, or the user dismissed its permission dialog (Wayland portal). */
    PortalDenied,

    /** No screenshot portal answered: nothing can capture on this session. */
    PortalUnavailable,

    /** No backend is written for this operating system. */
    PlatformUnsupported,

    /** The display server refused the connection or the grab. */
    DisplayUnavailable,

    /** macOS withheld the Screen Recording permission the capture APIs need. */
    ScreenRecordingDenied,

    /** Window capture was asked for while nothing was focused. */
    NoActiveWindow,

    /** A capture was triggered before the app window existed. */
    WindowNotReady,
}

/**
 * A capture that could not be taken. Backends translate platform failures into
 * this so the UI has a single type to catch; [details] is developer-facing
 * context and never contains screen content.
 */
class CaptureException(val failure: CaptureFailure, val details: String? = null) :
    Exception(if (details == null) failure.name else "${failure.name}: $details")

/** Size of the virtual screen in physical pixels. */
data class ScreenSize(val width: Int, val height: Int)

/**
 * Platform-agnostic screen capture contract. The UI and editor depend only on
 * this interface; per-OS implementations are selected at runtime.
 */
interface ScreenCaptureService {
    /** Grabs a full-resolution snapshot of every monitor, composited into one image. */
    suspend fun grabFullVirtualScreen(): CaptureResult

    /** Grabs [region] of the virtual screen as it looks now. */
    suspend fun grabRegion(region: CaptureRegion): CaptureResult

    /** Geometry of the focused top-level window in virtual-screen pixels, or `null`. */
    suspend fun activeWindowRegion(): CaptureRegion?

    /** Size of the whole virtual screen in physical pixels, without grabbing it. */
    suspend fun virtualScreenSize(): ScreenSize
}

/** Fallback for sessions with no usable backend: boots fine, throws on capture. */
class UnsupportedScreenCaptureService(
    private val failure: CaptureFailure,
    private val details: String? = null,
) : ScreenCaptureService {
    private fun unsupported(): Nothing = throw CaptureException(failure, details)

    override suspend fun grabFullVirtualScreen(): CaptureResult = unsupported()
    override suspend fun grabRegion(region: CaptureRegion): CaptureResult = unsupported()
    override suspend fun activeWindowRegion(): CaptureRegion? = unsupported()
    override suspend fun virtualScreenSize(): ScreenSize = unsupported()
}
