package com.foxdevelops.foxscreenshots.core.hotkey

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

private val composeKeys: Map<Key, HotkeyKey> = buildMap {
    put(Key.PrintScreen, HotkeyKey.PrintScreen)
    put(Key.Escape, HotkeyKey.Escape)
    put(Key.F1, HotkeyKey.F1); put(Key.F2, HotkeyKey.F2); put(Key.F3, HotkeyKey.F3); put(Key.F4, HotkeyKey.F4)
    put(Key.F5, HotkeyKey.F5); put(Key.F6, HotkeyKey.F6); put(Key.F7, HotkeyKey.F7); put(Key.F8, HotkeyKey.F8)
    put(Key.F9, HotkeyKey.F9); put(Key.F10, HotkeyKey.F10); put(Key.F11, HotkeyKey.F11); put(Key.F12, HotkeyKey.F12)
    put(Key.A, HotkeyKey.A); put(Key.B, HotkeyKey.B); put(Key.C, HotkeyKey.C); put(Key.D, HotkeyKey.D)
    put(Key.E, HotkeyKey.E); put(Key.F, HotkeyKey.F); put(Key.G, HotkeyKey.G); put(Key.H, HotkeyKey.H)
    put(Key.I, HotkeyKey.I); put(Key.J, HotkeyKey.J); put(Key.K, HotkeyKey.K); put(Key.L, HotkeyKey.L)
    put(Key.M, HotkeyKey.M); put(Key.N, HotkeyKey.N); put(Key.O, HotkeyKey.O); put(Key.P, HotkeyKey.P)
    put(Key.Q, HotkeyKey.Q); put(Key.R, HotkeyKey.R); put(Key.S, HotkeyKey.S); put(Key.T, HotkeyKey.T)
    put(Key.U, HotkeyKey.U); put(Key.V, HotkeyKey.V); put(Key.W, HotkeyKey.W); put(Key.X, HotkeyKey.X)
    put(Key.Y, HotkeyKey.Y); put(Key.Z, HotkeyKey.Z)
}

/** The key a pressed [Key] stands for as a hotkey, or `null` for keys that cannot be one (modifiers, arrows, digits…). */
fun Key.toHotkeyKey(): HotkeyKey? = composeKeys[this]

/**
 * The key press in [event] as a binding, for the "record a hotkey" dialog.
 * `null` while only modifiers are held down, or for a key a hotkey cannot use.
 */
fun KeyEvent.toHotkeyBinding(): HotkeyBinding? {
    val main = key.toHotkeyKey() ?: return null
    val modifiers = buildSet {
        if (isCtrlPressed) add(HotkeyModifier.Control)
        if (isAltPressed) add(HotkeyModifier.Alt)
        if (isShiftPressed) add(HotkeyModifier.Shift)
        if (isMetaPressed) add(HotkeyModifier.Meta)
    }
    return HotkeyBinding(main, modifiers)
}
