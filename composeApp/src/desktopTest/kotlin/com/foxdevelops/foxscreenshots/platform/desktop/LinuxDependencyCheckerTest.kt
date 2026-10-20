package com.foxdevelops.foxscreenshots.platform.desktop

import com.foxdevelops.foxscreenshots.core.desktop.DependencyIssue
import com.foxdevelops.foxscreenshots.core.desktop.DependencySeverity
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.platform.session.DesktopSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxDependencyCheckerTest {
    private fun checker(session: DesktopSession, xlib: Boolean = true, display: Boolean = true) =
        LinuxDependencyChecker(session, { xlib }, { display })

    @Test
    fun `a healthy x11 session has nothing to report`() = runTest {
        assertTrue(checker(DesktopSession.X11).check().isEmpty())
    }

    @Test
    fun `wayland is degraded, not blocking`() = runTest {
        assertEquals(
            listOf(DependencyIssue(SystemDependency.WaylandSession, DependencySeverity.Degraded)),
            checker(DesktopSession.Wayland, xlib = false).check(),
        )
    }

    @Test
    fun `a missing xlib blocks capture`() = runTest {
        assertEquals(
            listOf(DependencyIssue(SystemDependency.X11Library, DependencySeverity.Blocking)),
            checker(DesktopSession.X11, xlib = false).check(),
        )
    }

    @Test
    fun `an unreachable display blocks capture, but only once xlib loaded`() = runTest {
        assertEquals(
            listOf(DependencyIssue(SystemDependency.XDisplay, DependencySeverity.Blocking)),
            checker(DesktopSession.X11, display = false).check(),
        )
        assertEquals(
            listOf(SystemDependency.X11Library),
            checker(DesktopSession.X11, xlib = false, display = false).check().map { it.dependency },
        )
    }

    @Test
    fun `no display server at all reads as an unreachable display`() = runTest {
        assertEquals(
            listOf(SystemDependency.XDisplay),
            checker(DesktopSession.Other, display = false).check().map { it.dependency },
        )
    }
}
