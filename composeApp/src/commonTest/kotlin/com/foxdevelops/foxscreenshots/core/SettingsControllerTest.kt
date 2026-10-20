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
}
