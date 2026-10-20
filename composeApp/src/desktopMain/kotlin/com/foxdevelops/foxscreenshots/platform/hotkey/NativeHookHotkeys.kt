package com.foxdevelops.foxscreenshots.platform.hotkey

import com.foxdevelops.foxscreenshots.core.hotkey.GlobalHotkeys
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyModifier
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.NativeInputEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/** Why the system-wide keyboard hook could not be installed. */
class HotkeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * System-wide hotkeys through JNativeHook: an X record hook on X11, a low-level
 * keyboard hook on Windows, an event tap on macOS (which asks for the
 * Accessibility permission).
 *
 * The hook *observes* keys, so the matching is ours: modifiers are compared
 * exactly and lock keys (Caps, Num, Scroll Lock) never get in the way — the
 * reason a global-grab based implementation silently stops working under Scroll
 * Lock. A held key fires once, not on every auto-repeat.
 */
class NativeHookHotkeys : GlobalHotkeys, NativeKeyListener {
    @Volatile
    private var bindings: Map<HotkeyAction, HotkeyBinding> = emptyMap()

    @Volatile
    private var onTriggered: (HotkeyAction) -> Unit = {}

    /** Keys currently held, so auto-repeat does not retrigger. */
    private val held = ConcurrentHashMap.newKeySet<Int>()

    private var listening = false

    @Synchronized
    override fun register(bindings: Map<HotkeyAction, HotkeyBinding>, onTriggered: (HotkeyAction) -> Unit) {
        this.bindings = bindings
        this.onTriggered = onTriggered
        if (listening) return
        installHook()
        GlobalScreen.addNativeKeyListener(this)
        listening = true
    }

    /**
     * Stops listening and forgets the bindings. The process-wide hook itself stays up until the JVM exits:
     * the library shuts its dispatcher down for good when the hook is removed, so a hook cannot be
     * reinstalled in the same process.
     */
    @Synchronized
    override fun close() {
        if (!listening) return
        listening = false
        GlobalScreen.removeNativeKeyListener(this)
        bindings = emptyMap()
        held.clear()
    }

    override fun nativeKeyPressed(event: NativeKeyEvent) {
        if (!held.add(event.keyCode)) return
        val pressed = modifiersOf(event.modifiers)
        for ((action, binding) in bindings) {
            if (virtualKey(binding.key) == event.keyCode && binding.modifiers == pressed) {
                onTriggered(action)
                return
            }
        }
    }

    override fun nativeKeyReleased(event: NativeKeyEvent) {
        held.remove(event.keyCode)
    }

    companion object {
        private var hookInstalled = false

        @Synchronized
        private fun installHook() {
            if (hookInstalled) return
            try {
                // JNativeHook logs every event at INFO through java.util.logging; that would put keystrokes in a log.
                Logger.getLogger(GlobalScreen::class.java.packageName).apply {
                    level = Level.OFF
                    useParentHandlers = false
                }
                GlobalScreen.registerNativeHook()
                Runtime.getRuntime().addShutdownHook(
                    Thread { runCatching { GlobalScreen.unregisterNativeHook() } },
                )
                hookInstalled = true
            } catch (e: NativeHookException) {
                throw HotkeyUnavailableException("The system keyboard hook could not be installed: ${e.message}", e)
            } catch (e: UnsatisfiedLinkError) {
                throw HotkeyUnavailableException("The keyboard hook library could not be loaded: ${e.message}", e)
            }
        }

        /** The modifiers set in a JNativeHook mask; either side of a modifier key counts. */
        internal fun modifiersOf(mask: Int): Set<HotkeyModifier> = buildSet {
            if (mask and NativeInputEvent.CTRL_MASK != 0) add(HotkeyModifier.Control)
            if (mask and NativeInputEvent.ALT_MASK != 0) add(HotkeyModifier.Alt)
            if (mask and NativeInputEvent.SHIFT_MASK != 0) add(HotkeyModifier.Shift)
            if (mask and NativeInputEvent.META_MASK != 0) add(HotkeyModifier.Meta)
        }

        internal fun virtualKey(key: HotkeyKey): Int = when (key) {
            HotkeyKey.PrintScreen -> NativeKeyEvent.VC_PRINTSCREEN
            HotkeyKey.F1 -> NativeKeyEvent.VC_F1
            HotkeyKey.F2 -> NativeKeyEvent.VC_F2
            HotkeyKey.F3 -> NativeKeyEvent.VC_F3
            HotkeyKey.F4 -> NativeKeyEvent.VC_F4
            HotkeyKey.F5 -> NativeKeyEvent.VC_F5
            HotkeyKey.F6 -> NativeKeyEvent.VC_F6
            HotkeyKey.F7 -> NativeKeyEvent.VC_F7
            HotkeyKey.F8 -> NativeKeyEvent.VC_F8
            HotkeyKey.F9 -> NativeKeyEvent.VC_F9
            HotkeyKey.F10 -> NativeKeyEvent.VC_F10
            HotkeyKey.F11 -> NativeKeyEvent.VC_F11
            HotkeyKey.F12 -> NativeKeyEvent.VC_F12
            HotkeyKey.A -> NativeKeyEvent.VC_A
            HotkeyKey.B -> NativeKeyEvent.VC_B
            HotkeyKey.C -> NativeKeyEvent.VC_C
            HotkeyKey.D -> NativeKeyEvent.VC_D
            HotkeyKey.E -> NativeKeyEvent.VC_E
            HotkeyKey.F -> NativeKeyEvent.VC_F
            HotkeyKey.G -> NativeKeyEvent.VC_G
            HotkeyKey.H -> NativeKeyEvent.VC_H
            HotkeyKey.I -> NativeKeyEvent.VC_I
            HotkeyKey.J -> NativeKeyEvent.VC_J
            HotkeyKey.K -> NativeKeyEvent.VC_K
            HotkeyKey.L -> NativeKeyEvent.VC_L
            HotkeyKey.M -> NativeKeyEvent.VC_M
            HotkeyKey.N -> NativeKeyEvent.VC_N
            HotkeyKey.O -> NativeKeyEvent.VC_O
            HotkeyKey.P -> NativeKeyEvent.VC_P
            HotkeyKey.Q -> NativeKeyEvent.VC_Q
            HotkeyKey.R -> NativeKeyEvent.VC_R
            HotkeyKey.S -> NativeKeyEvent.VC_S
            HotkeyKey.T -> NativeKeyEvent.VC_T
            HotkeyKey.U -> NativeKeyEvent.VC_U
            HotkeyKey.V -> NativeKeyEvent.VC_V
            HotkeyKey.W -> NativeKeyEvent.VC_W
            HotkeyKey.X -> NativeKeyEvent.VC_X
            HotkeyKey.Y -> NativeKeyEvent.VC_Y
            HotkeyKey.Z -> NativeKeyEvent.VC_Z
        }
    }
}
