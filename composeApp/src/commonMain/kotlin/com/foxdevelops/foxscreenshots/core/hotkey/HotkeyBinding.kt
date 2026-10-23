package com.foxdevelops.foxscreenshots.core.hotkey

enum class HotkeyModifier(val label: String) {
    Control("Ctrl"),
    Alt("Alt"),
    Shift("Shift"),
    Meta("Meta"),
}

/** Keys a global hotkey can be bound to. */
enum class HotkeyKey {
    PrintScreen,
    Escape,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
}

/** Whether the key is a letter (as opposed to PrintScreen, Escape or a function key). */
val HotkeyKey.isLetter: Boolean get() = name.length == 1

/** A parsed hotkey label such as `PrintScreen` or `Ctrl+Shift+S`. */
data class HotkeyBinding(val key: HotkeyKey, val modifiers: Set<HotkeyModifier> = emptySet()) {

    /** The persisted label, e.g. `Ctrl+Shift+S`; [parse] reads it back to the same binding. */
    fun toLabel(): String = buildList {
        for (modifier in HotkeyModifier.entries) if (modifier in modifiers) add(modifier.label)
        add(key.name)
    }.joinToString("+")

    /**
     * Whether this can be a *global* capture hotkey. The hook watches every key
     * system-wide, so a bare letter would fire while typing and Escape belongs to
     * every dialog: letters need Ctrl, Alt or Meta, and Escape is reserved.
     */
    val isAllowedAsCaptureHotkey: Boolean
        get() = when {
            key == HotkeyKey.Escape -> false
            key.isLetter -> modifiers.any { it != HotkeyModifier.Shift }
            else -> true
        }

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
                "esc", "escape" -> HotkeyKey.Escape
                else -> HotkeyKey.entries.firstOrNull { it.name.lowercase() == normalized && it != HotkeyKey.PrintScreen }
            }
        }
    }
}

/** What a global hotkey triggers. [Cancel] is fixed to Escape and only means something while a timer is counting down. */
enum class HotkeyAction { Instant, Timer, Repeat, Cancel }

/** Registers system-wide hotkeys. Implemented per platform; absent on Wayland. */
interface GlobalHotkeys {
    /** Replaces every registration; [bindings] map each action to its parsed binding. */
    fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit)

    /** Releases native hooks. */
    fun close()

    /**
     * Reports, from any thread, whether the hotkeys really work. Backends that can only find out later
     * (the Wayland portal answers asynchronously, and the compositor may refuse) call it when they know;
     * the others fail or succeed inside [register] and need not. [detail] is developer-facing.
     */
    fun onStatus(listener: (working: Boolean, detail: String?) -> Unit) = Unit
}

/** Does nothing; used where global hotkeys cannot work, and in tests. */
object NoGlobalHotkeys : GlobalHotkeys {
    override fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit) = Unit
    override fun close() = Unit
}
