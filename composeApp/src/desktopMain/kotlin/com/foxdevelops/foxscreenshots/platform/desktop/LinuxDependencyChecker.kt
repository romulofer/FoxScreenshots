package com.foxdevelops.foxscreenshots.platform.desktop

import com.foxdevelops.foxscreenshots.core.desktop.DependencyChecker
import com.foxdevelops.foxscreenshots.core.desktop.DependencyIssue
import com.foxdevelops.foxscreenshots.core.desktop.DependencySeverity
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.platform.session.DesktopSession
import com.foxdevelops.foxscreenshots.platform.x11.Xlib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Linux: checks that the display server the capture backend needs is there, so
 * a user on a bare distro gets an explanation instead of a dead button. The
 * probes are injectable so the logic runs on any machine.
 */
class LinuxDependencyChecker internal constructor(
    private val session: DesktopSession,
    private val canLoadXlib: () -> Boolean,
    private val canOpenDisplay: () -> Boolean,
) : DependencyChecker {

    constructor(session: DesktopSession = DesktopSession.detect()) : this(session, ::probeXlib, ::probeDisplay)

    override suspend fun check(): List<DependencyIssue> = withContext(Dispatchers.IO) {
        buildList {
            when {
                // Capture still works, through the portal — with a permission prompt, without window framing.
                session == DesktopSession.Wayland ->
                    add(DependencyIssue(SystemDependency.WaylandSession, DependencySeverity.Degraded))
                !canLoadXlib() -> add(DependencyIssue(SystemDependency.X11Library, DependencySeverity.Blocking))
                // Only worth reporting once the library itself loaded.
                !canOpenDisplay() -> add(DependencyIssue(SystemDependency.XDisplay, DependencySeverity.Blocking))
            }
        }
    }

    private companion object {
        fun probeXlib(): Boolean = runCatching { Xlib.load() }.isSuccess

        /** Opens and immediately closes an X connection, to prove the server is reachable. */
        fun probeDisplay(): Boolean = runCatching {
            val x = Xlib.load()
            val display = x.XOpenDisplay(null) ?: return@runCatching false
            x.XCloseDisplay(display)
            true
        }.getOrDefault(false)
    }
}
