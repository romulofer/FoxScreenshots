package com.foxdevelops.foxscreenshots.core.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A system component the app needs at runtime. */
enum class SystemDependency {
    /** `libX11.so.6` — the Linux capture backend talks to it directly. */
    X11Library,

    /** A reachable X display (`DISPLAY`, and a server that accepts the connection). */
    XDisplay,

    /** The session is Wayland: capture goes through xdg-desktop-portal, which asks for permission and cannot frame another window. */
    WaylandSession,

    /** The system keyboard hook behind the global hotkeys could not be installed. */
    Hotkeys,

    /** The system tray is not available. */
    Tray,
}

/** How badly a missing dependency hurts. */
enum class DependencySeverity {
    /** Capture cannot work at all. */
    Blocking,

    /** The app runs, but one feature is degraded. */
    Degraded,
}

/** One unmet requirement, ready to be shown to the user. */
data class DependencyIssue(val dependency: SystemDependency, val severity: DependencySeverity)

/** Checks the display server and libraries capture relies on, before the user hits a dead button. */
interface DependencyChecker {
    /** Unmet requirements, most severe first. Empty means everything is in place. */
    suspend fun check(): List<DependencyIssue>
}

/** Reports nothing missing; used on platforms with nothing to probe, and in tests. */
object NoDependencyChecker : DependencyChecker {
    override suspend fun check(): List<DependencyIssue> = emptyList()
}

/**
 * The issues the home screen warns about: the static ones from a
 * [DependencyChecker], plus failures that only show up at runtime (the keyboard
 * hook refusing to install, no tray).
 */
class SystemIssues {
    private val mutableState = MutableStateFlow<List<DependencyIssue>>(emptyList())
    val state: StateFlow<List<DependencyIssue>> = mutableState.asStateFlow()

    fun report(issue: DependencyIssue) = mutableState.update { current ->
        (current.filterNot { it.dependency == issue.dependency } + issue).sortedBy { it.severity }
    }

    fun clear(dependency: SystemDependency) = mutableState.update { current -> current.filterNot { it.dependency == dependency } }

    fun reportAll(issues: List<DependencyIssue>) = issues.forEach(::report)
}
