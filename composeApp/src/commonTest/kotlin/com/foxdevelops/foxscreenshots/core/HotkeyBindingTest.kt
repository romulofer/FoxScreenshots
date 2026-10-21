package com.foxdevelops.foxscreenshots.core

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyModifier
import com.foxdevelops.foxscreenshots.core.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import com.foxdevelops.foxscreenshots.core.hotkey.toHotkeyKey
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotkeyBindingTest {
    @Test
    fun `parses a bare key`() {
        assertEquals(HotkeyBinding(HotkeyKey.PrintScreen), HotkeyBinding.parse("PrintScreen"))
        assertEquals(HotkeyBinding(HotkeyKey.F9), HotkeyBinding.parse("F9"))
    }

    @Test
    fun `parses modifiers in any case`() {
        val binding = HotkeyBinding.parse("ctrl+Shift+s")
        assertEquals(HotkeyKey.S, binding.key)
        assertEquals(setOf(HotkeyModifier.Control, HotkeyModifier.Shift), binding.modifiers)
    }

    @Test
    fun `accepts platform spellings of the modifiers`() {
        assertEquals(setOf(HotkeyModifier.Meta), HotkeyBinding.parse("Cmd+A").modifiers)
        assertEquals(setOf(HotkeyModifier.Meta), HotkeyBinding.parse("Win+A").modifiers)
        assertEquals(setOf(HotkeyModifier.Alt), HotkeyBinding.parse("Option+A").modifiers)
    }

    @Test
    fun `unknown or empty labels fall back to PrintScreen`() {
        assertEquals(HotkeyBinding(HotkeyKey.PrintScreen), HotkeyBinding.parse(""))
        assertEquals(
            HotkeyBinding(HotkeyKey.PrintScreen, setOf(HotkeyModifier.Control)),
            HotkeyBinding.parse("Ctrl+???"),
        )
    }

    @Test
    fun `every option offered in settings parses to a distinct key press`() {
        val parsed = Settings.HOTKEY_OPTIONS.map { HotkeyBinding.parse(it) }
        assertEquals(Settings.HOTKEY_OPTIONS.size, parsed.toSet().size)
    }

    @Test
    fun `labels round trip through parse`() {
        for (label in listOf("PrintScreen", "F9", "Ctrl+Shift+S", "Ctrl+Alt+Meta+K")) {
            assertEquals(label, HotkeyBinding.parse(label).toLabel())
        }
        assertEquals("Ctrl+Shift+S", HotkeyBinding.parse("shift+ctrl+s").toLabel())
    }

    @Test
    fun `escape parses by either name`() {
        assertEquals(HotkeyBinding(HotkeyKey.Escape), HotkeyBinding.parse("Esc"))
        assertEquals(HotkeyBinding(HotkeyKey.Escape), HotkeyBinding.parse("Escape"))
    }

    @Test
    fun `only safe bindings can be global capture hotkeys`() {
        assertTrue(HotkeyBinding.parse("PrintScreen").isAllowedAsCaptureHotkey)
        assertTrue(HotkeyBinding.parse("F8").isAllowedAsCaptureHotkey)
        assertTrue(HotkeyBinding.parse("Ctrl+S").isAllowedAsCaptureHotkey)
        assertFalse(HotkeyBinding.parse("S").isAllowedAsCaptureHotkey)
        assertFalse(HotkeyBinding.parse("Shift+S").isAllowedAsCaptureHotkey)
        assertFalse(HotkeyBinding.parse("Escape").isAllowedAsCaptureHotkey)
    }

    @Test
    fun `compose keys map to hotkey keys`() {
        assertEquals(HotkeyKey.F5, androidx.compose.ui.input.key.Key.F5.toHotkeyKey())
        assertEquals(HotkeyKey.Q, androidx.compose.ui.input.key.Key.Q.toHotkeyKey())
        assertEquals(HotkeyKey.PrintScreen, androidx.compose.ui.input.key.Key.PrintScreen.toHotkeyKey())
        assertNull(androidx.compose.ui.input.key.Key.CtrlLeft.toHotkeyKey())
        assertNull(androidx.compose.ui.input.key.Key.DirectionUp.toHotkeyKey())
    }
}
