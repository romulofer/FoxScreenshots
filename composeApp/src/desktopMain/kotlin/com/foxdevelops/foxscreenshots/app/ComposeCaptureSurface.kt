package com.foxdevelops.foxscreenshots.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foxdevelops.foxscreenshots.features.capture.CaptureSurface
import com.foxdevelops.foxscreenshots.features.capture.FrozenFrame
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A selection overlay waiting for the user, and the slot its answer goes into. */
class OverlayRequest(val frame: FrozenFrame, val answer: CompletableDeferred<CaptureRegion?>)

/**
 * The capture flows' windows, as Compose state the application renders.
 *
 * The selection overlay and the countdown badge are windows of their own, never
 * the hub repurposed: a failure between "enter" and "leave" of a repurposed hub
 * strands it click-through and pinned on top of the user's desktop.
 */
class ComposeCaptureSurface(
    private val repaintDelay: Duration = 220.milliseconds,
    private val compositorDelay: Duration = 120.milliseconds,
) : CaptureSurface {

    /** Whether the hub window is on screen. */
    var hubVisible by mutableStateOf(true)
        private set

    /** The overlay to show, or `null`. */
    var overlay by mutableStateOf<OverlayRequest?>(null)
        private set

    /** Seconds left on the countdown badge, or `null` when there is none. */
    var countdown by mutableStateOf<Int?>(null)
        private set

    private var hubWasVisible = true

    fun showHub() {
        hubVisible = true
    }

    /** Closing the window keeps the app running in the tray. */
    fun hideToTray() {
        hubVisible = false
    }

    override suspend fun hideHub() {
        hubWasVisible = hubVisible
        if (hubWasVisible) {
            hubVisible = false
            // Too short and the disappearing window is still in the frame.
            delay(repaintDelay)
        }
    }

    override suspend fun restoreHub() {
        if (hubWasVisible) hubVisible = true
    }

    override suspend fun selectRegion(frame: FrozenFrame): CaptureRegion? {
        val request = OverlayRequest(frame, CompletableDeferred())
        overlay = request
        try {
            return request.answer.await()
        } finally {
            // Gone before anything else happens, so it can never be part of the shot that follows.
            overlay = null
        }
    }

    override suspend fun countdown(wait: Duration) {
        val seconds = wait.inWholeSeconds.toInt()
        if (seconds < 1) {
            delay(wait)
            return
        }
        try {
            for (s in seconds downTo 1) {
                countdown = s
                delay(1.seconds)
            }
            val remainder = wait - seconds.seconds
            if (remainder > Duration.ZERO) delay(remainder)
        } finally {
            countdown = null
            // Let the compositor drop the badge before the frame is grabbed.
            delay(compositorDelay)
        }
    }
}
