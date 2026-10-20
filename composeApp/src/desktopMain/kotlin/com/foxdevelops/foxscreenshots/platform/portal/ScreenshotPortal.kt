package com.foxdevelops.foxscreenshots.platform.portal

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Why a portal request could not be served. */
enum class PortalFailure {
    /** The user dismissed the desktop's permission dialog, or the compositor refused the request. */
    Denied,

    /** No portal is running, or it failed mid-request. */
    Unavailable,
}

/** A portal request that did not produce a screenshot. [details] is developer-facing and never carries screen content. */
class PortalException(val failure: PortalFailure, val details: String? = null) :
    Exception(if (details == null) failure.name else "${failure.name}: $details")

/**
 * Screen capture through `xdg-desktop-portal` — the only way to read the screen
 * under Wayland, where no client may grab another's pixels. Behind an interface
 * so the capture backend can be unit-tested without a session bus.
 */
interface ScreenshotPortal {
    /** Asks the desktop for a screenshot of everything and returns the PNG bytes. */
    suspend fun capture(): ByteArray
}

/** How long to wait for the user to answer the desktop's modal dialog before giving up. */
val DEFAULT_PORTAL_TIMEOUT: Duration = 2.minutes
