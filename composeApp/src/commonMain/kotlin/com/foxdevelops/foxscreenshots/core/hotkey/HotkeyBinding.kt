package com.foxdevelops.foxscreenshots.core.hotkey

enum class HotkeyModifier { Control, Alt, Shift, Meta }

/** Keys a global hotkey can be bound to. */
enum class HotkeyKey {
    PrintScreen,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
}

/** A parsed hotkey label such as `PrintScreen` or `Ctrl+Shift+S`. */
data class HotkeyBinding(val key: HotkeyKey, val modifiers: Set<HotkeyModifier> = emptySet()) {
    companion object {
        /**
         * Parses a persisted label. Unknown keys fall back to [fallback] so a
         * corrupt preference cannot leave the app without a capture hotkey.
         */
        fun parse(label: String, fallback: HotkeyKey = HotkeyKey.PrintScreen): HotkeyBinding {
            val modifiers = mutableSetOf<HotkeyModifier>()
            var key: HotkeyKey? = null
            for (part in label.split('+').map { it.trim() }.filter { it.isNotEmpty() }) {
                when (part.lowercase()) {
                    "ctrl", "control" -> modifiers += HotkeyModifier.Control
                    "alt", "option" -> modifiers += HotkeyModifier.Alt
                    "shift" -> modifiers += HotkeyModifier.Shift
                    "meta", "cmd", "command", "super", "win" -> modifiers += HotkeyModifier.Meta
                    else -> key = keyFromToken(part) ?: key
                }
            }
            return HotkeyBinding(key ?: fallback, modifiers)
        }

        private fun keyFromToken(token: String): HotkeyKey? {
            val normalized = token.lowercase().replace(" ", "")
            return when (normalized) {
                "printscreen", "prtsc", "prntscrn", "snapshot" -> HotkeyKey.PrintScreen
                else -> HotkeyKey.entries.firstOrNull { it.name.lowercase() == normalized && it != HotkeyKey.PrintScreen }
            }
        }
    }
}

/** What a global hotkey triggers. */
enum class HotkeyAction { Instant, Timer, Repeat }

/** Registers system-wide hotkeys. Implemented per platform; absent on Wayland. */
interface GlobalHotkeys {
    /** Replaces every registration; [bindings] map each action to its parsed binding. */
    fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit)

    /** Releases native hooks. */
    fun close()
}

/** Does nothing; used where global hotkeys cannot work, and in tests. */
object NoGlobalHotkeys : GlobalHotkeys {
    override fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit) = Unit
    override fun close() = Unit
}
