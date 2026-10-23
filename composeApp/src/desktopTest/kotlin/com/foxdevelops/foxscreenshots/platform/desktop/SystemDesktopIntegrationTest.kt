package com.foxdevelops.foxscreenshots.platform.desktop

import com.foxdevelops.foxscreenshots.core.desktop.DesktopEvents
import com.foxdevelops.foxscreenshots.core.desktop.SystemDependency
import com.foxdevelops.foxscreenshots.core.desktop.SystemIssues
import com.foxdevelops.foxscreenshots.core.desktop.TrayAction
import com.foxdevelops.foxscreenshots.core.hotkey.GlobalHotkeys
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyModifier
import com.foxdevelops.foxscreenshots.core.i18n.StringsEn
import com.foxdevelops.foxscreenshots.core.i18n.StringsPt
import com.foxdevelops.foxscreenshots.core.settings.Settings
import com.foxdevelops.foxscreenshots.platform.hotkey.HotkeyUnavailableException
import com.foxdevelops.foxscreenshots.platform.tray.TrayPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemDesktopIntegrationTest {
    private class FakeTray(var supported: Boolean = true) : TrayPresenter {
        var tooltip: String? = null
        var labels: Map<TrayAction, String> = emptyMap()
        var open: () -> Unit = {}
        var onAction: (TrayAction) -> Unit = {}
        var disposed = false
        override fun show(tooltip: String, labels: Map<TrayAction, String>, onOpenWindow: () -> Unit, onAction: (TrayAction) -> Unit): Boolean {
            if (!supported) return false
            this.tooltip = tooltip
            this.labels = labels
            open = onOpenWindow
            this.onAction = onAction
            return true
        }

        override fun dispose() {
            disposed = true
        }
    }

    private class FakeHotkeys(var failing: Boolean = false) : GlobalHotkeys {
        var bindings: Map<HotkeyAction, HotkeyBinding> = emptyMap()
        var trigger: (HotkeyAction) -> Unit = {}
        var closed = false
        override fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit) {
            if (failing) throw HotkeyUnavailableException("no hook")
            this.bindings = bindings
            trigger = onTriggered
        }

        override fun close() {
            closed = true
        }

        var status: (Boolean, String?) -> Unit = { _, _ -> }

        override fun onStatus(listener: (working: Boolean, detail: String?) -> Unit) {
            status = listener
        }
    }

    private class RecordingEvents : DesktopEvents {
        val log = mutableListOf<String>()
        override fun onOpenWindow() { log += "open" }
        override fun onTrayAction(action: TrayAction) { log += "tray:$action" }
        override fun onHotkey(action: HotkeyAction) { log += "hotkey:$action" }
    }

    private val tray = FakeTray()
    private val hotkeys = FakeHotkeys()
    private val issues = SystemIssues()
    private val events = RecordingEvents()

    private fun integration() =
        SystemDesktopIntegration(tray, hotkeys, issues)

    @Test
    fun `the tray gets localized labels and routes clicks and menu items`() {
        integration().attach(StringsEn, Settings(), events)
        assertEquals("FoxScreenShots", tray.tooltip)
        assertEquals("Quit", tray.labels.getValue(TrayAction.Quit))
        assertEquals(TrayAction.entries.toSet(), tray.labels.keys)
        tray.open()
        tray.onAction(TrayAction.Timer)
        assertEquals(listOf("open", "tray:Timer"), events.log)
    }

    @Test
    fun `attaching again after a language change relabels the tray`() {
        val integration = integration()
        integration.attach(StringsEn, Settings(), events)
        integration.attach(StringsPt, Settings(), events)
        assertEquals("Sair", tray.labels.getValue(TrayAction.Quit))
    }

    @Test
    fun `hotkeys come from the settings, with their modifiers`() {
        integration().attach(StringsEn, Settings(hotkey = "F9", timerHotkey = "Ctrl+Shift+S", repeatHotkey = "F8"), events)
        assertEquals(HotkeyBinding(HotkeyKey.F9), hotkeys.bindings[HotkeyAction.Instant])
        assertEquals(
            HotkeyBinding(HotkeyKey.S, setOf(HotkeyModifier.Control, HotkeyModifier.Shift)),
            hotkeys.bindings[HotkeyAction.Timer],
        )
        assertEquals(HotkeyBinding(HotkeyKey.F8), hotkeys.bindings[HotkeyAction.Repeat])
        hotkeys.trigger(HotkeyAction.Instant)
        assertEquals(listOf("hotkey:Instant"), events.log)
    }

    @Test
    fun `a corrupt hotkey setting falls back to the default key of its action`() {
        integration().attach(StringsEn, Settings(hotkey = "???", timerHotkey = "???", repeatHotkey = "???"), events)
        assertEquals(HotkeyKey.PrintScreen, hotkeys.bindings.getValue(HotkeyAction.Instant).key)
        assertEquals(HotkeyKey.F7, hotkeys.bindings.getValue(HotkeyAction.Timer).key)
        assertEquals(HotkeyKey.F6, hotkeys.bindings.getValue(HotkeyAction.Repeat).key)
    }

    @Test
    fun `a keyboard hook that cannot be installed is reported, not thrown`() {
        hotkeys.failing = true
        integration().attach(StringsEn, Settings(), events)
        assertEquals(listOf(SystemDependency.Hotkeys), issues.state.value.map { it.dependency })
    }

    @Test
    fun `the hotkey issue clears once the hook installs`() {
        hotkeys.failing = true
        val integration = integration()
        integration.attach(StringsEn, Settings(), events)
        hotkeys.failing = false
        integration.attach(StringsEn, Settings(), events)
        assertTrue(issues.state.value.isEmpty())
    }

    @Test
    fun `a backend that learns later that the desktop refused reports it then`() {
        integration().attach(StringsEn, Settings(), events)
        assertTrue(issues.state.value.isEmpty())
        hotkeys.status(false, "the desktop refused")
        assertEquals(listOf(SystemDependency.Hotkeys), issues.state.value.map { it.dependency })
        hotkeys.status(true, null)
        assertTrue(issues.state.value.isEmpty())
    }

    @Test
    fun `a desktop without a tray is reported`() {
        tray.supported = false
        integration().attach(StringsEn, Settings(), events)
        assertEquals(listOf(SystemDependency.Tray), issues.state.value.map { it.dependency })
    }

    @Test
    fun `close takes the tray and the hotkeys down`() {
        val integration = integration()
        integration.attach(StringsEn, Settings(), events)
        integration.close()
        assertTrue(tray.disposed)
        assertTrue(hotkeys.closed)
    }
}
