package com.foxdevelops.foxscreenshots.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foxdevelops.foxscreenshots.features.capture.CaptureSurface
import com.foxdevelops.foxscreenshots.features.capture.FrozenFrame
import com.foxdevelops.foxscreenshots.model.CaptureRegion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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

    /** The toast to show, or `null`. A new request is a new object, so the same text twice still restarts its timer. */
    var toast by mutableStateOf<ToastRequest?>(null)
        private set

    private var countdownCancel: CompletableDeferred<Unit>? = null
    private var hubWasVisible = true

    /** A short message in a window of its own, for when the hub is hidden and a snackbar would have nowhere to appear. */
    fun showToast(text: String) {
        toast = ToastRequest(text)
    }

    fun clearToast() {
        toast = null
    }

    /** Aborts the countdown in progress (badge click, Esc), if any. */
    fun cancelCountdown() {
        countdownCancel?.complete(Unit)
    }

    fun showHub() {
        hubVisible = true
    }

    /** Closing the window keeps the app running in the tray. */
    fun hideToTray() {
        hubVisible = false
    }

    override suspend fun hideHub() {
        // A leftover toast must not end up in the shot that is about to be taken.
        toast = null
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

    override suspend fun countdown(wait: Duration): Boolean {
        val cancel = CompletableDeferred<Unit>()
        countdownCancel = cancel
        // Waiting is "sleep until the time is up, unless cancelled first": a non-null result means cancelled.
        suspend fun cancelledWithin(time: Duration) = withTimeoutOrNull(time) { cancel.await() } != null
        try {
            val seconds = wait.inWholeSeconds.toInt()
            if (seconds < 1) return !cancelledWithin(wait)
            for (s in seconds downTo 1) {
                countdown = s
                if (cancelledWithin(1.seconds)) return false
            }
            val remainder = wait - seconds.seconds
            return remainder <= Duration.ZERO || !cancelledWithin(remainder)
        } finally {
            countdownCancel = null
            countdown = null
            // Let the compositor drop the badge before the frame is grabbed.
            delay(compositorDelay)
        }
    }
}

/** One toast on screen. */
class ToastRequest(val text: String)
