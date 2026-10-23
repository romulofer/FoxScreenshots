package com.foxdevelops.foxscreenshots.platform.portal

import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyBinding
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyKey
import com.foxdevelops.foxscreenshots.core.hotkey.HotkeyModifier
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt64
import org.freedesktop.dbus.types.Variant

/** `(sa{sv})`: one shortcut the app asks the desktop to bind: its id and its description/preferred trigger. */
@JvmSuppressWildcards
class ShortcutSpec(
    @field:Position(0) val id: String,
    @field:Position(1) val properties: Map<String, Variant<*>>,
) : Struct()

/**
 * `org.freedesktop.portal.GlobalShortcuts`: system-wide shortcuts on Wayland, where a client cannot watch
 * the keyboard. The *compositor* owns the grab: it may ask the user to confirm, may bind a different
 * key than the preferred one, and swallows the key for every other app while it is bound.
 */
@JvmSuppressWildcards
@DBusInterfaceName("org.freedesktop.portal.GlobalShortcuts")
interface GlobalShortcutsApi : DBusInterface {
    fun CreateSession(options: Map<String, Variant<*>>): DBusPath

    fun BindShortcuts(
        sessionHandle: DBusPath,
        shortcuts: List<ShortcutSpec>,
        parentWindow: String,
        options: Map<String, Variant<*>>,
    ): DBusPath

    /** A bound shortcut was pressed. */
    class Activated(
        path: String,
        val sessionHandle: DBusPath,
        val shortcutId: String,
        val timestamp: UInt64,
        val options: Map<String, Variant<*>>,
    ) : DBusSignal(path, sessionHandle, shortcutId, timestamp, options)
}

/** `org.freedesktop.portal.Session`: what a portal session offers once created. */
@DBusInterfaceName("org.freedesktop.portal.Session")
interface PortalSessionApi : DBusInterface {
    fun Close()
}

/**
 * The binding in the portal's shortcut syntax: `+`-joined modifiers (`CTRL`, `ALT`, `SHIFT`, `LOGO`) and an
 * XKB key name (`Print`, `F9`, lower-case letters). It is only the *preferred* trigger: the desktop decides.
 */
fun HotkeyBinding.toPortalTrigger(): String {
    val parts = buildList {
        if (HotkeyModifier.Control in modifiers) add("CTRL")
        if (HotkeyModifier.Alt in modifiers) add("ALT")
        if (HotkeyModifier.Shift in modifiers) add("SHIFT")
        if (HotkeyModifier.Meta in modifiers) add("LOGO")
        add(
            when (key) {
                HotkeyKey.PrintScreen -> "Print"
                HotkeyKey.Escape -> "Escape"
                else -> if (key.name.length == 1) key.name.lowercase() else key.name
            },
        )
    }
    return parts.joinToString("+")
}
