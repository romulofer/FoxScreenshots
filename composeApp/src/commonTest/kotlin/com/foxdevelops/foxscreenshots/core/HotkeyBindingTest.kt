package com.foxdevelops.foxscreenshots.core

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyModifier
import com.foxdevelops.foxscreenshots.core.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
