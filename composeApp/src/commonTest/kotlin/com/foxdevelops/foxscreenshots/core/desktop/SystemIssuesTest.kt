package com.foxdevelops.foxscreenshots.core.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemIssuesTest {
    private val hotkeys = DependencyIssue(SystemDependency.Hotkeys, DependencySeverity.Degraded)
    private val display = DependencyIssue(SystemDependency.XDisplay, DependencySeverity.Blocking)

    @Test
    fun `blocking issues sort before degraded ones`() {
        val issues = SystemIssues()
        issues.report(hotkeys)
        issues.report(display)
        assertEquals(listOf(display, hotkeys), issues.state.value)
    }

    @Test
    fun `reporting the same dependency twice keeps one`() {
        val issues = SystemIssues()
        issues.report(hotkeys)
        issues.report(hotkeys)
        assertEquals(listOf(hotkeys), issues.state.value)
    }

    @Test
    fun `clear removes only that dependency`() {
        val issues = SystemIssues()
        issues.reportAll(listOf(hotkeys, display))
        issues.clear(SystemDependency.Hotkeys)
        assertEquals(listOf(display), issues.state.value)
        issues.clear(SystemDependency.XDisplay)
        assertTrue(issues.state.value.isEmpty())
    }
}
