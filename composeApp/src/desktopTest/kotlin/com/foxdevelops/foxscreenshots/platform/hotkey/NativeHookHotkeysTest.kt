package com.foxdevelops.foxscreenshots.platform.hotkey

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyModifier
import com.github.kwhat.jnativehook.NativeInputEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import org.junit.Assume.assumeTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeHookHotkeysTest {
    @Test
    fun `modifier masks translate either side of a key`() {
        assertEquals(emptySet(), NativeHookHotkeys.modifiersOf(0))
        assertEquals(setOf(HotkeyModifier.Control), NativeHookHotkeys.modifiersOf(NativeInputEvent.CTRL_L_MASK))
        assertEquals(setOf(HotkeyModifier.Control), NativeHookHotkeys.modifiersOf(NativeInputEvent.CTRL_R_MASK))
        assertEquals(
            setOf(HotkeyModifier.Control, HotkeyModifier.Shift, HotkeyModifier.Alt, HotkeyModifier.Meta),
            NativeHookHotkeys.modifiersOf(
                NativeInputEvent.CTRL_MASK or NativeInputEvent.SHIFT_MASK or NativeInputEvent.ALT_MASK or NativeInputEvent.META_MASK,
            ),
        )
    }

    @Test
    fun `every hotkey key has its own virtual key code`() {
        val codes = HotkeyKey.entries.map { NativeHookHotkeys.virtualKey(it) }
        assertEquals(HotkeyKey.entries.size, codes.toSet().size)
        assertEquals(NativeKeyEvent.VC_PRINTSCREEN, NativeHookHotkeys.virtualKey(HotkeyKey.PrintScreen))
    }

    // ---- live hook, driven by xdotool on a throwaway X server (xvfb-run); never on a real session ----

    private val hotkeys = NativeHookHotkeys()
    private val fired = CopyOnWriteArrayList<HotkeyAction>()

    @AfterTest
    fun tearDown() = hotkeys.close()

    private fun requireIsolatedX() {
        assumeTrue("set FOX_ISOLATED_DISPLAY=1 under xvfb-run", System.getenv("FOX_ISOLATED_DISPLAY") == "1")
        assumeTrue(System.getenv("DISPLAY")?.isNotEmpty() == true)
    }

    private fun xdotool(vararg args: String) {
        val process = ProcessBuilder("xdotool", *args).redirectErrorStream(true).start()
        process.waitFor(5, TimeUnit.SECONDS)
    }

    private fun settle() = Thread.sleep(400)

    private fun install() {
        hotkeys.register(
            mapOf(
                HotkeyAction.Instant to HotkeyBinding.parse("F9"),
                HotkeyAction.Timer to HotkeyBinding.parse("Ctrl+Shift+S"),
            ),
        ) { fired += it }
        settle()
    }

    @Test
    fun `a bare key fires its action`() {
        requireIsolatedX()
        install()
        xdotool("key", "F9")
        settle()
        assertEquals(listOf(HotkeyAction.Instant), fired.toList())
    }

    @Test
    fun `a chord fires only with exactly its modifiers`() {
        requireIsolatedX()
        install()
        xdotool("key", "ctrl+shift+s")
        settle()
        assertEquals(listOf(HotkeyAction.Timer), fired.toList())
        fired.clear()

        xdotool("key", "ctrl+F9") // F9 is bound bare; an extra modifier must not trigger it
        settle()
        assertTrue(fired.isEmpty(), "unexpected: $fired")
    }

    @Test
    fun `scroll lock does not stop the hotkey`() {
        requireIsolatedX()
        install()
        xdotool("key", "Scroll_Lock")
        settle()
        xdotool("key", "F9")
        settle()
        xdotool("key", "Scroll_Lock")
        assertEquals(listOf(HotkeyAction.Instant), fired.toList())
    }

    @Test
    fun `a held key fires once, not on every auto-repeat`() {
        requireIsolatedX()
        install()
        xdotool("keydown", "F9")
        xdotool("keydown", "F9")
        xdotool("keydown", "F9")
        settle()
        xdotool("keyup", "F9")
        settle()
        assertEquals(1, fired.size)
        xdotool("key", "F9")
        settle()
        assertEquals(2, fired.size)
    }

    @Test
    fun `registering again swaps the bindings`() {
        requireIsolatedX()
        install()
        hotkeys.register(mapOf(HotkeyAction.Repeat to HotkeyBinding.parse("F8"))) { fired += it }
        xdotool("key", "F9")
        xdotool("key", "F8")
        settle()
        assertEquals(listOf(HotkeyAction.Repeat), fired.toList())
    }
}
