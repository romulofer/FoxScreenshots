package com.foxdevelops.foxscreenshots.core.desktop

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyAction
import com.foxdevelops.foxscreenshots.core.i18n.Strings
import com.foxdevelops.foxscreenshots.core.settings.Settings

/** Actions the tray context menu can raise. */
enum class TrayAction { Show, Instant, Timer, Repeat, Settings, Quit }

/** What the desktop (tray icon, global hotkeys) asks the app to do. */
interface DesktopEvents {
    /** Left click on the tray icon. */
    fun onOpenWindow()
    fun onTrayAction(action: TrayAction)
    fun onHotkey(action: HotkeyAction)
}

/**
 * The behaviours that make the app live in the tray: the tray icon and its
 * menu, and the global capture hotkeys. Behind an interface because both need a
 * real desktop; UI tests use [NoDesktopIntegration].
 */
interface DesktopIntegration {
    /**
     * Installs (or refreshes) the tray icon and the hotkeys. Safe to call again
     * after a language change or a hotkey rebind.
     */
    fun attach(strings: Strings, settings: Settings, events: DesktopEvents)

    /** Tears the tray icon and the hotkeys down. */
    fun close()
}

/** Does nothing; used by UI tests, which have no tray or keyboard hook. */
object NoDesktopIntegration : DesktopIntegration {
    override fun attach(strings: Strings, settings: Settings, events: DesktopEvents) = Unit
    override fun close() = Unit
}
