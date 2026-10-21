package com.foxdevelops.foxscreenshots.core

import com.foxdevelops.foxscreenshots.core.settings.LanguagePreference
import com.foxdevelops.foxscreenshots.core.settings.MemorySettingsStore
import com.foxdevelops.foxscreenshots.core.settings.Settings
import com.foxdevelops.foxscreenshots.core.settings.SettingsController
import com.foxdevelops.foxscreenshots.core.settings.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.settings.HotkeyRejection
import kotlin.test.assertNull

class SettingsControllerTest {
    @Test
    fun `starts with the defaults`() {
        val state = SettingsController(MemorySettingsStore()).state.value
        assertEquals(ThemePreference.System, state.theme)
        assertEquals(LanguagePreference.System, state.language)
        assertEquals(3, state.timerDelaySeconds)
        assertEquals("PrintScreen", state.hotkey)
        assertEquals("F7", state.timerHotkey)
        assertEquals("F6", state.repeatHotkey)
        assertFalse(state.autoSave)
    }

    @Test
    fun `changes persist and survive a reload`() {
        val store = MemorySettingsStore()
        SettingsController(store).apply {
            setTheme(ThemePreference.Dark)
            setLanguage(LanguagePreference.English)
            setTimerDelaySeconds(9)
            setHotkey("F9")
            setOutputDir("/shots")
            setAutoSave(true)
        }
        val reloaded = SettingsController(store).state.value
        assertEquals(ThemePreference.Dark, reloaded.theme)
        assertEquals(LanguagePreference.English, reloaded.language)
        assertEquals(9, reloaded.timerDelaySeconds)
        assertEquals("F9", reloaded.hotkey)
        assertEquals("/shots", reloaded.outputDir)
        assertTrue(reloaded.autoSave)
    }

    @Test
    fun `timer delay is clamped`() {
        val controller = SettingsController(MemorySettingsStore())
        controller.setTimerDelaySeconds(0)
        assertEquals(Settings.MIN_TIMER_DELAY_SECONDS, controller.state.value.timerDelaySeconds)
        controller.setTimerDelaySeconds(999)
        assertEquals(Settings.MAX_TIMER_DELAY_SECONDS, controller.state.value.timerDelaySeconds)
    }

    @Test
    fun `a corrupt stored delay falls back to the default`() {
        val store = MemorySettingsStore(mapOf("timer_delay_seconds" to "abc"))
        assertEquals(3, SettingsController(store).state.value.timerDelaySeconds)
    }

    @Test
    fun `auto-save stays off while there is no output folder`() {
        val controller = SettingsController(MemorySettingsStore())
        controller.setAutoSave(true)
        assertFalse(controller.state.value.autoSave)
        controller.setOutputDir("/shots")
        controller.setAutoSave(true)
        assertTrue(controller.state.value.autoSave)
    }

    @Test
    fun `file name pattern defaults, persists, and a blank one resets`() {
        val store = MemorySettingsStore()
        val controller = SettingsController(store)
        assertEquals("foxshot_{date}_{time}", controller.state.value.fileNamePattern)
        controller.setFileNamePattern("  tela-{date}  ")
        assertEquals("tela-{date}", SettingsController(store).state.value.fileNamePattern)
        controller.setFileNamePattern("   ")
        assertEquals("foxshot_{date}_{time}", controller.state.value.fileNamePattern)
    }

    @Test
    fun `file name pattern is length limited`() {
        val controller = SettingsController(MemorySettingsStore())
        controller.setFileNamePattern("x".repeat(500))
        assertEquals(Settings.MAX_FILE_NAME_PATTERN_LENGTH, controller.state.value.fileNamePattern.length)
    }

    @Test
    fun `last save folder is remembered`() {
        val store = MemorySettingsStore()
        SettingsController(store).setLastSaveDir("/home/me/shots")
        assertEquals("/home/me/shots", SettingsController(store).state.value.lastSaveDir)
    }

    @Test
    fun `selection confirm defaults on and persists`() {
        val store = MemorySettingsStore()
        assertTrue(SettingsController(store).state.value.selectionConfirm)
        SettingsController(store).setSelectionConfirm(false)
        assertFalse(SettingsController(store).state.value.selectionConfirm)
    }

    @Test
    fun `dismissed issues accumulate and survive a reload`() {
        val store = MemorySettingsStore()
        val controller = SettingsController(store)
        controller.dismissIssues(setOf("Tray"))
        controller.dismissIssues(setOf("Hotkeys"))
        assertEquals(setOf("Tray", "Hotkeys"), SettingsController(store).state.value.dismissedIssues)
    }

    @Test
    fun `binding a hotkey stores it`() {
        val controller = SettingsController(MemorySettingsStore())
        assertNull(controller.bindHotkey(HotkeyAction.Timer, "Ctrl+Shift+T"))
        assertEquals("Ctrl+Shift+T", controller.state.value.timerHotkey)
    }

    @Test
    fun `a hotkey another action already uses is refused and nothing changes`() {
        val controller = SettingsController(MemorySettingsStore())
        val rejection = controller.bindHotkey(HotkeyAction.Timer, controller.state.value.hotkey)
        assertEquals(HotkeyRejection.InUse(HotkeyAction.Instant), rejection)
        assertEquals(Settings.DEFAULT_TIMER_HOTKEY, controller.state.value.timerHotkey)
    }

    @Test
    fun `the same binding spelled differently still clashes`() {
        val controller = SettingsController(MemorySettingsStore())
        controller.setRepeatHotkey("Ctrl+Shift+S")
        assertEquals(HotkeyRejection.InUse(HotkeyAction.Repeat), controller.bindHotkey(HotkeyAction.Instant, "shift+ctrl+s"))
    }

    @Test
    fun `rebinding an action to its own key is fine`() {
        val controller = SettingsController(MemorySettingsStore())
        assertNull(controller.bindHotkey(HotkeyAction.Instant, "PrintScreen"))
    }

    @Test
    fun `a bare letter or Escape cannot be a global hotkey`() {
        val controller = SettingsController(MemorySettingsStore())
        assertEquals(HotkeyRejection.NotAllowed, controller.bindHotkey(HotkeyAction.Instant, "S"))
        assertEquals(HotkeyRejection.NotAllowed, controller.bindHotkey(HotkeyAction.Instant, "Shift+S"))
        assertEquals(HotkeyRejection.NotAllowed, controller.bindHotkey(HotkeyAction.Instant, "Escape"))
        assertEquals("PrintScreen", controller.state.value.hotkey)
    }
}
