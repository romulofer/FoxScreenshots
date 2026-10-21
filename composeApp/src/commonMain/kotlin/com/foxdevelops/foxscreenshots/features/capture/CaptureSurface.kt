package com.foxdevelops.foxscreenshots.features.capture

import androidx.compose.ui.graphics.ImageBitmap
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlin.time.Duration

/** The frozen frame the selection overlay is drawn over. */
class FrozenFrame(val image: ImageBitmap, val width: Int, val height: Int)

/**
 * The windows the capture flows need, behind an interface so the flows stay
 * unit-testable: real windows need a desktop.
 */
interface CaptureSurface {
    /** Hides the hub window so it does not end up in the screenshot, and waits for the compositor to repaint. */
    suspend fun hideHub()

    /** Brings the hub window back after a capture, if it was visible before. */
    suspend fun restoreHub()

    /**
     * Shows the fullscreen selection overlay over [frame] and resolves with the
     * region dragged (screenshot pixels), or `null` if the user cancelled.
     */
    suspend fun selectRegion(frame: FrozenFrame): CaptureRegion?

    /**
     * Shows a countdown badge for [wait] and removes it again before returning.
     * Resolves `true` when the time ran out, `false` when the user cancelled.
     */
    suspend fun countdown(wait: Duration): Boolean
}
